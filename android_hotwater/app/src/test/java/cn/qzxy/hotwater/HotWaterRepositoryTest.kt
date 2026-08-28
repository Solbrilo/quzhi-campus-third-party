package cn.qzxy.hotwater

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(OperationOutcome.PENDING, result.outcome)
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
    fun check_preservesExplicitDeviceOnlineStateAndDoesNotInferFromSuccess() = runBlocking {
        val offlineStatus = successPayload().apply {
            getJSONObject("data").put("online", false)
        }
        val offlineResult = HotWaterRepository(
            FakeHotWaterApi(
                balanceResponses = listOf(balancePayload(15.0)),
                statusResponses = listOf(offlineStatus),
            ),
            FakeSessionStore(),
        ).check(completeConfig)

        assertEquals(false, offlineResult.snapshot?.isDeviceOnline)

        val alternateOfflineResult = HotWaterRepository(
            FakeHotWaterApi(
                balanceResponses = listOf(balancePayload(15.0)),
                statusResponses = listOf(
                    successPayload().apply {
                        put("errorMessage", "设备离线")
                    },
                ),
            ),
            FakeSessionStore(),
        ).check(completeConfig)

        assertEquals(false, alternateOfflineResult.snapshot?.isDeviceOnline)

        val onlineResult = HotWaterRepository(
            FakeHotWaterApi(
                balanceResponses = listOf(balancePayload(15.0)),
                statusResponses = listOf(
                    successPayload().apply {
                        getJSONObject("data").put("online", true)
                    },
                ),
            ),
            FakeSessionStore(),
        ).check(completeConfig)

        assertEquals(true, onlineResult.snapshot?.isDeviceOnline)

        val unknownResult = HotWaterRepository(
            FakeHotWaterApi(
                balanceResponses = listOf(balancePayload(15.0)),
                statusResponses = listOf(
                    JSONObject()
                        .put("success", false)
                        .put("errorCode", 503),
                ),
            ),
            FakeSessionStore(),
        ).check(completeConfig)

        assertNull(unknownResult.snapshot?.isDeviceOnline)
    }

    @Test
    fun check_doesNotUseDeviceInfoOnlineStatusIdAsOnlineEvidence() = runBlocking {
        val result = HotWaterRepository(
            FakeHotWaterApi(
                balanceResponses = listOf(balancePayload(15.0)),
                statusResponses = listOf(successPayload()),
                deviceInfoPayload = JSONObject()
                    .put("data", JSONObject().put("onlineStatusId", 1)),
            ),
            FakeSessionStore(),
        ).check(completeConfig)

        assertNull(result.snapshot?.isDeviceOnline)
    }

    @Test
    fun open_networkSlowResultMarksDeviceOfflineEvenWhenStatusRefreshSucceeds() = runBlocking {
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(15.0)),
            statusResponses = listOf(successPayload()),
            openResultResponses = listOf(
                JSONObject()
                    .put("success", true)
                    .put("errorCode", 1)
                    .put("errorMessage", "网络通讯慢,请稍后再试")
                    .put("data", JSONObject.NULL),
            ),
        )

        val result = HotWaterRepository(api, FakeSessionStore()).open(
            config = completeConfig,
            pollAttempts = 1,
            pollDelayMs = 0L,
        )

        assertEquals(false, result.snapshot?.isDeviceOnline)
        assertEquals("热水器不在线", result.title)
        assertEquals(OperationOutcome.WARNING, result.outcome)
        assertEquals(
            "热水器不在线，请确认寝室供电后重试。",
            result.snapshot?.statusLabel,
        )
        assertTrue(result.summary.contains("请确认寝室供电后重试"))
    }

    @Test
    fun open_networkSlowResultIsPreservedInFallbackSnapshot() = runBlocking {
        val result = HotWaterRepository(
            FakeHotWaterApi(
                balanceResponses = listOf(balancePayload(15.0)),
                statusResponses = listOf(successPayload()),
                openResultResponses = listOf(
                    JSONObject()
                        .put("success", true)
                        .put("errorCode", 1)
                        .put("errorMessage", "网络通讯慢,请稍后再试")
                        .put("data", JSONObject.NULL),
                ),
                balanceFailureAtCall = 2,
            ),
            FakeSessionStore(),
        ).open(
            config = completeConfig,
            pollAttempts = 1,
            pollDelayMs = 0L,
        )

        assertEquals(false, result.snapshot?.isDeviceOnline)
    }

    @Test
    fun open_successfulResultMarksDeviceOnline() = runBlocking {
        val result = HotWaterRepository(
            FakeHotWaterApi(
                balanceResponses = listOf(balancePayload(15.0)),
                statusResponses = listOf(successPayload()),
                openResultResponses = listOf(
                    successPayload().apply {
                        getJSONObject("data")
                            .put("orderNo", "order-open")
                            .put("state", 1)
                            .put("result", 0)
                    },
                ),
            ),
            FakeSessionStore(),
        ).open(
            config = completeConfig,
            pollAttempts = 1,
            pollDelayMs = 0L,
        )

        assertEquals(true, result.snapshot?.isDeviceOnline)
    }

    @Test
    fun open_failedResultWithDataDoesNotMarkDeviceOnline() = runBlocking {
        val result = HotWaterRepository(
            FakeHotWaterApi(
                balanceResponses = listOf(balancePayload(15.0)),
                statusResponses = listOf(successPayload()),
                openResultResponses = listOf(
                    JSONObject()
                        .put("success", false)
                        .put("errorCode", 0)
                        .put("isSuccess", true)
                        .put("data", JSONObject().put("orderNo", "order-failed")),
                ),
            ),
            FakeSessionStore(),
        ).open(
            config = completeConfig,
            pollAttempts = 1,
            pollDelayMs = 0L,
        )

        assertNull(result.snapshot?.isDeviceOnline)
    }

    @Test
    fun open_emptyOrUnrelatedResultDataDoesNotMarkDeviceOnline() = runBlocking {
        val result = HotWaterRepository(
            FakeHotWaterApi(
                balanceResponses = listOf(balancePayload(15.0)),
                statusResponses = listOf(successPayload()),
                openResultResponses = listOf(
                    successPayload(),
                ),
            ),
            FakeSessionStore(),
        ).open(
            config = completeConfig,
            pollAttempts = 1,
            pollDelayMs = 0L,
        )

        assertNull(result.snapshot?.isDeviceOnline)
    }

    @Test
    fun open_resultWithoutConfirmedStateAndResultDoesNotMarkDeviceOnline() = runBlocking {
        val result = HotWaterRepository(
            FakeHotWaterApi(
                balanceResponses = listOf(balancePayload(15.0)),
                statusResponses = listOf(successPayload()),
                openResultResponses = listOf(
                    successPayload().apply {
                        getJSONObject("data").put("orderNo", "order-incomplete")
                    },
                ),
            ),
            FakeSessionStore(),
        ).open(
            config = completeConfig,
            pollAttempts = 1,
            pollDelayMs = 0L,
        )

        assertNull(result.snapshot?.isDeviceOnline)
    }

    @Test
    fun open_mismatchedOrderResultCannotOverwriteOfflineProbe() = runBlocking {
        val result = HotWaterRepository(
            FakeHotWaterApi(
                balanceResponses = listOf(balancePayload(15.0)),
                statusResponses = listOf(successPayload()),
                openResponse = successPayload().apply {
                    getJSONObject("data").put("orderNo", "order-current")
                },
                openResultResponses = listOf(
                    JSONObject()
                        .put("success", true)
                        .put("errorCode", 1)
                        .put("errorMessage", "网络通讯慢,请稍后再试")
                        .put("data", JSONObject.NULL),
                    successPayload().apply {
                        getJSONObject("data").put("orderNo", "order-other")
                    },
                ),
            ),
            FakeSessionStore(),
        ).open(
            config = completeConfig,
            pollAttempts = 2,
            pollDelayMs = 0L,
        )

        assertEquals(false, result.snapshot?.isDeviceOnline)
    }

    @Test
    fun open_unknownOrderOfflineProbeCannotBeOverwrittenByLaterSuccess() = runBlocking {
        val result = HotWaterRepository(
            FakeHotWaterApi(
                balanceResponses = listOf(balancePayload(15.0)),
                statusResponses = listOf(successPayload()),
                openResultResponses = listOf(
                    JSONObject()
                        .put("success", true)
                        .put("errorCode", 1)
                        .put("errorMessage", "网络通讯慢,请稍后再试")
                        .put("data", JSONObject.NULL),
                    successPayload().apply {
                        getJSONObject("data")
                            .put("orderNo", "order-after-offline")
                            .put("state", 1)
                            .put("result", 0)
                    },
                ),
            ),
            FakeSessionStore(),
        ).open(
            config = completeConfig,
            pollAttempts = 2,
            pollDelayMs = 0L,
        )

        assertEquals(false, result.snapshot?.isDeviceOnline)
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
        assertEquals(1, store.pendingSettlements.size)
        assertEquals(
            1.25,
            store.pendingSettlements.single().finalConsumptionYuan ?: -1.0,
            0.001,
        )
    }

    @Test
    fun close_acceptedRequestReturnsWithoutBlockingOnConfirmationOrSettlement() = runBlocking {
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(15.0)),
            statusResponses = listOf(
                successPayload().apply {
                    getJSONObject("data").put("orderNo", "order-fast-close")
                },
            ),
            closeResponse = successPayload().apply {
                getJSONObject("data").put("consumeMoney", 0)
            },
        )
        val store = FakeSessionStore(
            orderNo = "order-fast-close",
            usageSession = UsageSession(
                startedAtMillis = System.currentTimeMillis() - 20_000L,
                startBalanceYuan = 20.0,
                startBalanceIsBeforePreauthorization = true,
            ),
        )

        val result = HotWaterRepository(api, store).close(completeConfig)

        assertEquals(0, api.closeResultCalls)
        assertEquals(0, api.consumeResultCalls)
        assertTrue(result.summary.contains("已受理"))
        assertNull(result.consumptionYuan)
        assertNull(store.loadOrderNo())
        assertEquals(1, store.pendingSettlements.size)
        assertTrue(!store.pendingSettlements.single().closeConfirmed)
        assertTrue(store.pendingSettlements.single().zeroConsumptionReported)
    }

    @Test
    fun close_unacceptedRequestUsesBoundedPollingUntilConfirmed() = runBlocking {
        val notAccepted = JSONObject()
            .put("success", false)
            .put("errorCode", 500)
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(15.0)),
            statusResponses = listOf(
                successPayload().apply {
                    getJSONObject("data").put("orderNo", "order-confirm-later")
                },
            ),
            closeResponse = notAccepted,
            closeResultResponses = listOf(notAccepted, successPayload()),
        )
        val store = FakeSessionStore(orderNo = "order-confirm-later")

        val result = HotWaterRepository(api, store).close(
            config = completeConfig,
            pollAttempts = 3,
            pollDelayMs = 0L,
        )

        assertEquals(2, api.closeResultCalls)
        assertEquals("关热水完成", result.title)
        assertNull(store.loadOrderNo())
        assertTrue(store.pendingSettlements.single().closeConfirmed)
    }

    @Test
    fun open_isBlockedBrieflyWhileAcceptedCloseStillLacksConfirmation() = runBlocking {
        val notConfirmed = JSONObject()
            .put("success", false)
            .put("errorCode", 500)
        val pending = pendingSettlement(
            historyId = 46L,
            orderNo = "order-gate",
            closedAtMillis = System.currentTimeMillis(),
            startBalanceYuan = 20.0,
            activeBalanceYuan = 15.0,
        ).copy(balanceFallbackAllowed = false)
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(15.0)),
            statusResponses = listOf(successPayload()),
            closeResultResponse = notConfirmed,
        )
        val store = FakeSessionStore(pendingSettlements = listOf(pending))

        val result = HotWaterRepository(api, store).open(completeConfig)

        assertEquals("关水确认中", result.title)
        assertEquals(0, api.openCalls)
        assertTrue(result.outcome == OperationOutcome.PENDING)
    }

    @Test
    fun open_neverResurrectsTheSameOrderWhileItsSettlementIsPending() = runBlocking {
        val pending = pendingSettlement(
            historyId = 48L,
            orderNo = "order-stale-open-status",
            closedAtMillis = System.currentTimeMillis() - 30_000L,
            startBalanceYuan = 20.0,
            activeBalanceYuan = 15.0,
        ).copy(closeConfirmed = true)
        val staleActiveStatus = successPayload().apply {
            getJSONObject("data").put("orderNo", pending.orderNo)
        }
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(15.0)),
            statusResponses = listOf(staleActiveStatus),
        )
        val store = FakeSessionStore(pendingSettlements = listOf(pending))

        val result = HotWaterRepository(api, store).open(completeConfig)

        assertEquals("账单同步中", result.title)
        assertEquals(0, api.openCalls)
        assertNull(store.loadOrderNo())
        assertTrue(result.outcome == OperationOutcome.PENDING)
    }

    @Test
    fun closeAndSettlementReconciliationAreSerializedAcrossRepositories() = runBlocking {
        val closeRequestStarted = CompletableDeferred<Unit>()
        val releaseCloseRequest = CompletableDeferred<Unit>()
        val checkAuthenticationCompleted = CompletableDeferred<Unit>()
        val activeStatus = successPayload().apply {
            getJSONObject("data").put("orderNo", "order-concurrent-close")
        }
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(15.0)),
            statusResponses = listOf(activeStatus),
            onCloseRequest = {
                closeRequestStarted.complete(Unit)
                releaseCloseRequest.await()
            },
            onUserInfoRequest = {
                checkAuthenticationCompleted.complete(Unit)
            },
        )
        val store = FakeSessionStore(orderNo = "order-concurrent-close")
        val foregroundRepository = HotWaterRepository(api, store)
        val workerRepository = HotWaterRepository(api, store)

        val closeResult = async(Dispatchers.Default) {
            foregroundRepository.close(completeConfig)
        }
        withTimeout(1_000L) { closeRequestStarted.await() }

        val checkResult = async(Dispatchers.Default) {
            workerRepository.check(completeConfig)
        }
        withTimeout(1_000L) { checkAuthenticationCompleted.await() }
        delay(50L)
        assertFalse(checkResult.isCompleted)

        releaseCloseRequest.complete(Unit)
        withTimeout(2_000L) {
            closeResult.await()
            checkResult.await()
        }
        assertNull(store.loadOrderNo())
        assertEquals(1, store.pendingSettlements.size)
    }

    @Test
    fun close_waitForBackgroundReconciliationIsBounded() = runBlocking {
        val reconciliationStarted = CompletableDeferred<Unit>()
        val neverComplete = CompletableDeferred<Unit>()
        val oldPending = pendingSettlement(
            historyId = 50L,
            orderNo = "order-old-pending",
            closedAtMillis = System.currentTimeMillis(),
            startBalanceYuan = 20.0,
            activeBalanceYuan = 15.0,
        )
        val activeStatus = successPayload().apply {
            getJSONObject("data").put("orderNo", "order-active-close")
        }
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(15.0)),
            statusResponses = listOf(activeStatus),
            onCloseResultRequest = {
                reconciliationStarted.complete(Unit)
                neverComplete.await()
            },
        )
        val store = FakeSessionStore(
            orderNo = "order-active-close",
            usageSession = UsageSession(
                startedAtMillis = System.currentTimeMillis() - 1_000L,
                startBalanceYuan = 20.0,
                startBalanceIsBeforePreauthorization = true,
            ),
            pendingSettlements = listOf(oldPending),
        )
        val workerCheck = async(Dispatchers.Default) {
            HotWaterRepository(api, store).check(completeConfig)
        }
        withTimeout(1_000L) { reconciliationStarted.await() }

        val closeResult = withTimeout(4_000L) {
            HotWaterRepository(api, store).close(completeConfig)
        }

        assertEquals("order-active-close", store.pendingSettlements.maxBy { it.closedAtMillis }.orderNo)
        assertEquals(1, api.closeCalls)
        assertTrue(closeResult.historyId != null)
        withTimeout(2_000L) { workerCheck.await() }
        Unit
    }

    @Test
    fun close_isIdempotentWhenASecondEntryUsesTheSameOrder() = runBlocking {
        val activeStatus = successPayload().apply {
            getJSONObject("data").put("orderNo", "order-duplicate-close")
        }
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(15.0)),
            statusResponses = listOf(activeStatus),
        )
        val store = FakeSessionStore(
            orderNo = "order-duplicate-close",
            usageSession = UsageSession(
                startedAtMillis = System.currentTimeMillis() - 1_000L,
                startBalanceYuan = 20.0,
                startBalanceIsBeforePreauthorization = true,
            ),
        )
        val repository = HotWaterRepository(api, store)

        val first = repository.close(completeConfig)
        val second = repository.close(completeConfig)

        assertEquals(1, api.closeCalls)
        assertEquals(first.historyId, second.historyId)
        assertEquals(1, store.pendingSettlements.size)
    }

    @Test
    fun close_doesNotPersistAfterAccountGenerationChanges() = runBlocking {
        lateinit var store: FakeSessionStore
        val activeStatus = successPayload().apply {
            getJSONObject("data").put("orderNo", "order-old-account")
        }
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(15.0)),
            statusResponses = listOf(activeStatus),
            onCloseRequest = {
                store.replaceRuntimeStateForNewGeneration(
                    orderNo = "order-new-account",
                    usageStartedAtMillis = 999L,
                )
            },
        )
        store = FakeSessionStore(
            orderNo = "order-old-account",
            usageSession = UsageSession(
                startedAtMillis = 100L,
                startBalanceYuan = 20.0,
                startBalanceIsBeforePreauthorization = true,
            ),
        )

        var cancelled = false
        try {
            HotWaterRepository(api, store).close(completeConfig)
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertEquals("order-new-account", store.loadOrderNo())
        assertEquals(999L, store.loadUsageSession()?.startedAtMillis)
        assertTrue(store.pendingSettlements.isEmpty())
    }

    @Test
    fun check_doesNotReconcileNewGenerationWithOldConfig() = runBlocking {
        lateinit var store: FakeSessionStore
        val newPending = pendingSettlement(
            historyId = 49L,
            orderNo = "order-new-generation",
            closedAtMillis = System.currentTimeMillis(),
            startBalanceYuan = 20.0,
            activeBalanceYuan = 15.0,
        )
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(15.0)),
            statusResponses = listOf(successPayload()),
            onUserInfoRequest = {
                store.replaceRuntimeStateForNewGeneration(
                    orderNo = null,
                    usageStartedAtMillis = null,
                    pending = newPending,
                )
            },
        )
        store = FakeSessionStore()

        var cancelled = false
        try {
            HotWaterRepository(api, store).check(completeConfig)
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertEquals(listOf("order-new-generation"), store.pendingSettlements.map { it.orderNo })
        assertEquals(0, api.consumeResultCalls)
        assertEquals(0, api.closeResultCalls)
    }

    @Test
    fun open_doesNotSendControlAfterAccountGenerationChanges() = runBlocking {
        lateinit var store: FakeSessionStore
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(20.0)),
            statusResponses = listOf(successPayload()),
            onBalanceRequest = {
                store.replaceRuntimeStateForNewGeneration(
                    orderNo = null,
                    usageStartedAtMillis = null,
                )
            },
        )
        store = FakeSessionStore()

        var cancelled = false
        try {
            HotWaterRepository(api, store).open(completeConfig)
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertEquals(0, api.openCalls)
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
    fun check_updatesFinalAmountButHidesWalletUntilPreauthorizationSettles() = runBlocking {
        val pending = pendingSettlement(
            historyId = 44L,
            orderNo = "order-wallet-guard",
            closedAtMillis = System.currentTimeMillis() - 5_000L,
            startBalanceYuan = 20.0,
            activeBalanceYuan = 15.0,
        )
        val finalConsumeResult = successPayload().apply {
            getJSONObject("data").put("consumeMoney", 1_250)
        }
        val api = FakeHotWaterApi(
            balanceResponses = listOf(
                balancePayload(15.0),
                balancePayload(15.0),
                balancePayload(18.75),
                balancePayload(18.75),
            ),
            statusResponses = listOf(successPayload()),
            consumeResultResponses = listOf(finalConsumeResult),
        )
        val store = FakeSessionStore(pendingSettlements = listOf(pending))
        val repository = HotWaterRepository(api, store)

        val staleWalletResult = repository.check(completeConfig)

        assertEquals(1.25, store.updatedHistorySettlements[44L] ?: -1.0, 0.001)
        assertEquals(1.25, store.pendingSettlements.single().finalConsumptionYuan ?: -1.0, 0.001)
        assertNull(staleWalletResult.snapshot?.balanceYuan)
        assertEquals("--", staleWalletResult.snapshot?.balanceLabel)

        val settledWalletResult = repository.check(completeConfig)

        assertTrue(store.pendingSettlements.isEmpty())
        assertEquals(18.75, settledWalletResult.snapshot?.balanceYuan ?: -1.0, 0.001)
    }

    @Test
    fun check_neverResurrectsTheSameOrderWhileItsCloseIsPending() = runBlocking {
        val notConfirmed = JSONObject()
            .put("success", false)
            .put("errorCode", 500)
        val pending = pendingSettlement(
            historyId = 47L,
            orderNo = "order-stale-status",
            closedAtMillis = System.currentTimeMillis() - 30_000L,
            startBalanceYuan = 20.0,
            activeBalanceYuan = 15.0,
        )
        val staleActiveStatus = successPayload().apply {
            getJSONObject("data").put("orderNo", pending.orderNo)
        }
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(15.0)),
            statusResponses = listOf(staleActiveStatus),
            closeResultResponse = notConfirmed,
        )
        val store = FakeSessionStore(pendingSettlements = listOf(pending))

        val result = HotWaterRepository(api, store).check(completeConfig)

        assertNull(store.loadOrderNo())
        assertTrue(result.snapshot?.hasCachedOrder == false)
        assertEquals(1, store.pendingSettlements.size)
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
    fun check_expiredFinalAmountDropsOnlyTheStaleWalletGuard() = runBlocking {
        val expiredPending = pendingSettlement(
            historyId = 45L,
            orderNo = "order-expired-final",
            closedAtMillis = System.currentTimeMillis() - 11 * 60 * 1_000L,
            startBalanceYuan = 20.0,
            activeBalanceYuan = 15.0,
        ).copy(
            finalConsumptionYuan = 1.25,
            closeConfirmed = true,
        )
        val api = FakeHotWaterApi(
            balanceResponses = listOf(balancePayload(26.5)),
            statusResponses = listOf(successPayload()),
        )
        val store = FakeSessionStore(pendingSettlements = listOf(expiredPending))

        val result = HotWaterRepository(api, store).check(completeConfig)

        assertEquals(1.25, store.updatedHistorySettlements[45L] ?: -1.0, 0.001)
        assertTrue(store.pendingSettlements.isEmpty())
        assertEquals(26.5, result.snapshot?.balanceYuan ?: -1.0, 0.001)
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
        var stateGeneration: Long = 0L
        val pendingSettlements = pendingSettlements
            .map { pending ->
                if (pending.stateGeneration == -1L) pending.copy(stateGeneration = stateGeneration) else pending
            }
            .toMutableList()
        val updatedHistorySettlements = mutableMapOf<Long, Double>()

        override fun loadStateGeneration(): Long = stateGeneration

        override fun loadOrderNo(): String? = orderNo

        override fun saveOrderNo(orderNo: String) {
            this.orderNo = orderNo
        }

        override fun saveOrderNoForGeneration(orderNo: String, expectedStateGeneration: Long): Boolean {
            if (stateGeneration != expectedStateGeneration) return false
            this.orderNo = orderNo
            return true
        }

        override fun saveOrderNoUnlessPending(orderNo: String, expectedStateGeneration: Long?): Boolean {
            if (expectedStateGeneration != null && stateGeneration != expectedStateGeneration) return false
            if (pendingSettlements.any { it.orderNo == orderNo }) return false
            this.orderNo = orderNo
            return true
        }

        override fun clearOrderNo() {
            orderNo = null
        }

        override fun clearActiveUsageForGeneration(expectedStateGeneration: Long): Boolean {
            if (stateGeneration != expectedStateGeneration) return false
            orderNo = null
            usageSession = null
            return true
        }

        override fun clearUsageSessionForGeneration(expectedStateGeneration: Long): Boolean {
            if (stateGeneration != expectedStateGeneration) return false
            usageSession = null
            return true
        }

        override fun loadUsageSession(): UsageSession? = usageSession

        override fun startUsageSessionIfAbsent(
            startBalanceYuan: Double?,
            startBalanceIsBeforePreauthorization: Boolean,
            expectedStateGeneration: Long?,
        ): UsageSession? {
            if (expectedStateGeneration != null && stateGeneration != expectedStateGeneration) return null
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
            pendingSettlements.add(
                if (settlement.stateGeneration == -1L) {
                    settlement.copy(stateGeneration = stateGeneration)
                } else {
                    settlement
                },
            )
        }

        override fun savePendingSettlementAndEndUsage(
            settlement: PendingSettlement,
            expectedStateGeneration: Long,
        ): Boolean {
            if (stateGeneration != expectedStateGeneration) return false
            savePendingSettlement(settlement.copy(stateGeneration = expectedStateGeneration))
            orderNo = null
            usageSession = null
            return true
        }

        override fun removePendingSettlement(orderNo: String, expectedStateGeneration: Long?) {
            pendingSettlements.removeAll { it.orderNo == orderNo }
        }

        override fun updateHistorySettlement(
            settlement: PendingSettlement,
            consumptionYuan: Double,
        ) {
            updatedHistorySettlements[settlement.historyId] = consumptionYuan
        }

        fun replaceRuntimeStateForNewGeneration(
            orderNo: String?,
            usageStartedAtMillis: Long?,
            pending: PendingSettlement? = null,
        ) {
            stateGeneration += 1L
            this.orderNo = orderNo
            usageSession = usageStartedAtMillis?.let {
                UsageSession(
                    startedAtMillis = it,
                    startBalanceYuan = null,
                )
            }
            pendingSettlements.clear()
            pending?.let { pendingSettlements.add(it.copy(stateGeneration = stateGeneration)) }
        }
    }

    private class FakeHotWaterApi(
        balanceResponses: List<JSONObject>,
        statusResponses: List<JSONObject>,
        private val openResponse: JSONObject = successPayload(),
        private val openResultResponse: JSONObject = successPayload(),
        openResultResponses: List<JSONObject>? = null,
        private val closeResponse: JSONObject = successPayload(),
        private val closeResultResponse: JSONObject = successPayload(),
        closeResultResponses: List<JSONObject>? = null,
        consumeResultResponses: List<JSONObject> = listOf(JSONObject()),
        private val failFirstBalanceRequest: Boolean = false,
        private val balanceFailureAtCall: Int? = null,
        private val balanceFailure: Throwable = IOException("Balance unavailable"),
        private val deviceInfoPayload: JSONObject? = null,
        private val onCloseRequest: suspend () -> Unit = {},
        private val onUserInfoRequest: () -> Unit = {},
        private val onBalanceRequest: suspend () -> Unit = {},
        private val onCloseResultRequest: suspend () -> Unit = {},
    ) : HotWaterRemoteDataSource {
        private val balances = ArrayDeque(balanceResponses)
        private val statuses = ArrayDeque(statusResponses)
        private val openResults = ArrayDeque(openResultResponses ?: listOf(openResultResponse))
        private val consumeResults = ArrayDeque(consumeResultResponses)
        private val closeResults = ArrayDeque(closeResultResponses ?: listOf(closeResultResponse))
        var balanceCalls: Int = 0
            private set
        var openCalls: Int = 0
            private set
        var closeCalls: Int = 0
            private set
        var closeResultCalls: Int = 0
            private set
        var consumeResultCalls: Int = 0
            private set

        override suspend fun userInfo(config: HotWaterConfig): JSONObject {
            onUserInfoRequest()
            return JSONObject().put(
                "data",
                JSONObject().put("telephone", config.telephone),
            )
        }

        override suspend fun deviceInfo(config: HotWaterConfig): JSONObject {
            return deviceInfoPayload ?: JSONObject().put(
                "data",
                JSONObject()
                    .put("deviceName", "Test device")
                    .put("roomName", "Test room"),
            )
        }

        override suspend fun balance(config: HotWaterConfig): JSONObject {
            balanceCalls += 1
            onBalanceRequest()
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
            return nextOrLast(openResults)
        }

        override suspend fun closeHotWater(config: HotWaterConfig, orderNo: String): JSONObject {
            closeCalls += 1
            onCloseRequest()
            return closeResponse
        }

        override suspend fun closeResult(config: HotWaterConfig, orderNo: String): JSONObject {
            closeResultCalls += 1
            onCloseResultRequest()
            return nextOrLast(closeResults)
        }

        override suspend fun consumeResult(config: HotWaterConfig, orderNo: String): JSONObject {
            consumeResultCalls += 1
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
