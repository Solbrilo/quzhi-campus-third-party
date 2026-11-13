package cn.qzxy.hotwater

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.widget.RemoteViews
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import java.util.Date

object HotWaterWidgetUi {
    const val ACTION_OPEN = "cn.qzxy.hotwater.widget.OPEN"
    const val ACTION_CLOSE = "cn.qzxy.hotwater.widget.CLOSE"
    const val ACTION_REFRESH = "cn.qzxy.hotwater.widget.REFRESH"

    @Synchronized
    fun updateAllWidgets(context: Context, forceIds: Set<Int> = emptySet()) {
        val manager = AppWidgetManager.getInstance(context)
        val sliderIds = manager.getAppWidgetIds(ComponentName(context, HotWaterSwitchWidgetProvider::class.java)).toSet()
        val ids = manager.getAppWidgetIds(ComponentName(context, HotWaterWidgetProvider::class.java)).toSet() + sliderIds
        val prefs = context.getSharedPreferences("hotwater_widget_presentation", Context.MODE_PRIVATE)
        val store = HotWaterConfigStore(context)
        val display = displayState(context, store)
        val dark = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        ids.forEach { id ->
            val force = id in forceIds
            val options = manager.getAppWidgetOptions(id)
            val slider = id in sliderIds
            val sizes = widgetHostSizes(options, slider)
            val layouts = sizes.map { widgetLayoutSize(context, it.width.toInt(), it.height.toInt()) }
            // Both providers may be resized. A 3×1, 2×3, 3×2, 4×4, etc. all use
            // the original information/actions layout, including the switch provider.
            val legacy = layouts.any { it != WidgetLayoutSize.DASHBOARD }
            val layoutKey = "$slider:$layouts:$sizes:$dark:${context.resources.displayMetrics.density}:v8"
            val state = store.loadWidgetState()
            val generation = store.loadStateGeneration()
            val sameLayout = !force && prefs.getString("layout_$id", null) == layoutKey
            val previousOpened = if (sameLayout && prefs.getLong("generation_$id", -1L) == generation && prefs.contains("opened_$id")) prefs.getBoolean("opened_$id", false) else null
            val widgetDisplay = if (display.busy && previousOpened != null) display.copy(opened = previousOpened) else display
            val visibleKey = listOf(generation, widgetDisplay, layoutKey,
                if (legacy) listOf(state.title, state.message, state.tone) else "").toString()
            val key = java.security.MessageDigest.getInstance("SHA-256").digest(visibleKey.toByteArray())
                .joinToString("") { "%02x".format(it) }
            if (!force && prefs.getString("content_$id", null) == key) return@forEach
            val animation = widgetSwitchAnimation(previousOpened, widgetDisplay.opened, state.tone != "error" && !widgetDisplay.busy,
                android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f)
            val variants = sizes.zip(layouts).associate { (size, layout) ->
                size to if (layout == WidgetLayoutSize.DASHBOARD) switchRemoteViews(context, id, widgetDisplay, size.height >= 110f, animation, size)
                    else buildRemoteViews(context, manager, id, layout, size.height.toInt())
            }
            val views = when {
                variants.size == 1 -> variants.values.first()
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> RemoteViews(variants)
                else -> RemoteViews(variants.values.last(), variants.values.first())
            }
            runCatching {
                // A full RemoteViews replaces cached animation actions; the host still
                // reapplies the same native layout. Partial merges would replay an old
                // setDisplayedChild action on every later balance/status refresh.
                if (layouts.all { it != WidgetLayoutSize.DASHBOARD } && sameLayout) manager.partiallyUpdateAppWidget(id, views)
                else manager.updateAppWidget(id, views)
            }.onSuccess {
                prefs.edit().putString("content_$id", key).putString("layout_$id", layoutKey).putBoolean("opened_$id", widgetDisplay.opened).putLong("generation_$id", generation).apply()
            }
        }
    }

    private fun displayState(context: Context, store: HotWaterConfigStore): WidgetDisplayState {
        val state = store.loadWidgetState()
        val opened = !store.loadOrderNo().isNullOrBlank()
        val ready = store.loadConfig().isComplete() && !store.requiresReauthentication()
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        val connected = cm?.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        return WidgetDisplayState(opened, WidgetActionPolicy.isBusy(state), ready, store.loadWidgetBalance(),
            widgetIndicator(ready && connected && store.loadWidgetOnline() && state.tone != "error",
                store.loadWidgetOccupied(), opened))
    }

    internal fun buildSwitchRemoteViews(context: Context, appWidgetId: Int, animate: Boolean = false): RemoteViews =
        switchRemoteViews(context, appWidgetId, displayState(context, HotWaterConfigStore(context)), false,
            if (animate) !HotWaterConfigStore(context).loadOrderNo().isNullOrBlank() else null)

