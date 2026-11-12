package cn.qzxy.hotwater

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import cn.qzxy.hotwater.designsystem.theme.CustomThemePreferences
import cn.qzxy.hotwater.designsystem.theme.CustomThemeSettings
import cn.qzxy.hotwater.designsystem.theme.extractImageBackground
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.qzxy.hotwater.designsystem.theme.GlassTheme
import cn.qzxy.hotwater.designsystem.theme.ThemeMode
import cn.qzxy.hotwater.designsystem.theme.ThemePalette
import cn.qzxy.hotwater.designsystem.theme.glassHazeSource
import cn.qzxy.hotwater.ui.*
import cn.qzxy.hotwater.ui.navigation.BottomDock
import java.time.YearMonth

/** Offline documentation fixture. Only included in Debug; never loads accounts or calls a server. */
class DocumentationPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent.getStringExtra("screen") ?: "home"
        if (screen == "custom" || screen == "widgets" || intent.getBooleanExtra("showcaseBackground", false)) {
            val bitmap = assets.open("showcase_background.jpg").use { android.graphics.BitmapFactory.decodeStream(it) }
            CustomThemePreferences.imageFile(this).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
            CustomThemePreferences.write(this, CustomThemeSettings(background = extractImageBackground(bitmap), accent = 0xFF5795D2,
                image = true, imageRevision = 1, imageColorRevision = 1, animation = "sky"))
            bitmap.recycle()
        }
        if (screen == "widgets") {
            val idle = WidgetDisplayState(false, false, true, "26.80", DeviceIndicator.IDLE)
            listOf("slider" to false, "dashboard" to true).forEach { (name, dashboard) ->
                val artwork = renderNativeWidget(this, idle, 600, if (dashboard) 600 else 205, dashboard)
                java.io.File(cacheDir, "widget-$name.png").outputStream().use {
                    artwork.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                artwork.recycle()
            }
        }
        val palette = ThemePalette.entries.firstOrNull { it.storageValue == intent.getStringExtra("palette") }
            ?: ThemePalette.TIDE
        val mode = if (intent.getBooleanExtra("dark", false)) ThemeMode.DARK else ThemeMode.LIGHT
        setContent {
            var selectedPalette by remember { mutableStateOf(palette) }
            GlassTheme(palette = selectedPalette, themeMode = mode, glassIntensity = 0.65f) {
                DocumentationScreen(screen, selectedPalette, mode) { selectedPalette = it }
            }
        }
    }
}

