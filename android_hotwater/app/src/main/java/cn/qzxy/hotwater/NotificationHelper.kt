package cn.qzxy.hotwater

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    private const val CHANNEL_ID = "hotwater_live"
    private const val CHANNEL_NAME = "热水实况"
    private const val REQUEST_CODE_CLOSE = 1002
    private const val OPEN_CHANNEL = "hotwater_open_result"
    private const val CLOSE_CHANNEL = "hotwater_close_result"
    private const val OPEN_ID = 1101
    private const val CLOSE_ID = 1102

    fun cancelDisabledNotifications(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!FeedbackPreferences.enabled(context, FeedbackOption.OPEN_NOTIFICATION)) manager.cancel(OPEN_ID)
        if (!FeedbackPreferences.enabled(context, FeedbackOption.CLOSE_NOTIFICATION)) manager.cancel(CLOSE_ID)
    }

    fun notifyOperation(context: Context, type: OperationHistory.OperationType, result: OperationResult) {
        if (type == OperationHistory.OperationType.REFRESH) return
        val closing = type == OperationHistory.OperationType.CLOSE
        val option = if (closing) FeedbackOption.CLOSE_NOTIFICATION else FeedbackOption.OPEN_NOTIFICATION
        if (!FeedbackPreferences.enabled(context, option)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) return
        val channelId = if (closing) CLOSE_CHANNEL else OPEN_CHANNEL
        manager.createNotificationChannel(NotificationChannel(channelId,
            if (closing) "关水结果" else "开水结果", NotificationManager.IMPORTANCE_DEFAULT).apply {
            enableVibration(false)
        })
        val pending = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_qs_tile).setContentTitle(result.title)
            .setContentText(HotWaterUiText.firstMeaningfulLine(result.summary))
            .setContentIntent(pending).setAutoCancel(true).build()
        // A notification permission change cannot turn a completed water operation into a failure.
        runCatching { manager.notify(if (closing) CLOSE_ID else OPEN_ID, notification) }
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "热水开启时的实时计时与关闭入口"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun canPostPromotedNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.canPostPromotedNotifications()
    }

    fun buildActiveNotification(context: Context, usageStartedAtMillis: Long): Notification {
        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val closeIntent = Intent(context, HotWaterNotificationService::class.java).apply {
            action = HotWaterNotificationService.ACTION_CLOSE
        }
        val closePending = PendingIntent.getService(
            context,
            REQUEST_CODE_CLOSE,
            closeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val expanded = android.widget.RemoteViews(context.packageName, R.layout.notification_hotwater_active).apply {
            val elapsed = (System.currentTimeMillis() - usageStartedAtMillis).coerceAtLeast(0L)
            setChronometer(R.id.notification_duration, android.os.SystemClock.elapsedRealtime() - elapsed, null, true)
            setOnClickPendingIntent(R.id.notification_close, closePending)
        }

        val promoted = FeedbackPreferences.enabled(context, FeedbackOption.PROMOTED_NOTIFICATION) &&
            canPostPromotedNotifications(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_tile)
            .setLargeIcon(Icon.createWithResource(context, R.mipmap.ic_launcher_round))
            .setContentTitle("热水已开启")
            .setWhen(usageStartedAtMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(false)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setRequestPromotedOngoing(promoted)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        // Android 16 rejects custom RemoteViews for native promoted live updates. Keep
        // that system template when promotion is available; other hosts get the timer/stop row.
        if (promoted) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText("热水已开启"))
                .addAction(R.drawable.ic_notification_stop, "关闭热水", closePending)
        } else {
            builder.setCustomBigContentView(expanded).setStyle(NotificationCompat.DecoratedCustomViewStyle())
        }
        return builder.build()
    }
}
