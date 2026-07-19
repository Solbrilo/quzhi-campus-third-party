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
import androidx.core.graphics.drawable.IconCompat

object NotificationHelper {
    private const val CHANNEL_ID = "hotwater_live"
    private const val CHANNEL_NAME = "热水实况"
    private const val REQUEST_CODE_CLOSE = 1002

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
        val closeAction = NotificationCompat.Action.Builder(
            IconCompat.createWithResource(context, R.drawable.ic_hotwater_stop),
            "关闭热水",
            closePending,
        ).build()

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_tile)
            .setLargeIcon(Icon.createWithResource(context, R.mipmap.ic_launcher_round))
            .setContentTitle("热水已开启")
            .setWhen(usageStartedAtMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(false)
            .setContentIntent(openPending)
            .addAction(closeAction)
            .setStyle(NotificationCompat.BigTextStyle().bigText("热水已开启"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setRequestPromotedOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
