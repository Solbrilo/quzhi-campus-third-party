package cn.qzxy.hotwater

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.widget.RemoteViews
import java.util.Date

object HotWaterWidgetUi {
    const val ACTION_OPEN = "cn.qzxy.hotwater.widget.OPEN"
    const val ACTION_CLOSE = "cn.qzxy.hotwater.widget.CLOSE"
    const val ACTION_REFRESH = "cn.qzxy.hotwater.widget.REFRESH"

    fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, HotWaterWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isEmpty()) {
            return
        }

        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(
                appWidgetId,
                buildRemoteViews(context, appWidgetManager, appWidgetId),
            )
        }
    }

    private fun buildRemoteViews(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ): RemoteViews {
        val store = HotWaterConfigStore(context)
        val config = store.loadConfig()
        val widgetState = store.loadWidgetState()
        val hasCachedOrder = !store.loadOrderNo().isNullOrBlank()
        val isCompact = isCompactWidget(appWidgetManager, appWidgetId)

        val title = when {
            widgetState.title.isNotBlank() -> widgetState.title
            config.isComplete() -> "配置已就绪"
            else -> "未导入配置"
        }

        val message = when {
            widgetState.message.isNotBlank() -> widgetState.message
            config.isComplete() -> "点下面按钮就能直接开关热水。"
            else -> "先打开应用导入 1.chls 或手动填写参数。"
        }

        val statusChip = when {
            widgetState.isBusy -> "处理中"
            widgetState.tone == "error" -> "失败"
            widgetState.tone == "success" -> "已完成"
            widgetState.tone == "warning" -> "待配置"
            config.isComplete() -> "可操作"
            else -> "未配置"
        }

        val modeText = when {
            !config.isComplete() -> "待配置"
            hasCachedOrder -> "优先关热水"
            else -> "优先开热水"
        }

        val meta = when {
            widgetState.isBusy -> "$modeText · 请求执行中"
            widgetState.updatedAtMillis > 0L -> "$modeText · ${
                DateFormat.getTimeFormat(context).format(Date(widgetState.updatedAtMillis))
            }"
            config.isComplete() -> "$modeText · 点卡片可打开应用"
            else -> "$modeText · 点卡片去完成配置"
        }

        val layoutId = if (isCompact) R.layout.widget_hotwater_compact else R.layout.widget_hotwater

        return RemoteViews(context.packageName, layoutId).apply {
            if (!isCompact) {
                setTextViewText(R.id.widget_title, title)
                setTextViewText(R.id.widget_message, message)
                setTextViewText(R.id.widget_status_chip, statusChip)
                setTextViewText(R.id.widget_meta, meta)
                setInt(R.id.widget_status_chip, "setBackgroundResource", chipBackground(widgetState.tone, widgetState.isBusy))
            }
            setTextViewText(
                R.id.widget_open_button,
                when {
                    widgetState.isBusy && message.contains("开启") -> "开启中…"
                    widgetState.isBusy -> "请稍候"
                    else -> "开热水"
                },
            )
            setTextViewText(
                R.id.widget_close_button,
                when {
                    widgetState.isBusy && message.contains("关闭") -> "关闭中…"
                    widgetState.isBusy -> "请稍候"
                    else -> "关热水"
                },
            )
            setInt(R.id.widget_open_button, "setBackgroundResource", R.drawable.widget_glass_action_open)
            setInt(R.id.widget_close_button, "setBackgroundResource", R.drawable.widget_glass_action_close)
            setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
            setOnClickPendingIntent(
                R.id.widget_open_button,
                actionPendingIntent(context, appWidgetId, ACTION_OPEN, appWidgetId * 10 + 1),
            )
            setOnClickPendingIntent(
                R.id.widget_close_button,
                actionPendingIntent(context, appWidgetId, ACTION_CLOSE, appWidgetId * 10 + 2),
            )
        }
    }

    private fun isCompactWidget(appWidgetManager: AppWidgetManager, appWidgetId: Int): Boolean {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        return minWidth in 1..179
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun chipBackground(tone: String, isBusy: Boolean): Int {
        return when {
            isBusy -> R.drawable.widget_status_busy
            tone == "error" -> R.drawable.widget_status_error
            tone == "success" -> R.drawable.widget_status_success
            tone == "warning" -> R.drawable.widget_status_warning
            else -> R.drawable.widget_status_idle
        }
    }

    private fun actionPendingIntent(
        context: Context,
        appWidgetId: Int,
        action: String,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, HotWaterWidgetProvider::class.java).apply {
            this.action = action
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val OPEN_APP_REQUEST_CODE = 9001
}