    internal fun switchRemoteViews(context: Context, id: Int, state: WidgetDisplayState,
        dashboard: Boolean, animation: Boolean?, size: SizeF = SizeF(180f, if (dashboard) 180f else 64f)): RemoteViews {
        return RemoteViews(context.packageName, if (dashboard) R.layout.widget_hotwater_dashboard else R.layout.widget_hotwater_slider).apply {
            val status = state.indicator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val trackHeight = minOf((size.width - 16f) * .32f,
                    if (dashboard) size.height * .36f else size.height - 12f).coerceIn(32f, 88f)
                setViewLayoutHeight(R.id.widget_switch_track, trackHeight, android.util.TypedValue.COMPLEX_UNIT_DIP)
                if (!dashboard) setViewLayoutHeight(R.id.widget_slider_shell, trackHeight + 8f, android.util.TypedValue.COMPLEX_UNIT_DIP)
            }
            val led = when (status) {
                DeviceIndicator.IDLE -> R.drawable.widget_led_idle
                DeviceIndicator.OFFLINE -> R.drawable.widget_led_offline
                DeviceIndicator.OCCUPIED -> R.drawable.widget_led_occupied
                DeviceIndicator.USING -> R.drawable.widget_led_using
            }
            setContentDescription(R.id.widget_root, "${status.label}，${if (dashboard) "余额 ${state.balance} 元，" else ""}热水${if (state.opened) "已开" else "已关"}")
            setInt(R.id.widget_switch_track, "setBackgroundResource",
                if (state.opened) R.drawable.widget_switch_track_close else R.drawable.widget_switch_track_open)
            setViewVisibility(R.id.widget_thumb_off, if (state.opened) android.view.View.INVISIBLE else android.view.View.VISIBLE)
            setViewVisibility(R.id.widget_thumb_on, if (state.opened) android.view.View.VISIBLE else android.view.View.INVISIBLE)
            listOf(R.id.widget_led_on_0, R.id.widget_led_on_1, R.id.widget_led_off_0, R.id.widget_led_off_1).forEach {
                setImageViewResource(it, led)
            }
            // Toggle between identical children only on a confirmed position change. The
            // launcher's ViewFlipper runs the translation locally without bitmap refreshes.
            // Prepare only the hidden direction. First inflation/restoration stays static,
            // while the next confirmed change can animate from either side.
            setDisplayedChild(if (state.opened) R.id.widget_thumb_off else R.id.widget_thumb_on, 0)
            if (animation != null) setDisplayedChild(if (state.opened) R.id.widget_thumb_on else R.id.widget_thumb_off, 1)
            val actionText = if (!state.ready) "登录" else if (state.busy) "稍候" else if (state.opened) "关" else "开"
            setTextViewText(R.id.widget_switch_left, if (state.opened) actionText else "")
            setTextViewText(R.id.widget_switch_right, if (state.opened) "" else actionText)
            if (dashboard) {
                setOnClickPendingIntent(R.id.widget_refresh_button, if (state.ready)
                    actionPendingIntent(context, id, ACTION_REFRESH, widgetRequestCode(id, ACTION_REFRESH)) else openAppPendingIntent(context))
                setTextViewText(R.id.widget_balance, "¥ ${state.balance}")
                setTextViewText(R.id.widget_dashboard_status, "● ${status.label}")
                setTextColor(R.id.widget_dashboard_status, status.color)
                setTextViewCompoundDrawables(R.id.widget_dashboard_status, 0, 0, 0, 0)
                setInt(R.id.widget_dashboard_status, "setBackgroundResource", when (status) {
                    DeviceIndicator.IDLE -> R.drawable.widget_status_idle
                    DeviceIndicator.OFFLINE -> R.drawable.widget_status_error
                    DeviceIndicator.OCCUPIED -> R.drawable.widget_status_warning
                    DeviceIndicator.USING -> R.drawable.widget_status_using
                })
            }
            val target = if (state.opened) R.id.widget_switch_left else R.id.widget_switch_right
            val thumb = if (state.opened) R.id.widget_switch_right else R.id.widget_switch_left
            val action = if (state.opened) ACTION_CLOSE else ACTION_OPEN
            setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
            setOnClickPendingIntent(thumb, openAppPendingIntent(context))
            setContentDescription(thumb, "${status.label}，打开应用")
            setContentDescription(target, if (!state.ready) "登录并绑定设备" else if (state.opened) "关闭热水" else "开启热水")
            setOnClickPendingIntent(target, if (state.ready) actionPendingIntent(context, id, action, widgetRequestCode(id, action)) else openAppPendingIntent(context))
        }
    }

    internal fun buildRemoteViews(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        layoutSize: WidgetLayoutSize = widgetLayoutSize(context, appWidgetManager, appWidgetId),
        availableHeightDp: Int = appWidgetManager.getAppWidgetOptions(appWidgetId)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 240),
    ): RemoteViews {
        val store = HotWaterConfigStore(context)
        if (layoutSize == WidgetLayoutSize.DASHBOARD) return switchRemoteViews(context, appWidgetId,
            displayState(context, store), true, null)
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
            WidgetLayoutSize.DASHBOARD -> error("Dashboard is rendered above")
            WidgetLayoutSize.COMPACT -> R.layout.widget_hotwater_compact
            WidgetLayoutSize.COMPACT_WIDE -> R.layout.widget_hotwater_compact_wide
            WidgetLayoutSize.STANDARD -> R.layout.widget_hotwater
            WidgetLayoutSize.EXPANDED -> R.layout.widget_hotwater_expanded
            WidgetLayoutSize.TALL -> R.layout.widget_hotwater_tall
        }
        val openAppIntent = openAppPendingIntent(context)
        // Keep the action reachable: the provider debounces active work and recovers expired work.
        val canSendCommand = config.isComplete()

        return RemoteViews(context.packageName, layoutId).apply {
            setTextViewText(R.id.widget_classic_balance, "¥ ${store.loadWidgetBalance()}")
            setBoolean(R.id.widget_open_button, "setEnabled", true)
            setBoolean(R.id.widget_close_button, "setEnabled", true)
            setFloat(R.id.widget_open_button, "setAlpha", if (presentation.isBusy) 0.5f else 1f)
            setFloat(R.id.widget_close_button, "setAlpha", if (presentation.isBusy) 0.5f else 1f)
            when (layoutSize) {
                WidgetLayoutSize.DASHBOARD -> Unit
                WidgetLayoutSize.COMPACT, WidgetLayoutSize.COMPACT_WIDE -> {
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

                WidgetLayoutSize.EXPANDED, WidgetLayoutSize.TALL -> {
                    setTextViewText(R.id.widget_expanded_status, presentation.status)
                    setTextViewText(R.id.widget_expanded_title, presentation.title)
                    setTextViewText(R.id.widget_expanded_detail, presentation.detail)
                    setTextViewText(R.id.widget_expanded_usage, presentation.nextAction)
                    setTextViewText(R.id.widget_expanded_updated, presentation.meta)
                    if (layoutSize == WidgetLayoutSize.TALL) {
                        // Keep the shared upper status/lower actions arrangement on 3×2;
                        // compact secondary copy before it can overlap the buttons.
                        setInt(R.id.widget_expanded_title, "setMaxLines", if (availableHeightDp < 200) 1 else 2)
                        setViewVisibility(R.id.widget_expanded_title, if (availableHeightDp < 200) android.view.View.GONE else android.view.View.VISIBLE)
                        setViewVisibility(R.id.widget_classic_balance_label, if (availableHeightDp < 165) android.view.View.GONE else android.view.View.VISIBLE)
                        setViewVisibility(R.id.widget_expanded_detail, if (availableHeightDp < 240) android.view.View.GONE else android.view.View.VISIBLE)
                        setTextViewTextSize(R.id.widget_classic_balance, android.util.TypedValue.COMPLEX_UNIT_SP, if (availableHeightDp < 180) 20f else 28f)
                        val extraVisibility = if (availableHeightDp < 260) android.view.View.GONE else android.view.View.VISIBLE
                        setViewVisibility(R.id.widget_expanded_usage, extraVisibility)
                        setViewVisibility(R.id.widget_expanded_updated, extraVisibility)
                    }
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
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ): WidgetLayoutSize {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        return widgetLayoutSize(context, minWidth, minHeight)
    }

    internal fun widgetLayoutSize(context: Context, minWidth: Int, minHeight: Int): WidgetLayoutSize = when {
            usesTallWidget(minWidth.toFloat(), minHeight.toFloat()) -> WidgetLayoutSize.TALL
            usesWideTwoRowWidget(minWidth.toFloat(), minHeight.toFloat()) -> WidgetLayoutSize.TALL
            usesDashboardWidget(minWidth.toFloat(), minHeight.toFloat(), context.resources.configuration.smallestScreenWidthDp) -> WidgetLayoutSize.DASHBOARD
            minWidth >= 220 && minHeight < STANDARD_MIN_HEIGHT_DP -> WidgetLayoutSize.COMPACT_WIDE
            minHeight >= STANDARD_MIN_HEIGHT_DP -> WidgetLayoutSize.TALL

            else -> WidgetLayoutSize.COMPACT
        }

    @Suppress("DEPRECATION")
    internal fun widgetHostSizes(options: Bundle, slider: Boolean): List<SizeF> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val exact = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                ?.filter { it.width.isFinite() && it.height.isFinite() && it.width > 0 && it.height > 0 }
                ?.distinct()?.take(16)
            if (!exact.isNullOrEmpty()) return exact
        }
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180).coerceAtLeast(1)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, if (slider) 64 else 180).coerceAtLeast(1)
        val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWidth).coerceAtLeast(minWidth)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight).coerceAtLeast(minHeight)
        // Minimum width and minimum height can describe different orientations.
        return listOf(SizeF(minWidth.toFloat(), maxHeight.toFloat()), SizeF(maxWidth.toFloat(), minHeight.toFloat())).distinct()
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
        DASHBOARD,
        COMPACT,
        COMPACT_WIDE,
        STANDARD,
        EXPANDED,
        TALL,
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