@Composable
private fun DocumentationScreen(screen: String, palette: ThemePalette, mode: ThemeMode, onPalette: (ThemePalette) -> Unit) {
    var previewFeatures by remember { mutableStateOf(screen == "update") }
    if (screen == "widgets") {
        val previewContext = androidx.compose.ui.platform.LocalContext.current
        val idle = remember { WidgetDisplayState(false, false, true, "26.80", DeviceIndicator.IDLE) }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(40.dp))
            androidx.compose.material3.Text("桌面小组件", color = cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens.current.textPrimary,
                fontSize = androidx.compose.ui.unit.TextUnit.Unspecified)
            Image(remember { renderNativeWidget(previewContext, idle, 600, 205, false).asImageBitmap() }, "2×1 滑动开关",
                Modifier.fillMaxWidth().aspectRatio(600f / 205f))
            Image(remember { renderNativeWidget(previewContext, idle.copy(opened = true, indicator = DeviceIndicator.USING), 500, 500, true).asImageBitmap() }, "2×2 余额与开关",
                Modifier.size(250.dp))
        }
        return
    }
    if (screen == "widget_tall") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.ui.viewinterop.AndroidView(modifier = Modifier.width(160.dp).height(240.dp), factory = { source ->
                val fixture = object : android.content.ContextWrapper(source) {
                    override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences =
                        super.getSharedPreferences("widget_documentation_" + name, mode)
                }
                HotWaterWidgetUi.buildRemoteViews(fixture, android.appwidget.AppWidgetManager.getInstance(fixture), 0,
                    HotWaterWidgetUi.WidgetLayoutSize.TALL).apply(fixture, android.widget.FrameLayout(fixture))
            })
        }
        return
    }
    if (screen == "welcome") {
        OnboardingQuotePage(onNext = { previewFeatures = true }, isUpgrade = true)
        if (previewFeatures) UpdateHighlightsSheet { previewFeatures = false }
        return
    }
    var destination by remember { mutableStateOf(when (screen) {
        "theme" -> MorePageDestination.THEME
        "settings" -> MorePageDestination.OVERVIEW
        else -> MorePageDestination.OPEN_SOURCE
    }) }
    val snapshot = DashboardSnapshot(
        balanceYuan = 26.80, balanceLabel = "26.80", userLabel = "演示账号",
        deviceName = "301 宿舍热水器", locationLabel = "示例校区 · 3 号楼",
        statusLabel = "热水已关闭", orderNo = null, hasCachedOrder = false, isDeviceOnline = true,
    )
    Box(Modifier.fillMaxSize()) {
        if (screen in listOf("opensource", "settings", "theme")) {
            cn.qzxy.hotwater.designsystem.theme.GlassPageSurface(solidBackground = destination == MorePageDestination.THEME) {
            MorePage(
                destination = destination, motionEnabled = true,
                checkResult = null, checking = false, installing = false,
                releaseNotes = null, releaseNotesLoading = false, releaseNotesError = null,
                themePalette = palette, onThemePaletteChange = onPalette, themeMode = mode, onThemeModeChange = {},
                glassIntensity = 0.65f, onGlassIntensityChange = {}, onGlassIntensityChangeFinished = {},
                onResetGlassIntensity = {}, onCheckForUpdate = {}, onLoadReleaseNotes = {},
                onNavigate = { destination = it }, onClearAllUserData = {}, onOpenExternal = {}, onBack = { destination = MorePageDestination.OVERVIEW },
                loggedIn = true, accountName = "校园用户", schoolName = "示例大学",
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp),
            )
            }
        } else {
            Column(Modifier.fillMaxSize().glassHazeSource(zIndex = 1f)) {
                AppHeader(connectionStatus = AppConnectionStatus.CONNECTED, onOpenMessages = {},
                    hasUnreadMessages = true,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(start = 20.dp, top = 18.dp, end = 20.dp))
                Box(Modifier.weight(1f).padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
                    if (screen == "bills") {
                        val month = YearMonth.now()
                        val amounts = listOf(820L, 670L, 1080L, 540L, 960L, 610L)
                        val bills = amounts.mapIndexed { index, amount ->
                            ServerBill("demo-$index", "demo-$index", null,
                                "$month-${(6-index).toString().padStart(2, '0')} 21:10:00", amount,
                                "301 宿舍热水器", null, month, waterMilliLiters = amount * 37)
                        }
                        RecordsPage(ServerBillsState(BillQuery(month.year, month.monthValue), bills, loaded = true),
                            accountKey = "documentation-demo", onLoad = { _, _ -> })
                    } else {
                        OperationsPage(snapshot, busy = false, onToggle = {}, onRefreshBalance = {},
                            onOpenRecharge = {}, onOpenNearbyDevices = {})
                    }
                }
            }
            BottomDock(labels = listOf("用水", "账单", "设置"), selectedIndex = if (screen == "bills") 1 else 0,
                onSelected = { _, _ -> }, onDragStateChanged = {},
                modifier = Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(start = 20.dp, end = 20.dp, bottom = 12.dp))
        }
        if (screen == "update" && previewFeatures) UpdateHighlightsSheet { previewFeatures = false }
        if (screen == "recharge") {
            RechargeSheet(
                state = RechargeState(visible = true,
                    packages = listOf(10, 20, 30, 50, 80, 100).map { RechargePackage(it.toLong(), it.toString(), null) },
                    selectedId = 20),
                balance = 26.80, refreshingBalance = false, onDismiss = {}, onRetry = {}, onSelect = {},
                onSubmit = {}, onRefreshBalance = {}, onLogin = {},
            )
        }
    }
}

private fun renderNativeWidget(context: android.content.Context, state: WidgetDisplayState, width: Int, height: Int, dashboard: Boolean): android.graphics.Bitmap {
    val view = HotWaterWidgetUi.switchRemoteViews(context, 0, state, dashboard, null).apply(context, android.widget.FrameLayout(context))
    view.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
        android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY))
    view.layout(0, 0, width, height)
    return android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888).also {
        view.draw(android.graphics.Canvas(it))
    }
}
