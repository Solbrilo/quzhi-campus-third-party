package cn.qzxy.hotwater

import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class ServerBillsTest {
    private val month = YearMonth.of(2026, 8)
    private val config = HotWaterConfig(1, 2, 3, "test-session", "13800138000")
    private fun response(id: String = "test-order", amount: String = "0.08") = JSONObject("""{
        "success":true,"errorCode":0,"data":[{"consumeBillDTO":{
        "orderId":"$id","orderNo":"test-order-number-$id","consumeDate":"2026-08-12 10:08:42",
        "consumeMoney":"$amount","description":"热水器:学生公寓"}}]}""")

    @Test fun yuanAmountsAreNotDividedByOneThousand() {
        assertEquals(80L, ServerBillParser.parse(response(), month).single().amountMilliYuan)
        assertEquals(0L, ServerBillParser.parse(response(amount = "0"), month).single().amountMilliYuan)
        assertNull(ServerBillParser.parse(response(amount = "NaN"), month).single().amountMilliYuan)
        assertNull(ServerBillParser.parse(response(amount = "-1"), month).single().amountMilliYuan)
        assertNull(ServerBillParser.parse(response(amount = ""), month).single().amountMilliYuan)
    }

    @Test fun unknownAmountsAndDatesRemainVisibleInsteadOfInventingZeroOrDroppingRows() {
        val payload = response()
        val dto = payload.getJSONArray("data").getJSONObject(0).getJSONObject("consumeBillDTO")
        dto.put("consumeMoney", JSONObject.NULL).put("consumeDate", "not-a-date")
        val bill = ServerBillParser.parse(payload, month).single()
        assertNull(bill.amountMilliYuan)
        assertNull(bill.timestamp)
        assertEquals(month, bill.period)
    }

    @Test fun serviceFailuresAndMalformedDataAreNotEmptySuccessfulBills() {
        assertTrue(runCatching { ServerBillParser.parse(JSONObject("""{"success":false,"data":[]}"""), month) }.isFailure)
        assertTrue(runCatching { ServerBillParser.parse(JSONObject("""{"success":true,"data":"invalid"}"""), month) }.isFailure)
        assertTrue(ServerBillParser.parse(JSONObject("""{"success":true,"data":[]}"""), month).isEmpty())
    }

    @Test fun annualQueryStopsAtCurrentMonth() {
        assertEquals(9, BillQuery(2026).months(YearMonth.of(2026, 9)).size)
        assertEquals(12, BillQuery(2025).months(YearMonth.of(2026, 9)).size)
        assertTrue(BillQuery(2026, 12).months(YearMonth.of(2026, 9)).isEmpty())
    }

    @Test fun lateCancelledResponseCannotOverwriteNewPeriod() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val oldResult = CompletableDeferred<Unit>()
        val oldStarted = CompletableDeferred<Unit>()
        val remote = object : ServerBillRemoteDataSource {
            override suspend fun billList(config: HotWaterConfig, month: String): JSONObject {
                if (month == "2026-07") {
                    oldStarted.complete(Unit)
                    withContext(NonCancellable) { oldResult.await() }
                    return response("old")
                }
                return response("new")
            }
        }
        val controller = ServerBillsController(scope, remote) { fail("unexpected expiry") }
        try {
            controller.load(config, BillQuery(2026, 7))
            withTimeout(1000) { oldStarted.await() }
            controller.load(config, BillQuery(2026, 8))
            withTimeout(1000) { while (!controller.state.value.loaded) yield() }
            oldResult.complete(Unit)
            yield()
            assertEquals("new", controller.state.value.bills.single().orderId)
            assertEquals(BillQuery(2026, 8), controller.state.value.query)
        } finally { oldResult.complete(Unit); scope.cancel() }
    }

    @Test fun accountClearInvalidatesInFlightBillResponse() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val release = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val remote = object : ServerBillRemoteDataSource {
            override suspend fun billList(config: HotWaterConfig, month: String): JSONObject {
                started.complete(Unit)
                withContext(NonCancellable) { release.await() }
                return response()
            }
        }
        val controller = ServerBillsController(scope, remote) {}
        try {
            controller.load(config, BillQuery(2026, 8))
            withTimeout(1000) { started.await() }
            controller.clear()
            release.complete(Unit)
            yield()
            assertTrue(controller.state.value.bills.isEmpty())
            assertFalse(controller.state.value.loaded)
        } finally { release.complete(Unit); scope.cancel() }
    }

    @Test fun partialYearFailureDoesNotClaimCompleteTotal() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val remote = object : ServerBillRemoteDataSource {
            override suspend fun billList(config: HotWaterConfig, month: String): JSONObject {
                if (month.endsWith("-02")) throw java.io.IOException("offline")
                return response(month)
            }
        }
        val controller = ServerBillsController(scope, remote) {}
        try {
            controller.load(config, BillQuery(2025))
            withTimeout(1000) { while (controller.state.value.loading) yield() }
            assertFalse(controller.state.value.loaded)
            assertEquals(listOf(YearMonth.of(2025, 2)), controller.state.value.failedMonths)
            assertEquals(11, controller.state.value.bills.size)
        } finally { scope.cancel() }
    }
}
