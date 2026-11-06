package cn.qzxy.hotwater

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

enum class FeedbackOption(val key: String, val defaultEnabled: Boolean = true) {
    HAPTICS("haptics"),
    OPEN_NOTIFICATION("open_notification"),
    CLOSE_NOTIFICATION("close_notification"),
    LIVE_NOTIFICATION("live_notification"),
    PROMOTED_NOTIFICATION("promoted_notification"),
}

object FeedbackPreferences {
    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences("hotwater_feedback_preferences", Context.MODE_PRIVATE)

    fun enabled(context: Context, option: FeedbackOption): Boolean =
        preferences(context).getBoolean(option.key, option.defaultEnabled)

    fun set(context: Context, option: FeedbackOption, enabled: Boolean) {
        preferences(context).edit { putBoolean(option.key, enabled) }
        if (!enabled) NotificationHelper.cancelDisabledNotifications(context)
        if (option == FeedbackOption.LIVE_NOTIFICATION || option == FeedbackOption.PROMOTED_NOTIFICATION) {
            if (FeedbackPreferences.enabled(context, FeedbackOption.LIVE_NOTIFICATION) &&
                !HotWaterConfigStore(context).loadOrderNo().isNullOrBlank()) {
                runCatching { HotWaterNotificationService.show(context) }
            } else {
                HotWaterNotificationService.dismiss(context)
            }
        }
    }

    fun clear(context: Context) { preferences(context).edit { clear() } }
}
