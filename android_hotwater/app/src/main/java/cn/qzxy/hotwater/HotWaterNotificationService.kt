package cn.qzxy.hotwater

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showNotification()
            ACTION_DISMISS -> stopSelf()
            ACTION_CLOSE -> closeAndDismiss()
            else -> showNotification()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        cancelNotification()
        super.onDestroy()
    }

    private fun showNotification() {
        NotificationHelper.ensureChannel(this)
        startForeground(NOTIFICATION_ID, NotificationHelper.buildActiveNotification(this))
    }

    private fun cancelNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.cancel(NOTIFICATION_ID)
    }

    private fun closeAndDismiss() {
        val store = HotWaterConfigStore(this)
        val config = RuntimeDeviceInfoResolver.enrichConfig(this, store.loadConfig())

        if (!config.isComplete()) {
            stopSelf()
            return
        }

        scope.launch {
            try {
                val repository = HotWaterRepository(HotWaterApi(), store)
                val result = withContext(Dispatchers.IO) { repository.close(config) }
                store.saveOperationResultHistory(OperationHistory.OperationType.CLOSE, result)
                HotWaterWidgetUi.updateAllWidgets(this@HotWaterNotificationService)
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
            }
            stopSelf()
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
