package cn.qzxy.hotwater

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.ArrayDeque

class HotWaterRepositoryTest {
    @Test
    fun open_preservesBalanceBeforeFiveYuanReservation() = runBlocking {
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(20.0), balancePayload(15.0)),
            statusResponses = listOf(successPayload()),
            openResponse = JSONObject().put("orderNo", "order-1"),
            openResultResponse = successPayload(),
        )
        val store = FakeSessionStore(
            usageSession = UsageSession(
                startedAtMillis = System.currentTimeMillis() - 600_000L,
                startBalanceYuan = 99.0,
            ),
        )

        val result = HotWaterRepository(api, store).open(
            config = completeConfig,
            pollAttempts = 1,
            pollDelayMs = 0L,
        )

        assertEquals(2, api.balanceCalls)
        assertEquals(15.0, result.snapshot?.balanceYuan ?: -1.0, 0.001)
        assertEquals(20.0, store.usageSession?.startBalanceYuan ?: -1.0, 0.001)
        assertTrue(store.usageSession?.startBalanceIsBeforePreauthorization == true)
    }

    @Test
    fun open_unknownPreOpenBalanceStaysUnknownUntilCloseRecoversActiveBalance() = runBlocking {
        val activeStatus = successPayload().apply {
            getJSONObject("data").put("orderNo", "order-unknown-open-baseline")
        }
        val api = FakeHotWaterApi(
            balanceResponses = listOf(
                // First post-open snapshot may still expose the pre-reservation value.
                balancePayload(20.0),
                balancePayload(15.0),
                balancePayload(15.0),
                balancePayload(18.75),
            ),
            statusResponses = listOf(successPayload(), activeStatus),
            openResponse = JSONObject().put("orderNo", "order-unknown-open-baseline"),
            openResultResponse = successPayload(),
            closeResponse = successPayload(),
            closeResultResponse = successPayload(),
            failFirstBalanceRequest = true,
        )
        val store = FakeSessionStore()
        val repository = HotWaterRepository(api, store)

        val openResult = repository.open(
            config = completeConfig,
            pollAttempts = 1,
            pollDelayMs = 0L,
        )
        val startedAtMillis = store.usageSession?.startedAtMillis

        assertEquals(20.0, openResult.snapshot?.balanceYuan ?: -1.0, 0.001)
        assertNull(store.usageSession?.startBalanceYuan)
        assertTrue(startedAtMillis != null)

        val closeResult = repository.close(
            config = completeConfig,
            pollAttempts = 1,
            pollDelayMs = 0L,
            settlementRefreshAttempts = 2,
            settlementRefreshDelayMs = 0L,
        )

        assertEquals(1.25, closeResult.consumptionYuan ?: -1.0, 0.001)
        assertEquals(startedAtMillis, closeResult.usageStartedAtMillis)
    }

    @Test
    fun close_refreshesUntilSettledAndRepairsLegacySessionBaseline() = runBlocking {
        val api = FakeHotWaterApi(
            balanceResponses = listOf(
                balancePayload(15.0),
                balancePayload(15.0),
                balancePayload(18.75),
            ),
            statusResponses = listOf(
                successPayload().apply {
                    getJSONObject("data").put("orderNo", "order-1")
                },
            ),
            closeResponse = successPayload(),
            closeResultResponse = successPayload(),
        )
        val store = FakeSessionStore(
            orderNo = "order-1",
            usageSession = UsageSession(
                startedAtMillis = System.currentTimeMillis() - 60_000L,
                // Older versions stored the already-reserved balance here.
                startBalanceYuan = 15.0,
            ),
        )

        val result = HotWaterRepository(api, store).close(
            config = completeConfig,
            pollAttempts = 1,
            pollDelayMs = 0L,
            settlementRefreshAttempts = 2,
            settlementRefreshDelayMs = 0L,
        )

        assertEquals(3, api.balanceCalls)
        assertEquals(18.75, result.snapshot?.balanceYuan ?: -1.0, 0.001)
        assertEquals(1.25, result.consumptionYuan ?: -1.0, 0.001)
        assertNull(store.usageSession)
    }

    @Test
    fun close_fallsBackToPreOpenBalanceDeltaWhenSettlementHasNoAmount() = runBlocking {
        val api = FakeHotWaterApi(
            balanceResponses = listOf(
                balancePayload(15.0),
                balancePayload(15.0),
                balancePayload(18.75),
            ),
            statusResponses = listOf(
                successPayload().apply {
                    getJSONObject("data").put("orderNo", "order-1")
                },
            ),
            closeResponse = successPayload(),
            closeResultResponse = successPayload(),
        )
        val store = FakeSessionStore(
            orderNo = "order-1",
            usageSession = UsageSession(
                startedAtMillis = System.currentTimeMillis() - 60_000L,
                startBalanceYuan = 20.0,
                startBalanceIsBeforePreauthorization = true,
            ),
        )

        val result = HotWaterRepository(api, store).close(
            config = completeConfig,
            pollAttempts = 1,
            pollDelayMs = 0L,
            settlementRefreshAttempts = 2,
            settlementRefreshDelayMs = 0L,
        )

        assertEquals(1.25, result.consumptionYuan ?: -1.0, 0.001)
        assertTrue(result.outcome == OperationOutcome.SUCCESS)
    }

    @Test
    fun close_missingLegacySessionBaselineFallsBackToQueriedActiveBalance() = runBlocking {
        val startedAtMillis = System.currentTimeMillis() - 60_000L
        val api = FakeHotWaterApi(
            balanceResponses = listOf(
                balancePayload(15.0),
                balancePayload(15.0),
                balancePayload(18.75),
            ),
            statusResponses = listOf(
                successPayload().apply {
                    getJSONObject("data").put("orderNo", "order-missing-baseline")
                },
            ),
            closeResponse = successPayload(),
            closeResultResponse = successPayload(),
        )
        val store = FakeSessionStore(
            orderNo = "order-missing-baseline",
            usageSession = UsageSession(
                startedAtMillis = startedAtMillis,
                startBalanceYuan = null,
            ),
        )

        val result = HotWaterRepository(api, store).close(
            config = completeConfig,
            pollAttempts = 1,
            pollDelayMs = 0L,
            settlementRefreshAttempts = 2,
            settlementRefreshDelayMs = 0L,
        )

        assertEquals(1.25, result.consumptionYuan ?: -1.0, 0.001)
        assertEquals(startedAtMillis, result.usageStartedAtMillis)
        assertTrue((result.usageDurationMillis ?: 0L) >= 60_000L)
    }

    @Test
    fun close_keepsConsumptionPendingWhenReservationNeverSettles() = runBlocking {
        val api = FakeHotWaterApi(
            // The active-balance request fails; all six post-close reads repeat the
            // reserved balance and therefore cannot be treated as settled.
            balanceResponses = listOf(balancePayload(15.0)),
            statusResponses = listOf(
                successPayload().apply {
                    getJSONObject("data").put("orderNo", "order-1")
                },
            ),
            closeResponse = successPayload(),
            closeResultResponse = successPayload(),
            failFirstBalanceRequest = true,
        )
        val store = FakeSessionStore(
            orderNo = "order-1",
            usageSession = UsageSession(
                startedAtMillis = System.currentTimeMillis() - 60_000L,
                startBalanceYuan = 15.0,
            ),
        )

        val result = HotWaterRepository(api, store).close(
            config = completeConfig,
            pollAttempts = 1,
            pollDelayMs = 0L,
            settlementRefreshAttempts = 6,
            settlementRefreshDelayMs = 0L,
        )

        assertEquals(7, api.balanceCalls)
        assertEquals("--", result.snapshot?.balanceLabel)
        assertNull(result.consumptionYuan)
        assertEquals(OperationOutcome.SUCCESS, result.outcome)
        assertEquals(1, store.pendingSettlements.size)
        assertTrue(store.usageSession == null)
        val history = OperationHistory(
            type = OperationHistory.OperationType.CLOSE,
            success = true,
            message = result.summary,
            consumptionYuan = result.consumptionYuan,
        )
        assertEquals("消费金额 待同步", history.getConsumptionLabel())
    }

    @Test
    fun close_withoutBalanceBaselineUsesFinalConsumeResultAmount() = runBlocking {
        val consumeResult = successPayload().apply {
            getJSONObject("data").put("consumeMoney", 1_250)
        }
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(15.0)),
            statusResponses = listOf(
                successPayload().apply {
                    getJSONObject("data").put("orderNo", "order-1")
                },
            ),
            closeResponse = successPayload(),
            closeResultResponse = successPayload(),
            consumeResultResponses = listOf(consumeResult),
            failFirstBalanceRequest = true,
        )
        val store = FakeSessionStore(orderNo = "order-1")

        val result = HotWaterRepository(api, store).close(
            config = completeConfig,
            pollAttempts = 1,
            pollDelayMs = 0L,
            settlementRefreshAttempts = 6,
            settlementRefreshDelayMs = 0L,
        )

        assertEquals(7, api.balanceCalls)
        assertEquals(15.0, result.snapshot?.balanceYuan ?: -1.0, 0.001)
        assertEquals(1.25, result.consumptionYuan ?: -1.0, 0.001)
    }

    @Test
    fun check_missingBalanceStaysUnknownAndDoesNotCreateUsageBaseline() = runBlocking {
        val api = FakeHotWaterApi(
            balanceResponses = listOf(JSONObject().put("data", JSONObject())),
            statusResponses = listOf(successPayload()),
        )
        val store = FakeSessionStore()

        val result = HotWaterRepository(api, store).check(completeConfig)

        assertNull(result.snapshot?.balanceYuan)
        assertEquals("--", result.snapshot?.balanceLabel)
        assertNull(store.usageSession)
    }

    @Test
    fun open_existingOwnedOrderDoesNotSendDuplicateOpenAndRepairsBaseline() = runBlocking {
        val activeStatus = successPayload().apply {
            getJSONObject("data").put("orderNo", "order-existing")
        }
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(15.0)),
            statusResponses = listOf(activeStatus),
        )
        val store = FakeSessionStore()

        val result = HotWaterRepository(api, store).open(
            config = completeConfig,
            pollAttempts = 1,
            pollDelayMs = 0L,
        )

        assertEquals(0, api.openCalls)
        assertEquals("order-existing", store.loadOrderNo())
        assertEquals(15.0, store.usageSession?.startBalanceYuan ?: -1.0, 0.001)
        assertTrue(store.usageSession?.startBalanceIsBeforePreauthorization == false)
        assertEquals(OperationOutcome.SUCCESS, result.outcome)
    }

    @Test
    fun close_exactFiveYuanUsesFinalConsumeResultEvenWhenBalanceIsUnchanged() = runBlocking {
        val finalConsumeResult = successPayload().apply {
            getJSONObject("data").put("consumeMoney", 5_000)
        }
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(15.0), balancePayload(15.0)),
            statusResponses = listOf(
                successPayload().apply {
                    getJSONObject("data").put("orderNo", "order-5")
                },
            ),
            closeResponse = successPayload(),
            closeResultResponse = successPayload(),
            consumeResultResponses = listOf(finalConsumeResult),
        )
        val store = FakeSessionStore(
            orderNo = "order-5",
            usageSession = UsageSession(
                startedAtMillis = System.currentTimeMillis() - 30_000L,
                startBalanceYuan = 20.0,
                startBalanceIsBeforePreauthorization = true,
            ),
        )

        val result = HotWaterRepository(api, store).close(
            config = completeConfig,
            pollAttempts = 1,
            pollDelayMs = 0L,
            settlementRefreshAttempts = 2,
            settlementRefreshDelayMs = 0L,
        )

        assertEquals(5.0, result.consumptionYuan ?: -1.0, 0.001)
        assertEquals(15.0, result.snapshot?.balanceYuan ?: -1.0, 0.001)
        assertTrue(store.pendingSettlements.isEmpty())
    }

    @Test
    fun close_finalAmountStillWaitsForMatchingRealBalance() = runBlocking {
        val finalConsumeResult = successPayload().apply {
            getJSONObject("data").put("consumeMoney", 1_250)
        }
        val api = FakeHotWaterApi(
            balanceResponses = listOf(
                balancePayload(15.0),
                balancePayload(15.0),
                balancePayload(18.75),
            ),
            statusResponses = listOf(
                successPayload().apply {
                    getJSONObject("data").put("orderNo", "order-real-balance")
                },
            ),
            closeResponse = successPayload(),
            closeResultResponse = successPayload(),
            consumeResultResponses = listOf(finalConsumeResult),
        )
        val store = FakeSessionStore(
            orderNo = "order-real-balance",
            usageSession = UsageSession(
                startedAtMillis = System.currentTimeMillis() - 30_000L,
                startBalanceYuan = 20.0,
                startBalanceIsBeforePreauthorization = true,
            ),
        )

        val result = HotWaterRepository(api, store).close(
            config = completeConfig,
            pollAttempts = 1,
            pollDelayMs = 0L,
            settlementRefreshAttempts = 2,
            settlementRefreshDelayMs = 0L,
        )

        assertEquals(3, api.balanceCalls)
        assertEquals(18.75, result.snapshot?.balanceYuan ?: -1.0, 0.001)
        assertEquals(1.25, result.consumptionYuan ?: -1.0, 0.001)
    }

    @Test
    fun close_finalOrderAmountWinsWhenWalletDeltaConflicts() = runBlocking {
        val finalConsumeResult = successPayload().apply {
            getJSONObject("data").put("consumeMoney", 1_250)
        }
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(15.0), balancePayload(18.0)),
            statusResponses = listOf(
                successPayload().apply {
                    getJSONObject("data").put("orderNo", "order-final-wins")
                },
            ),
            closeResponse = successPayload(),
            closeResultResponse = successPayload(),
            consumeResultResponses = listOf(finalConsumeResult),
        )
        val store = FakeSessionStore(
            orderNo = "order-final-wins",
            usageSession = UsageSession(
                startedAtMillis = System.currentTimeMillis() - 30_000L,
                startBalanceYuan = 20.0,
                startBalanceIsBeforePreauthorization = true,
            ),
        )

        val result = HotWaterRepository(api, store).close(
            config = completeConfig,
            pollAttempts = 1,
            pollDelayMs = 0L,
            settlementRefreshAttempts = 2,
            settlementRefreshDelayMs = 0L,
        )

        assertEquals(1.25, result.consumptionYuan ?: -1.0, 0.001)
        assertEquals("--", result.snapshot?.balanceLabel)
        assertTrue(store.pendingSettlements.isEmpty())
    }

    @Test
    fun close_zeroConsumeResultNeedsRestoredBalanceBeforeConfirmation() = runBlocking {
        val zeroConsumeResult = successPayload().apply {
            getJSONObject("data").put("consumeMoney", 0)
        }
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(15.0), balancePayload(20.0)),
            statusResponses = listOf(
                successPayload().apply {
                    getJSONObject("data").put("orderNo", "order-zero")
                },
            ),
            closeResponse = successPayload(),
            closeResultResponse = successPayload(),
            consumeResultResponses = listOf(zeroConsumeResult),
        )
        val store = FakeSessionStore(
            orderNo = "order-zero",
            usageSession = UsageSession(
                startedAtMillis = System.currentTimeMillis() - 1_000L,
                startBalanceYuan = 20.0,
                startBalanceIsBeforePreauthorization = true,
            ),
        )

        val result = HotWaterRepository(api, store).close(
            config = completeConfig,
            pollAttempts = 1,
            pollDelayMs = 0L,
            settlementRefreshAttempts = 2,
            settlementRefreshDelayMs = 0L,
        )

        assertEquals(0.0, result.consumptionYuan ?: -1.0, 0.0)
        assertEquals(20.0, result.snapshot?.balanceYuan ?: -1.0, 0.001)
        assertTrue(store.pendingSettlements.isEmpty())
    }

    @Test
    fun close_initialZeroConsumeResultRemainsPendingAtReservedBalance() = runBlocking {
        val zeroConsumeResult = successPayload().apply {
            getJSONObject("data").put("consumeMoney", 0)
        }
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(15.0)),
            statusResponses = listOf(
                successPayload().apply {
                    getJSONObject("data").put("orderNo", "order-zero-pending")
                },
            ),
            closeResponse = successPayload(),
            closeResultResponse = successPayload(),
            consumeResultResponses = listOf(zeroConsumeResult),
        )
        val store = FakeSessionStore(
            orderNo = "order-zero-pending",
            usageSession = UsageSession(
                startedAtMillis = System.currentTimeMillis() - 1_000L,
                startBalanceYuan = 20.0,
                startBalanceIsBeforePreauthorization = true,
            ),
        )

        val result = HotWaterRepository(api, store).close(
            config = completeConfig,
            pollAttempts = 1,
            pollDelayMs = 0L,
            settlementRefreshAttempts = 2,
            settlementRefreshDelayMs = 0L,
        )

        assertNull(result.consumptionYuan)
        assertEquals("--", result.snapshot?.balanceLabel)
        assertEquals(1, store.pendingSettlements.size)
        assertTrue(store.pendingSettlements.single().zeroConsumptionReported)
    }

    @Test
    fun close_rejectsAmountCarriedByUnconfirmedConsumeResult() = runBlocking {
        val unconfirmedConsumeResult = JSONObject()
            .put("success", false)
            .put("errorCode", 0)
            .put("data", JSONObject().put("consumeMoney", 1_250))
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(15.0)),
            statusResponses = listOf(
                successPayload().apply {
                    getJSONObject("data").put("orderNo", "order-unconfirmed-amount")
                },
            ),
            closeResponse = successPayload(),
            closeResultResponse = successPayload(),
            consumeResultResponses = listOf(unconfirmedConsumeResult),
        )
        val store = FakeSessionStore(
            orderNo = "order-unconfirmed-amount",
            usageSession = UsageSession(
                startedAtMillis = System.currentTimeMillis() - 1_000L,
                startBalanceYuan = 20.0,
                startBalanceIsBeforePreauthorization = true,
            ),
        )

        val result = HotWaterRepository(api, store).close(
            config = completeConfig,
            pollAttempts = 1,
            pollDelayMs = 0L,
            settlementRefreshAttempts = 2,
            settlementRefreshDelayMs = 0L,
        )

        assertNull(result.consumptionYuan)
        assertEquals(1, store.pendingSettlements.size)
        assertNull(store.pendingSettlements.single().finalConsumptionYuan)
    }

    @Test
    fun close_cancellationAfterAcceptancePropagatesAndKeepsPendingSettlement() = runBlocking {
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(15.0)),
            statusResponses = listOf(
                successPayload().apply {
                    getJSONObject("data").put("orderNo", "order-cancel")
                },
            ),
            closeResponse = successPayload(),
            closeResultResponse = successPayload(),
            balanceFailureAtCall = 2,
            balanceFailure = CancellationException("cancel settlement"),
        )
        val store = FakeSessionStore(
            orderNo = "order-cancel",
            usageSession = UsageSession(
                startedAtMillis = System.currentTimeMillis() - 10_000L,
                startBalanceYuan = 20.0,
                startBalanceIsBeforePreauthorization = true,
            ),
        )

        var cancelled = false
        try {
            HotWaterRepository(api, store).close(
                config = completeConfig,
                pollAttempts = 1,
                pollDelayMs = 0L,
                settlementRefreshAttempts = 2,
                settlementRefreshDelayMs = 0L,
            )
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertEquals(1, store.pendingSettlements.size)
        assertEquals("order-cancel", store.pendingSettlements.single().orderNo)
        assertNull(store.usageSession)
    }

    @Test
    fun check_refillsPendingHistoryFromFinalConsumeResult() = runBlocking {
        val pending = PendingSettlement(
            historyId = 42L,
            orderNo = "order-pending",
            closedAtMillis = System.currentTimeMillis() - 60_000L,
            usageStartedAtMillis = System.currentTimeMillis() - 120_000L,
            usageDurationMillis = 60_000L,
            startBalanceYuan = 20.0,
            activeBalanceYuan = 15.0,
            provisionalConsumptionYuan = null,
            finalConsumptionYuan = null,
            zeroConsumptionReported = false,
        )
        val finalConsumeResult = successPayload().apply {
            getJSONObject("data").put("consumeMoney", 1_250)
        }
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(18.75)),
            statusResponses = listOf(successPayload()),
            consumeResultResponses = listOf(finalConsumeResult),
        )
        val store = FakeSessionStore(pendingSettlements = listOf(pending))

        HotWaterRepository(api, store).check(completeConfig)

        assertTrue(store.pendingSettlements.isEmpty())
        assertEquals(1.25, store.updatedHistorySettlements[42L] ?: -1.0, 0.001)
    }

    @Test
    fun check_balanceFallbackOnlyRepairsNewestFreshPendingSettlement() = runBlocking {
        val now = System.currentTimeMillis()
        val olderPending = pendingSettlement(
            historyId = 41L,
            orderNo = "order-older",
            closedAtMillis = now - 30_000L,
            startBalanceYuan = 40.0,
            activeBalanceYuan = 35.0,
        )
        val newestPending = pendingSettlement(
            historyId = 42L,
            orderNo = "order-newest",
            closedAtMillis = now - 10_000L,
            startBalanceYuan = 30.0,
            activeBalanceYuan = 25.0,
        )
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(28.75)),
            statusResponses = listOf(successPayload()),
            consumeResultResponses = listOf(JSONObject(), JSONObject()),
        )
        val store = FakeSessionStore(
            pendingSettlements = listOf(olderPending, newestPending),
        )

        HotWaterRepository(api, store).check(completeConfig)

        assertEquals(listOf("order-older"), store.pendingSettlements.map { it.orderNo })
        assertNull(store.updatedHistorySettlements[41L])
        assertEquals(1.25, store.updatedHistorySettlements[42L] ?: -1.0, 0.001)
        assertTrue(!store.pendingSettlements.single().balanceFallbackAllowed)

        HotWaterRepository(api, store).check(completeConfig)

        assertEquals(listOf("order-older"), store.pendingSettlements.map { it.orderNo })
        assertNull(store.updatedHistorySettlements[41L])
    }

    @Test
    fun check_expiredPendingSettlementDoesNotUseCurrentWalletBalance() = runBlocking {
        val expiredPending = pendingSettlement(
            historyId = 43L,
            orderNo = "order-expired",
            closedAtMillis = System.currentTimeMillis() - 11 * 60 * 1_000L,
            startBalanceYuan = 20.0,
            activeBalanceYuan = 15.0,
        )
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(18.75)),
            statusResponses = listOf(successPayload()),
        )
        val store = FakeSessionStore(pendingSettlements = listOf(expiredPending))

        HotWaterRepository(api, store).check(completeConfig)

        assertEquals(listOf("order-expired"), store.pendingSettlements.map { it.orderNo })
        assertTrue(store.updatedHistorySettlements.isEmpty())
        // Only the normal dashboard refresh reads the wallet; reconciliation skips it.
        assertEquals(1, api.balanceCalls)
    }

    @Test
    fun legacyZeroCloseConsumptionMigratesToPending() {
        val migrated = OperationHistory.migrateConsumptionYuan(
            sourceSchemaVersion = 1,
            type = OperationHistory.OperationType.CLOSE,
            consumptionYuan = 0.0,
        )
        val current = OperationHistory.migrateConsumptionYuan(
            sourceSchemaVersion = OperationHistory.CURRENT_SCHEMA_VERSION,
            type = OperationHistory.OperationType.CLOSE,
            consumptionYuan = 0.0,
        )

        assertNull(migrated)
        assertEquals(0.0, current ?: -1.0, 0.0)
    }

    @Test
    fun exactCentFloatingPointValueFormatsAsOneCent() {
        val floatingCent = 0.03 - 0.02

        assertEquals("¥0.01", OperationHistory.formatConsumptionAmount(floatingCent))
    }

    @Test
    fun invalidLegacyBalanceDeltaIsNotReportedAsZeroConsumption() {
        val consumption = HotWaterSettlement.resolveConsumptionYuan(
            provisionalConsumptionYuan = null,
            startBalanceYuan = 15.0,
            settledBalanceYuan = 18.75,
        )

        assertNull(consumption)
    }

    @Test
    fun realBalanceDeltaWinsWhenExplicitAmountStillContainsReservation() {
        val consumption = HotWaterSettlement.resolveConsumptionYuan(
            provisionalConsumptionYuan = 5.0,
            startBalanceYuan = 20.0,
            settledBalanceYuan = 18.75,
        )

        assertEquals(1.25, consumption ?: -1.0, 0.001)
    }

    @Test
    fun subCentConsumptionKeepsPrecisionAndUsesNonMisleadingLabel() {
        val payload = JSONObject().put(
            "data",
            JSONObject().put("consumeMoney", 1),
        )
        val history = OperationHistory(
            type = OperationHistory.OperationType.CLOSE,
            success = true,
            message = "closed",
            consumptionYuan = HotWaterPayload.consumptionYuan(payload),
        )

        assertEquals(0.001, history.consumptionYuan ?: -1.0, 0.000_001)
        assertEquals("< ¥0.01", history.getConsumptionAmountLabel())
        assertEquals("消费金额 < ¥0.01", history.getConsumptionLabel())
    }

    @Test
    fun positiveMilliAmountWinsOverStaleFormattedZero() {
        val payload = JSONObject().put(
            "data",
            JSONObject()
                .put("consumeMoneyStr", "0.000")
                .put("consumeMoney", 1_250),
        )

        assertEquals(1.25, HotWaterPayload.consumptionYuan(payload) ?: -1.0, 0.001)
        assertEquals(1.25, HotWaterPayload.consumptionYuanIncludingZero(payload) ?: -1.0, 0.001)
    }

    private class FakeSessionStore(
        private var orderNo: String? = null,
        var usageSession: UsageSession? = null,
        pendingSettlements: List<PendingSettlement> = emptyList(),
    ) : HotWaterSessionStore {
        val pendingSettlements = pendingSettlements.toMutableList()
        val updatedHistorySettlements = mutableMapOf<Long, Double>()

        override fun loadOrderNo(): String? = orderNo

        override fun saveOrderNo(orderNo: String) {
            this.orderNo = orderNo
        }

        override fun clearOrderNo() {
            orderNo = null
        }

        override fun loadUsageSession(): UsageSession? = usageSession

        override fun startUsageSessionIfAbsent(
            startBalanceYuan: Double?,
            startBalanceIsBeforePreauthorization: Boolean,
        ): UsageSession {
            val existing = usageSession
            if (existing?.startBalanceYuan != null) return existing
            if (existing != null && startBalanceYuan == null) return existing
            return UsageSession(
                startedAtMillis = existing?.startedAtMillis ?: System.currentTimeMillis(),
                startBalanceYuan = startBalanceYuan,
                startBalanceIsBeforePreauthorization = startBalanceIsBeforePreauthorization,
            ).also { usageSession = it }
        }

        override fun clearUsageSession() {
            usageSession = null
        }

        override fun shouldIgnoreRecoveredOrder(windowMs: Long): Boolean = false

        override fun loadPendingSettlements(): List<PendingSettlement> {
            return pendingSettlements.toList()
        }

        override fun savePendingSettlement(settlement: PendingSettlement) {
            pendingSettlements.removeAll { it.orderNo == settlement.orderNo }
            pendingSettlements.add(settlement)
        }

        override fun removePendingSettlement(orderNo: String) {
            pendingSettlements.removeAll { it.orderNo == orderNo }
        }

        override fun updateHistorySettlement(
            settlement: PendingSettlement,
            consumptionYuan: Double,
        ) {
            updatedHistorySettlements[settlement.historyId] = consumptionYuan
        }
    }

    private class FakeHotWaterApi(
        balanceResponses: List<JSONObject>,
        statusResponses: List<JSONObject>,
        private val openResponse: JSONObject = successPayload(),
        private val openResultResponse: JSONObject = successPayload(),
        private val closeResponse: JSONObject = successPayload(),
        private val closeResultResponse: JSONObject = successPayload(),
        consumeResultResponses: List<JSONObject> = listOf(JSONObject()),
        private val failFirstBalanceRequest: Boolean = false,
        private val balanceFailureAtCall: Int? = null,
        private val balanceFailure: Throwable = IOException("Balance unavailable"),
    ) : HotWaterRemoteDataSource {
        private val balances = ArrayDeque(balanceResponses)
        private val statuses = ArrayDeque(statusResponses)
        private val consumeResults = ArrayDeque(consumeResultResponses)
        var balanceCalls: Int = 0
            private set
        var openCalls: Int = 0
            private set

        override suspend fun userInfo(config: HotWaterConfig): JSONObject {
            return JSONObject().put(
                "data",
                JSONObject().put("telephone", config.telephone),
            )
        }

        override suspend fun deviceInfo(config: HotWaterConfig): JSONObject {
            return JSONObject().put(
                "data",
                JSONObject()
                    .put("deviceName", "Test device")
                    .put("roomName", "Test room"),
            )
        }

        override suspend fun balance(config: HotWaterConfig): JSONObject {
            balanceCalls += 1
            if (balanceFailureAtCall == balanceCalls) {
                throw balanceFailure
            }
            if (failFirstBalanceRequest && balanceCalls == 1) {
                throw IOException("Active balance unavailable")
            }
            return nextOrLast(balances)
        }

        override suspend fun status(config: HotWaterConfig): JSONObject = nextOrLast(statuses)

        override suspend fun openHotWater(config: HotWaterConfig): JSONObject {
            openCalls += 1
            return openResponse
        }

        override suspend fun openResult(config: HotWaterConfig, orderNo: String?): JSONObject {
            return openResultResponse
        }

        override suspend fun closeHotWater(config: HotWaterConfig, orderNo: String): JSONObject {
            return closeResponse
        }

        override suspend fun closeResult(config: HotWaterConfig, orderNo: String): JSONObject {
            return closeResultResponse
        }

        override suspend fun consumeResult(config: HotWaterConfig, orderNo: String): JSONObject {
            return nextOrLast(consumeResults)
        }

        private fun nextOrLast(queue: ArrayDeque<JSONObject>): JSONObject {
            check(queue.isNotEmpty()) { "Missing fake response" }
            return if (queue.size > 1) queue.removeFirst() else queue.first
        }
    }

    companion object {
        private val completeConfig = HotWaterConfig(
            projectId = 1,
            userId = 2,
            accountId = 3,
            loginCode = "login-code",
            telephone = "13800138000",
            snCode = "device-sn",
            phoneSystem = "Android",
            version = "1.4.1",
        )

        private fun successPayload(): JSONObject {
            return JSONObject()
                .put("success", true)
                .put("errorCode", 0)
                .put("data", JSONObject())
        }

        private fun balancePayload(yuan: Double): JSONObject {
            return JSONObject().put(
                "data",
                JSONObject().put("accountRealMoney", (yuan * 1_000).toLong()),
            )
        }

        private fun pendingSettlement(
            historyId: Long,
            orderNo: String,
            closedAtMillis: Long,
            startBalanceYuan: Double,
            activeBalanceYuan: Double,
        ): PendingSettlement {
            return PendingSettlement(
                historyId = historyId,
                orderNo = orderNo,
                closedAtMillis = closedAtMillis,
                usageStartedAtMillis = null,
                usageDurationMillis = null,
                startBalanceYuan = startBalanceYuan,
                activeBalanceYuan = activeBalanceYuan,
                provisionalConsumptionYuan = null,
                finalConsumptionYuan = null,
                zeroConsumptionReported = false,
            )
        }
    }
}
