package cn.qzxy.hotwater

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
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
