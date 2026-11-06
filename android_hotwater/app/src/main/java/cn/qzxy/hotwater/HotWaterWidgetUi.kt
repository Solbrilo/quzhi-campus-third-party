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

    private val lastSwitchContent = mutableMapOf<Int, List<Any>>()

    @Synchronized
    fun updateAllWidgets(context: Context, force: Boolean = false) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val sliderIds = appWidgetManager.getAppWidgetIds(ComponentName(context, HotWaterSwitchWidgetProvider::class.java)).toSet()
        val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, HotWaterWidgetProvider::class.java)).toSet() + sliderIds
        val statePreferences = context.getSharedPreferences("hotwater_widget_presentation", Context.MODE_PRIVATE)
        val store = HotWaterConfigStore(context)
        val opened = !store.loadOrderNo().isNullOrBlank()
        val state = store.loadWidgetState()
        val renderKey = listOf(store.loadStateGeneration(), opened, state,
            WidgetActionPolicy.isBusy(state), store.loadConfig().isComplete(), store.requiresReauthentication())
        appWidgetIds.forEach { appWidgetId ->
            // Workers publish once on completion and again in finally. Preserve the in-flight
            // launcher animation when those updates contain exactly the same state.
            if (appWidgetId in sliderIds && !force && lastSwitchContent[appWidgetId] == renderKey) return@forEach
            val key = "switch_$appWidgetId"
            val animate = statePreferences.contains(key) && statePreferences.getBoolean(key, opened) != opened &&
                android.provider.Settings.Global.getFloat(context.contentResolver,
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
            runCatching {
                appWidgetManager.updateAppWidget(appWidgetId,
                    if (appWidgetId in sliderIds) buildSwitchRemoteViews(context, appWidgetId, animate)
                    else buildRemoteViews(context, appWidgetManager, appWidgetId))
            }.onSuccess {
                statePreferences.edit().putBoolean(key, opened).apply()
                if (appWidgetId in sliderIds) lastSwitchContent[appWidgetId] = renderKey
            }
        }
    }

    internal fun buildSwitchRemoteViews(context: Context, appWidgetId: Int, animate: Boolean = false): RemoteViews {
        val store = HotWaterConfigStore(context)
        val opened = !store.loadOrderNo().isNullOrBlank()
        val state = store.loadWidgetState()
        val busy = WidgetActionPolicy.isBusy(state)
        val ready = store.loadConfig().isComplete() && !store.requiresReauthentication()
        val layout = when {
            opened && animate -> R.layout.widget_hotwater_switch_on_animated
            opened -> R.layout.widget_hotwater_switch_on
            animate -> R.layout.widget_hotwater_switch_off_animated
            else -> R.layout.widget_hotwater_switch_off
        }
        val targetId = if (opened) R.id.widget_switch_left else R.id.widget_switch_right
        val hiddenId = if (opened) R.id.widget_switch_right else R.id.widget_switch_left
        val action = if (opened) ACTION_CLOSE else ACTION_OPEN
        return RemoteViews(context.packageName, layout).apply {
            setDisplayedChild(R.id.widget_switch_slider, 0)
            setTextViewText(R.id.widget_switch_thumb, when {
                !ready -> "待登录"
                !busy && (state.isBusy || state.tone == "error" || state.tone == "warning") -> "待确认"
                opened -> "已开"
                else -> "已关"
            })
            setTextViewText(hiddenId, "")
            setTextViewText(targetId, when {
                !ready -> "登录"
                busy -> "处理中"
                else -> if (opened) "关" else "开"
            })
            setTextViewTextSize(targetId, android.util.TypedValue.COMPLEX_UNIT_SP,
                if (busy || !ready) 14f else 22f)
            setTextViewTextSize(R.id.widget_switch_thumb, android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            setContentDescription(targetId, when {
                !ready -> "打开应用登录并绑定设备"
                busy -> "操作处理中，请稍候"
                else -> if (opened) "关闭热水" else "开启热水"
            })
            setContentDescription(R.id.widget_switch_thumb, when {
                !ready -> "打开应用登录并绑定设备"
                state.isBusy || state.tone == "error" || state.tone == "warning" -> "状态待确认，打开应用查看"
                opened -> "热水已开启"
                else -> "热水已关闭"
            })
            setFloat(R.id.widget_switch_track, "setAlpha", if (busy) 0.65f else 1f)
            setInt(R.id.widget_switch_track, "setBackgroundResource", when {
                !ready -> R.drawable.widget_switch_idle
                opened -> R.drawable.widget_switch_close
                else -> R.drawable.widget_switch_open
            })
            setOnClickPendingIntent(R.id.widget_switch_thumb, openAppPendingIntent(context))
            setOnClickPendingIntent(targetId, if (ready) actionPendingIntent(context, appWidgetId, action,
                widgetRequestCode(appWidgetId, action)) else openAppPendingIntent(context))
        }
    }

    internal fun buildRemoteViews(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        layoutSize: WidgetLayoutSize = widgetLayoutSize(appWidgetManager, appWidgetId),
    ): RemoteViews {
        val store = HotWaterConfigStore(context)
        val config = store.loadConfig()
        val savedState = store.loadWidgetState()
        val widgetState = if (savedState.isBusy && !WidgetActionPolicy.isBusy(savedState)) {
            savedState.copy(title = "请刷新状态", message = "上次操作未完成，请刷新确认。", isBusy = false, tone = "warning")
        } else savedState
        val hasCachedOrder = !store.loadOrderNo().isNullOrBlank()
        val presentation = buildPresentation(
            context = context,
            config = config,
            widgetState = widgetState,
            hasCachedOrder = hasCachedOrder,
            usageSession = store.loadUsageSession(),
        )
        val layoutId = when (layoutSize) {
            WidgetLayoutSize.COMPACT -> R.layout.widget_hotwater_compact
            WidgetLayoutSize.STANDARD -> R.layout.widget_hotwater
            WidgetLayoutSize.EXPANDED -> R.layout.widget_hotwater_expanded
        }
        val openAppIntent = openAppPendingIntent(context)
        // Keep the action reachable: the provider debounces active work and recovers expired work.
        val canSendCommand = config.isComplete()

        return RemoteViews(context.packageName, layoutId).apply {
            setBoolean(R.id.widget_open_button, "setEnabled", true)
            setBoolean(R.id.widget_close_button, "setEnabled", true)
            setFloat(R.id.widget_open_button, "setAlpha", if (presentation.isBusy) 0.5f else 1f)
            setFloat(R.id.widget_close_button, "setAlpha", if (presentation.isBusy) 0.5f else 1f)
            when (layoutSize) {
                WidgetLayoutSize.COMPACT -> {
                    setTextViewText(R.id.widget_compact_state, presentation.status)
                    setTextViewText(R.id.widget_compact_detail, presentation.detail)
                    setInt(
                        R.id.widget_status_dot,
                        "setBackgroundResource",
                        statusIndicatorBackground(presentation),
                    )
                }

                WidgetLayoutSize.STANDARD -> {
                    setTextViewText(R.id.widget_title, "热水控制")
                    setTextViewText(R.id.widget_status_chip, presentation.status)
                    setTextViewText(R.id.widget_state_text, presentation.title)
                    setTextViewText(R.id.widget_detail, presentation.detail)
                    setInt(
                        R.id.widget_status_chip,
                        "setBackgroundResource",
                        chipBackground(presentation),
                    )
                }

                WidgetLayoutSize.EXPANDED -> {
                    setTextViewText(R.id.widget_expanded_status, presentation.status)
                    setTextViewText(R.id.widget_expanded_title, presentation.title)
                    setTextViewText(R.id.widget_expanded_detail, presentation.detail)
                    setTextViewText(R.id.widget_expanded_usage, presentation.nextAction)
                    setTextViewText(R.id.widget_expanded_updated, presentation.meta)
                    setInt(
                        R.id.widget_expanded_status_dot,
                        "setBackgroundResource",
                        statusIndicatorBackground(presentation),
                    )
                    setOnClickPendingIntent(
                        R.id.widget_refresh_button,
                        if (canSendCommand) {
                            actionPendingIntent(
                                context,
                                appWidgetId,
                                ACTION_REFRESH,
                                widgetRequestCode(appWidgetId, ACTION_REFRESH),
                            )
                        } else {
                            openAppIntent
                        },
                    )
                }
            }

            setTextViewText(
                R.id.widget_open_button,
                when {
                    !config.isComplete() -> "去配置"
                    presentation.isBusy -> "处理中"
                    else -> "开启热水"
                },
            )
            setTextViewText(
                R.id.widget_close_button,
                when {
                    !config.isComplete() -> "打开应用"
                    presentation.isBusy -> "处理中"
                    else -> "关闭热水"
                },
            )
            setInt(R.id.widget_open_button, "setBackgroundResource", R.drawable.widget_glass_action_open)
            setInt(R.id.widget_close_button, "setBackgroundResource", R.drawable.widget_glass_action_close)
            setOnClickPendingIntent(R.id.widget_root, openAppIntent)
            setOnClickPendingIntent(
                R.id.widget_open_button,
                if (canSendCommand) {
                    actionPendingIntent(
                        context,
                        appWidgetId,
                        ACTION_OPEN,
                        widgetRequestCode(appWidgetId, ACTION_OPEN),
                    )
                } else {
                    openAppIntent
                },
            )
            setOnClickPendingIntent(
                R.id.widget_close_button,
                if (canSendCommand) {
                    actionPendingIntent(
                        context,
                        appWidgetId,
                        ACTION_CLOSE,
                        widgetRequestCode(appWidgetId, ACTION_CLOSE),
                    )
                } else {
                    openAppIntent
                },
            )
        }
    }

    private fun buildPresentation(
        context: Context,
        config: HotWaterConfig,
        widgetState: HotWaterConfigStore.WidgetState,
        hasCachedOrder: Boolean,
        usageSession: UsageSession?,
    ): WidgetPresentation {
        val deviceBusyByOther = !hasCachedOrder && (widgetState.message.contains("他人正在使用") ||
            widgetState.title.contains("设备正在使用"))
        val status = when {
            !config.isComplete() -> "待配置"
            widgetState.isBusy -> "处理中"
            deviceBusyByOther -> "设备占用"
            hasCachedOrder && widgetState.tone == "error" -> "待确认"
            hasCachedOrder -> "用水中"
            widgetState.tone == "error" -> "需重试"
            else -> "已就绪"
        }
        val title = when {
            !config.isComplete() -> "完成配置后即可控制热水"
            widgetState.isBusy -> "正在同步热水器状态"
            deviceBusyByOther -> "当前设备正在被其他用户使用"
            hasCachedOrder && widgetState.tone == "error" -> "用水状态待确认"
            hasCachedOrder -> "热水正在使用"
            widgetState.tone == "error" -> "上次操作未完成"
            else -> widgetState.title.takeIf { it.isNotBlank() } ?: "热水已准备就绪"
        }
        val detail = widgetState.message
            .lineSequence()
            .map(String::trim)
            .firstOrNull(String::isNotBlank)
            ?: when {
                !config.isComplete() -> "点击卡片打开应用补全配置。"
                hasCachedOrder -> "需要结束用水时，请点击关闭热水。"
                else -> "可从桌面直接开启或关闭热水。"
            }
        val nextAction = when {
            !config.isComplete() -> "下一步：打开应用完成配置"
            widgetState.isBusy -> "当前操作：请稍候"
            hasCachedOrder -> "当前操作：可关闭热水"
            else -> "当前操作：可开启热水"
        }
        val usageText = if (hasCachedOrder) {
            usageSession?.let { "已用水 ${formatUsageDuration(System.currentTimeMillis() - it.startedAtMillis)}" }
                ?: "热水运行中"
        } else {
            null
        }
        val updatedText = widgetState.updatedAtMillis.takeIf { it > 0L }?.let {
            "更新 ${DateFormat.getTimeFormat(context).format(Date(it))}"
        }

        return WidgetPresentation(
            title = title,
            detail = detail,
            status = status,
            nextAction = nextAction,
            meta = listOfNotNull(usageText, updatedText).joinToString(" · ").ifBlank {
                if (config.isComplete()) "点击刷新同步最新状态" else "配置尚未完成"
            },
            tone = widgetState.tone,
            isBusy = widgetState.isBusy,
        )
    }

    private fun widgetLayoutSize(
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ): WidgetLayoutSize {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        return when {
            minWidth >= EXPANDED_MIN_WIDTH_DP && minHeight >= EXPANDED_MIN_HEIGHT_DP -> {
                WidgetLayoutSize.EXPANDED
            }

            minWidth >= STANDARD_MIN_WIDTH_DP && minHeight >= STANDARD_MIN_HEIGHT_DP -> {
                WidgetLayoutSize.STANDARD
            }

            else -> WidgetLayoutSize.COMPACT
        }
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

    private fun chipBackground(presentation: WidgetPresentation): Int {
        return when {
            presentation.isBusy -> R.drawable.widget_status_busy
            presentation.status == "设备占用" -> R.drawable.widget_status_warning
            presentation.tone == "error" -> R.drawable.widget_status_error
            presentation.status == "用水中" || presentation.tone == "success" -> {
                R.drawable.widget_status_success
            }

            presentation.status == "待配置" || presentation.tone == "warning" -> {
                R.drawable.widget_status_warning
            }

            else -> R.drawable.widget_status_idle
        }
    }

    private fun statusIndicatorBackground(presentation: WidgetPresentation): Int {
        return when {
            presentation.isBusy -> R.drawable.widget_status_dot_busy
            presentation.status == "设备占用" || presentation.status == "待配置" ||
                presentation.tone == "warning" -> R.drawable.widget_status_dot_warning
            presentation.tone == "error" -> R.drawable.widget_status_dot_error
            presentation.status == "用水中" || presentation.tone == "success" -> {
                R.drawable.widget_status_dot_success
            }

            else -> R.drawable.widget_status_dot_idle
        }
    }

    private fun formatUsageDuration(durationMillis: Long): String {
        val totalMinutes = (durationMillis.coerceAtLeast(0L) / 60_000L).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            totalMinutes == 0 -> "不足 1 分钟"
            hours == 0 -> "$minutes 分钟"
            minutes == 0 -> "$hours 小时"
            else -> "$hours 小时 $minutes 分钟"
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
    private const val STANDARD_MIN_WIDTH_DP = 180
    private const val STANDARD_MIN_HEIGHT_DP = 150
    private const val EXPANDED_MIN_WIDTH_DP = 250
    private const val EXPANDED_MIN_HEIGHT_DP = 180

    internal enum class WidgetLayoutSize {
        COMPACT,
        STANDARD,
        EXPANDED,
    }

    private data class WidgetPresentation(
        val title: String,
        val detail: String,
        val status: String,
        val nextAction: String,
        val meta: String,
        val tone: String,
        val isBusy: Boolean,
    )

    private fun widgetRequestCode(appWidgetId: Int, action: String): Int {
        return (appWidgetId.toString() + action).hashCode() and 0x7FFFFFFF
    }
}
