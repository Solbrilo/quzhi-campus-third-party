package cn.qzxy.hotwater

import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RechargeControllerTest {
    private val config = HotWaterConfig(1, 2, 3, "test-session", "test-phone")
    private class Remote : RechargeRemoteDataSource {
        var orders = 0
        var waitForOrder: CompletableDeferred<Unit>? = null
        var waitForOptions: CompletableDeferred<Unit>? = null
        var balance = "12.5"
        override suspend fun rechargeBalance(config: HotWaterConfig) =
            JSONObject().put("success", true).put("data", JSONObject().put("accountMoneyStr", balance))
        override suspend fun rechargeOptions(config: HotWaterConfig): JSONObject {
            waitForOptions?.let { withContext(NonCancellable) { it.await() } }
            return JSONObject("""{"success":true,"data":{"payWay":["2"]}}""")
        }
        override suspend fun rechargePackages(config: HotWaterConfig) =
            JSONObject("""{"success":true,"data":[{"typeId":1,"amount":10}]}""")
        override suspend fun createRecharge(config: HotWaterConfig, amount: String): JSONObject {
            orders++
            waitForOrder?.let { withContext(NonCancellable) { it.await() } }
            return JSONObject("""{"success":true,"data":"app_id=test&sign=test"}""")
        }
    }

    @Test fun duplicateClicksAndActivityRecreationCannotLaunchTheSameOrderTwice() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val remote = Remote()
        var refreshes = 0
        val controller = RechargeController(scope, remote, { config }, { 1 }, { false }, {}, { refreshes++ })
        try {
            controller.open()
            refreshes = 0
            controller.submit()
            controller.submit()
            controller.load()
            assertEquals(1, remote.orders)
            val id = controller.state.value.paymentRequestId!!
            assertNotNull(controller.claimPayment(id))
            assertNull(controller.claimPayment(id))
            assertTrue(controller.state.value.paying)
            remote.balance = "14.5"
            controller.close()
            assertTrue(controller.state.value.visible)
            controller.completePayment(id, AlipayResult.UNKNOWN)
            controller.completePayment(id, AlipayResult.SUCCESS)
            assertEquals(1, refreshes)
            assertEquals(AlipayResult.UNKNOWN.notice, controller.state.value.notice)
            assertFalse(controller.state.value.busy)
            assertEquals(14.5, controller.state.value.balanceYuan!!, 0.0)
        } finally { scope.cancel() }
    }

    @Test fun lateOrderAfterAccountChangeCannotOpenAlipay() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gate = CompletableDeferred<Unit>()
        val remote = Remote().apply { waitForOrder = gate }
        var current = config
        val controller = RechargeController(scope, remote, { current }, { 1 }, { false }, {}, {})
        try {
            controller.open()
            controller.submit()
            current = config.copy(userId = 9, accountId = 8)
            controller.synchronizeAccount()
            controller.open()
            gate.complete(Unit)
            yield()
            assertNull(controller.state.value.paymentRequestId)
            assertFalse(controller.state.value.busy)
            assertTrue(controller.state.value.canSubmit)
        } finally { gate.complete(Unit); scope.cancel() }
    }

    @Test fun clearDataAndOldSdkResultCannotRefreshNewAccount() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var epoch = 1L
        var refreshes = 0
        val controller = RechargeController(scope, Remote(), { config }, { epoch }, { false }, {}, { refreshes++ })
        try {
            controller.open()
            controller.submit()
            val id = controller.state.value.paymentRequestId!!
            assertNotNull(controller.claimPayment(id))
            epoch++
            controller.clear()
            controller.open()
            refreshes = 0
            controller.completePayment(id, AlipayResult.SUCCESS)
            assertEquals(0, refreshes)
            assertNull(controller.state.value.notice)
        } finally { scope.cancel() }
    }

    @Test fun dismissedSheetCannotReappearFromLateConfigurationResponse() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gate = CompletableDeferred<Unit>()
        val controller = RechargeController(scope, Remote().apply { waitForOptions = gate },
            { config }, { 1 }, { false }, {}, {})
        try {
            controller.open()
            controller.close()
            gate.complete(Unit)
            yield()
            assertFalse(controller.state.value.visible)
            assertTrue(controller.state.value.packages.isEmpty())
        } finally { gate.complete(Unit); scope.cancel() }
    }
}
