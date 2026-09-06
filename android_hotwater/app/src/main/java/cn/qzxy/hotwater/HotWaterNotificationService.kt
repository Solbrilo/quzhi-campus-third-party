package cn.qzxy.hotwater

import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.service.quicksettings.TileService
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HotWaterNotificationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile
    private var closing = false
    private var activeStateSyncJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                syncNotificationState()
                ensureActiveStateSync()
            }
            ACTION_DISMISS -> {
                stopActiveStateSync()
                stopSelf()
            }
            ACTION_CLOSE -> {
                stopActiveStateSync()
                closeAndDismiss()
            }
            else -> {
                syncNotificationState()
                ensureActiveStateSync()
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep the foreground service visible after the activity is removed from Recents.
        syncNotificationState()
        ensureActiveStateSync()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopActiveStateSync()
        scope.cancel()
        cancelNotification()
        super.onDestroy()
    }

    private fun syncNotificationState() {
        val store = HotWaterConfigStore(this)
        val stateGeneration = store.loadStateGeneration()
        if (store.loadOrderNo().isNullOrBlank()) {
            stopActiveStateSync()
            stopSelf()
            return
        }
        val usageStartedAtMillis = store.loadUsageSession()?.startedAtMillis
            ?: store.startUsageSessionIfAbsent(
                startBalanceYuan = null,
                expectedStateGeneration = stateGeneration,
            )?.startedAtMillis
            ?: run {
                stopSelf()
                return
            }
        showNotification(usageStartedAtMillis)
    }

    private fun ensureActiveStateSync() {
        if (activeStateSyncJob?.isActive == true ||
            HotWaterConfigStore(this).loadOrderNo().isNullOrBlank()) {
            return
        }
        activeStateSyncJob = scope.launch {
            try {
                while (true) {
                    delay(ACTIVE_STATE_SYNC_INTERVAL_MS)
                    val store = HotWaterConfigStore(this@HotWaterNotificationService)
                    if (store.loadOrderNo().isNullOrBlank()) {
                        break
                    }
                    val stateGeneration = store.loadStateGeneration()
                    val config = RuntimeDeviceInfoResolver.enrichConfig(
                        this@HotWaterNotificationService,
                        store.loadConfig(),
                    )
                    if (!config.isComplete()) {
                        break
                    }

                    val result = try {
                        withContext(Dispatchers.IO) {
                            HotWaterRepository(HotWaterApi(), store).check(config)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: CredentialExpiredException) {
                        if (store.markCredentialsExpired(config.loginCode)) {
                            store.saveWidgetState(
                                title = "凭证已过期",
                                message = CREDENTIAL_EXPIRED_MESSAGE,
                                isBusy = false,
                                tone = "warning",
                                expectedStateGeneration = stateGeneration,
                            )
                            refreshSurfaces()
                        }
                        break
                    } catch (_: Exception) {
                        continue
                    }

                    if (store.loadStateGeneration() != stateGeneration) {
                        break
                    }
                    if (result.automaticClose && result.historyId != null) {
                        store.saveOperationResultHistory(
                            type = OperationHistory.OperationType.CLOSE,
                            result = result,
                            expectedStateGeneration = stateGeneration,
                        )
                        store.saveWidgetState(
                            title = result.title,
                            message = HotWaterUiText.firstMeaningfulLine(result.summary),
                            isBusy = false,
                            tone = "warning",
                            expectedStateGeneration = stateGeneration,
                        )
                        if (store.loadPendingSettlements().isNotEmpty()) {
                            HotWaterSettlementSyncScheduler.enqueue(this@HotWaterNotificationService)
                        }
                    }
                    refreshSurfaces()
                    if (store.loadOrderNo().isNullOrBlank()) {
                        break
                    }
                }
            } finally {
                if (activeStateSyncJob === currentCoroutineContext()[Job]) {
                    activeStateSyncJob = null
                }
            }
            if (HotWaterConfigStore(this@HotWaterNotificationService).loadOrderNo().isNullOrBlank()) {
                stopSelf()
            }
        }
    }

    private fun stopActiveStateSync() {
        activeStateSyncJob?.cancel()
        activeStateSyncJob = null
    }

    private fun refreshSurfaces() {
        HotWaterWidgetUi.updateAllWidgets(this)
        TileService.requestListeningState(
            this,
            ComponentName(this, HotWaterTileService::class.java),
        )
    }

    private fun showNotification(usageStartedAtMillis: Long) {
        NotificationHelper.ensureChannel(this)
        startForeground(
            NOTIFICATION_ID,
            NotificationHelper.buildActiveNotification(this, usageStartedAtMillis),
        )
    }

    private fun cancelNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.cancel(NOTIFICATION_ID)
    }

    private fun closeAndDismiss() {
        if (closing) return
        val store = HotWaterConfigStore(this)
        val stateGeneration = store.loadStateGeneration()
        val config = RuntimeDeviceInfoResolver.enrichConfig(this, store.loadConfig())

        if (!config.isComplete()) {
            stopSelf()
            return
        }

        closing = true
        scope.launch {
            try {
                val repository = HotWaterRepository(HotWaterApi(), store)
                val result = withContext(Dispatchers.IO) { repository.close(config) }
                store.saveOperationResultHistory(
                    type = OperationHistory.OperationType.CLOSE,
                    result = result,
                    expectedStateGeneration = stateGeneration,
                )
                if (store.loadStateGeneration() != stateGeneration) {
                    stopSelf()
                    return@launch
                }
                if (result.historyId != null) {
                    HotWaterSettlementSyncScheduler.enqueue(this@HotWaterNotificationService)
                }
                HotWaterWidgetUi.updateAllWidgets(this@HotWaterNotificationService)
                TileService.requestListeningState(
                    this@HotWaterNotificationService,
                    ComponentName(this@HotWaterNotificationService, HotWaterTileService::class.java),
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@HotWaterNotificationService,
                        HotWaterUiText.firstMeaningfulLine(result.summary),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(
                    this@HotWaterNotificationService,
                    "关闭热水失败，请重试",
                    Toast.LENGTH_SHORT,
                ).show()
            } finally {
                closing = false
                if (store.loadOrderNo().isNullOrBlank()) {
                    stopSelf()
                } else {
                    syncNotificationState()
                }
            }
        }
    }

    companion object {
        const val ACTION_SHOW = "cn.qzxy.hotwater.SHOW_NOTIFICATION"
        const val ACTION_DISMISS = "cn.qzxy.hotwater.DISMISS_NOTIFICATION"
        const val ACTION_CLOSE = "cn.qzxy.hotwater.CLOSE_FROM_NOTIFICATION"
        private const val NOTIFICATION_ID = 1001
        private const val ACTIVE_STATE_SYNC_INTERVAL_MS = 30_000L

        fun show(context: Context) {
            val intent = Intent(context, HotWaterNotificationService::class.java).apply {
                action = ACTION_SHOW
            }
            context.startForegroundService(intent)
        }

        fun dismiss(context: Context) {
            val intent = Intent(context, HotWaterNotificationService::class.java).apply {
                action = ACTION_DISMISS
            }
            context.startService(intent)
        }
    }
}
