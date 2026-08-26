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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HotWaterNotificationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile
    private var closing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> syncNotificationState()
            ACTION_DISMISS -> stopSelf()
            ACTION_CLOSE -> closeAndDismiss()
            else -> syncNotificationState()
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep the foreground service visible after the activity is removed from Recents.
        syncNotificationState()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        cancelNotification()
        super.onDestroy()
    }

    private fun syncNotificationState() {
        val store = HotWaterConfigStore(this)
        val stateGeneration = store.loadStateGeneration()
        if (store.loadOrderNo().isNullOrBlank()) {
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
