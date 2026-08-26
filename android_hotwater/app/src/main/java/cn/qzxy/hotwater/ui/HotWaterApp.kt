package cn.qzxy.hotwater.ui

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.qzxy.hotwater.AppUpdateCheckResult
import cn.qzxy.hotwater.AppUpdateDownloadProgress
import cn.qzxy.hotwater.AppUpdateInfo
import cn.qzxy.hotwater.AppUpdateInstallResult
import cn.qzxy.hotwater.AppUpdateManager
import cn.qzxy.hotwater.AppReleaseNote
import cn.qzxy.hotwater.AppReleaseNotesResult
import cn.qzxy.hotwater.FeedbackEvent
import cn.qzxy.hotwater.MainViewModel
import cn.qzxy.hotwater.designsystem.component.GlassPressable
import cn.qzxy.hotwater.designsystem.motion.performGlassHapticFeedback
import cn.qzxy.hotwater.designsystem.theme.GlassMaterial
import cn.qzxy.hotwater.designsystem.theme.LocalGlassEnvironment
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.ThemeMode
import cn.qzxy.hotwater.designsystem.theme.glassHazeSource
import cn.qzxy.hotwater.designsystem.theme.glassMaterial
import cn.qzxy.hotwater.ui.navigation.BottomDock
import cn.qzxy.hotwater.ui.navigation.DockSelectionSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class AppScreen(val tabIndex: Int) {
    OPERATIONS(0),
    RECORDS(1),
    CONFIG(2),
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun HotWaterApp(
    viewModel: MainViewModel,
    updateManager: AppUpdateManager,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    glassIntensity: Float,
    onGlassIntensityChange: (Float) -> Unit,
    onGlassIntensityChangeFinished: () -> Unit,
    onResetGlassIntensity: () -> Unit,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
    openConfigRequest: Int = 0,
    onOpenAlipayRecharge: () -> Unit,
    onRequestNotificationPermission: ((() -> Unit) -> Unit),
    onClearAllUserData: () -> Unit,
    onOpenLogin: () -> Unit,
    onScanSnCode: () -> Unit,
    scannedSnCode: String?,
    onScannedSnCodeConsumed: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val environment = LocalGlassEnvironment.current
    val layoutDirection = LocalLayoutDirection.current
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.OPERATIONS) }
    // A config page can be kept in the saveable state holder while another tab
    // is visible. Bump this key when all data is cleared so stale form values
    // cannot be restored when the config page is opened again.
    var configStateGeneration by rememberSaveable { mutableStateOf(0) }
    var dockDragging by remember { mutableStateOf(false) }
    var moreDestination by rememberSaveable { mutableStateOf<MorePageDestination?>(null) }
    var renderedMoreDestination by rememberSaveable {
        mutableStateOf(MorePageDestination.OVERVIEW)
    }
    var showLowBalanceDialog by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateCheckResult by remember { mutableStateOf<AppUpdateCheckResult?>(null) }
    var releaseNotes by remember { mutableStateOf<List<AppReleaseNote>?>(null) }
    var releaseNotesLoading by remember { mutableStateOf(false) }
    var releaseNotesError by remember { mutableStateOf<String?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var downloadingUpdate by remember { mutableStateOf(false) }
    var updateDownloadJob by remember { mutableStateOf<Job?>(null) }
    var resumeUpdateInstallJob by remember { mutableStateOf<Job?>(null) }
    var updateDownloadProgress by remember { mutableStateOf<AppUpdateDownloadProgress?>(null) }
    var installStatusMessage by remember { mutableStateOf<String?>(null) }
    var displayedFeedback by remember { mutableStateOf<FeedbackEvent?>(null) }
    var feedbackVisible by remember { mutableStateOf(false) }
    var lastAnnouncedFeedbackId by rememberSaveable { mutableStateOf<Long?>(null) }
    val updateScope = rememberCoroutineScope()
    val saveableStateHolder = rememberSaveableStateHolder()
    val motionEnabled = environment.motionEnabled
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(openConfigRequest) {
        if (openConfigRequest > 0) {
            currentScreen = AppScreen.CONFIG
        }
    }

    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val feedbackEvent by viewModel.feedbackEvent.collectAsStateWithLifecycle()
    val networkValidated = rememberValidatedNetworkState()
    val connectionStatus = resolveAppConnectionStatus(
        networkValidated = networkValidated,
        deviceOnline = snapshot?.isDeviceOnline,
    )
    val performToggleHaptic: (Boolean) -> Unit = { turningOn ->
        haptic.performGlassHapticFeedback(
            if (turningOn) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
        )
    }

    BackHandler(enabled = moreDestination != null) {
        moreDestination = when (moreDestination) {
            MorePageDestination.OVERVIEW -> null
            null -> null
            else -> MorePageDestination.OVERVIEW.also {
                renderedMoreDestination = MorePageDestination.OVERVIEW
            }
        }
    }

    BackHandler(
        enabled = moreDestination == null && currentScreen != AppScreen.OPERATIONS,
    ) {
        dockDragging = false
        currentScreen = AppScreen.OPERATIONS
    }

    LaunchedEffect(feedbackEvent) {
        val event = feedbackEvent ?: return@LaunchedEffect
        if (lastAnnouncedFeedbackId == event.id) return@LaunchedEffect
        lastAnnouncedFeedbackId = event.id
        displayedFeedback = event
        feedbackVisible = true
        haptic.performGlassHapticFeedback(
            when (event.type) {
                FeedbackEvent.FeedbackType.SUCCESS -> HapticFeedbackType.Confirm
                FeedbackEvent.FeedbackType.ERROR -> HapticFeedbackType.Reject
                FeedbackEvent.FeedbackType.WARNING -> HapticFeedbackType.SegmentTick
            }
        )
        delay(3_200L)
        feedbackVisible = false
        delay(if (motionEnabled) 180L else 80L)
        viewModel.clearFeedbackEvent()
    }

    val checkForUpdate: (Boolean) -> Unit = { notifyWhenAvailable ->
        updateScope.launch {
            checkingUpdate = true
            val result = updateManager.checkForUpdate()
            updateCheckResult = result
            if (notifyWhenAvailable && result is AppUpdateCheckResult.UpdateAvailable) {
                showUpdateDialog = true
            }
            checkingUpdate = false
        }
    }
    val installUpdate: (AppUpdateInfo) -> Unit = installUpdate@{ update ->
        if (updateDownloadJob?.isActive == true) return@installUpdate
        updateDownloadJob = updateScope.launch {
            downloadingUpdate = true
            updateDownloadProgress = null
            try {
                when (val result = updateManager.downloadAndInstall(update) { progress ->
                    updateDownloadProgress = progress
                }) {
                    AppUpdateInstallResult.InstallationLaunched -> Unit
                    AppUpdateInstallResult.SourcePermissionRequired -> {
                        installStatusMessage = "请允许安装未知应用，返回后将自动继续安装。"
                    }
                    is AppUpdateInstallResult.Failed -> {
                        installStatusMessage = result.message
                    }
                }
            } finally {
                downloadingUpdate = false
                updateDownloadJob = null
            }
        }
    }
    val loadReleaseNotes: () -> Unit = {
        if (!releaseNotesLoading && (releaseNotes == null || releaseNotesError != null)) {
            updateScope.launch {
                releaseNotesLoading = true
                releaseNotesError = null
                try {
                    when (val result = updateManager.fetchReleaseNotes()) {
                        is AppReleaseNotesResult.Success -> releaseNotes = result.releases
                        is AppReleaseNotesResult.Failed -> releaseNotesError = result.message
                    }
                } finally {
                    releaseNotesLoading = false
                }
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (resumeUpdateInstallJob?.isActive != true) {
            resumeUpdateInstallJob = updateScope.launch {
                try {
                    updateDownloadJob?.join()
                    val result = updateManager.resumePendingInstall {
                        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    }
                    when (result) {
                        null -> Unit
                        AppUpdateInstallResult.InstallationLaunched -> {
                            installStatusMessage = null
                            haptic.performGlassHapticFeedback(HapticFeedbackType.Confirm)
                        }
                        AppUpdateInstallResult.SourcePermissionRequired -> {
                            installStatusMessage = "尚未获得安装权限；授权后返回应用将自动继续安装。"
                        }
                        is AppUpdateInstallResult.Failed -> {
                            installStatusMessage = result.message
                            haptic.performGlassHapticFeedback(HapticFeedbackType.Reject)
                        }
                    }
                } finally {
                    resumeUpdateInstallJob = null
                }
            }
        }
    }

    LaunchedEffect(updateManager) {
        checkForUpdate(true)
    }

    if (showLowBalanceDialog) {
        AlertDialog(
            onDismissRequest = { showLowBalanceDialog = false },
            modifier = Modifier.glassMaterial(GlassMaterial.Overlay, 28.dp),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            titleContentColor = tokens.textPrimary,
            textContentColor = tokens.textSecondary,
            title = { Text(text = "余额不足5元", color = tokens.textPrimary) },
            text = { Text(text = "余额不足5元，建议通过支付宝进行充值。", color = tokens.textSecondary) },
            confirmButton = {
                GlassDialogAction(
                    text = "前往支付宝充值",
                    onClick = {
                        showLowBalanceDialog = false
                        onOpenAlipayRecharge()
                    },
                    emphasized = true,
                )
            },
            dismissButton = {
                GlassDialogAction(
                    text = "仍要开启",
                    onClick = {
                        showLowBalanceDialog = false
                        onRequestNotificationPermission {
                            performToggleHaptic(true)
                            viewModel.toggleWhenIdle()
                        }
                    },
                    hapticFeedback = { null },
                )
            },
        )
    }

    val availableUpdate = updateCheckResult as? AppUpdateCheckResult.UpdateAvailable
    if (downloadingUpdate) {
        AlertDialog(
            onDismissRequest = { updateDownloadJob?.cancel() },
            modifier = Modifier.glassMaterial(GlassMaterial.Overlay, 28.dp),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            titleContentColor = tokens.textPrimary,
            textContentColor = tokens.textSecondary,
            title = { Text(text = "正在下载更新", color = tokens.textPrimary) },
            text = { UpdateDownloadProgressContent(progress = updateDownloadProgress) },
            confirmButton = {},
            dismissButton = {
                GlassDialogAction(
                    text = "取消下载",
                    onClick = { updateDownloadJob?.cancel() },
                )
            },
        )
    }

    if (showUpdateDialog && availableUpdate != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            modifier = Modifier.glassMaterial(GlassMaterial.Overlay, 28.dp),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            titleContentColor = tokens.textPrimary,
            textContentColor = tokens.textSecondary,
            title = {
                Text(
                    text = "发现新版本 v${availableUpdate.update.versionName}",
                    color = tokens.textPrimary,
                )
            },
            text = {
                Text(
                    text = availableUpdate.update.releaseNotes.ifBlank {
                        "已有新版本可用，建议立即更新。"
                    },
                    color = tokens.textSecondary,
                )
            },
            confirmButton = {
                GlassDialogAction(
                    text = "立即更新",
                    onClick = {
                        showUpdateDialog = false
                        installUpdate(availableUpdate.update)
                    },
                    emphasized = true,
                )
            },
            dismissButton = {
                GlassDialogAction(
                    text = "稍后",
                    onClick = { showUpdateDialog = false },
                )
            },
        )
    }

    installStatusMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { installStatusMessage = null },
            modifier = Modifier.glassMaterial(GlassMaterial.Overlay, 28.dp),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            titleContentColor = tokens.textPrimary,
            textContentColor = tokens.textSecondary,
            title = { Text(text = "更新提示", color = tokens.textPrimary) },
            text = { Text(text = message, color = tokens.textSecondary) },
            confirmButton = {
                GlassDialogAction(
                    text = "知道了",
                    onClick = { installStatusMessage = null },
                    emphasized = true,
                )
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .glassHazeSource(zIndex = 1f),
        ) {
            AppHeader(
                connectionStatus = connectionStatus,
                onOpenMore = {
                    renderedMoreDestination = MorePageDestination.OVERVIEW
                    moreDestination = MorePageDestination.OVERVIEW
                },
                modifier = Modifier
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                        ),
                    )
                    .padding(start = 20.dp, top = 18.dp, end = 20.dp),
            )

            AnimatedContent(
                targetState = currentScreen,
                modifier = Modifier
                    .weight(1f)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                    )
                    .padding(start = 20.dp, top = 18.dp, end = 20.dp),
                transitionSpec = {
                    val logicalDirection = if (targetState.tabIndex > initialState.tabIndex) 1 else -1
                    val direction = if (layoutDirection == LayoutDirection.Rtl) {
                        -logicalDirection
                    } else {
                        logicalDirection
                    }
                    if (dockDragging) {
                        fadeIn(tween(90)) togetherWith fadeOut(tween(90))
                    } else if (motionEnabled) {
                        slideInHorizontally(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = 400f,
                            ),
                            initialOffsetX = { it * direction / 6 },
                        ) + fadeIn(tween(140)) togetherWith
                            slideOutHorizontally(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = 400f,
                                ),
                                targetOffsetX = { -it * direction / 6 },
                            ) + fadeOut(tween(140))
                    } else {
                        fadeIn(tween(90)) togetherWith fadeOut(tween(70))
                    }
                },
                label = "screen_transition",
            ) { screen ->
                val stateKey = if (screen == AppScreen.CONFIG) {
                    "${screen.name}:$configStateGeneration"
                } else {
                    screen.name
                }
                saveableStateHolder.SaveableStateProvider(stateKey) {
                    when (screen) {
                        AppScreen.OPERATIONS -> OperationsPage(
                            snapshot = snapshot,
                            busy = busy,
                            onToggle = {
                                if (viewModel.shouldShowLowBalanceOpenWarning()) {
                                    showLowBalanceDialog = true
                                } else if (snapshot?.hasCachedOrder == true) {
                                    performToggleHaptic(false)
                                    viewModel.toggle()
                                } else if (config.isComplete()) {
                                    onRequestNotificationPermission {
                                        performToggleHaptic(true)
                                        viewModel.toggleWhenIdle()
                                    }
                                } else {
                                    viewModel.toggle()
                                }
                            },
                            onRefreshBalance = { viewModel.refreshBalance() },
                            onOpenRecharge = onOpenAlipayRecharge,
                        )
                        AppScreen.RECORDS -> RecordsPage(history = history)
                        AppScreen.CONFIG -> ConfigPage(
                            config = config,
                            busy = busy,
                            onSave = { viewModel.saveConfig(it) },
                            onExport = onExportConfig,
                            onImport = onImportConfig,
                            onOpenLogin = onOpenLogin,
                            onScanSnCode = onScanSnCode,
                            scannedSnCode = scannedSnCode,
                            onScannedSnCodeConsumed = onScannedSnCodeConsumed,
                        )
                    }
                }
            }
        }

        BottomDock(
            labels = listOf("用水", "账单", "配置"),
            selectedIndex = currentScreen.tabIndex,
            onSelected = { index, source ->
                val nextScreen = AppScreen.entries[index]
                if (nextScreen != currentScreen) {
                    dockDragging = source == DockSelectionSource.Drag
                    currentScreen = nextScreen
                }
            },
            onDragStateChanged = { dockDragging = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                    ),
                )
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        )

        AnimatedVisibility(
            visible = feedbackVisible && displayedFeedback != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                    ),
                )
                .padding(start = 20.dp, end = 20.dp, bottom = 82.dp),
            enter = if (motionEnabled) {
                fadeIn(tween(120)) + slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                    initialOffsetY = { it / 3 },
                )
            } else {
                fadeIn(tween(80))
            },
            exit = if (motionEnabled) {
                fadeOut(tween(140)) + slideOutVertically(tween(160)) { it / 3 }
            } else {
                fadeOut(tween(70))
            },
        ) {
            displayedFeedback?.let { event ->
                FeedbackBanner(
                    event = event,
                    onDismiss = {
                        feedbackVisible = false
                        viewModel.clearFeedbackEvent()
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = moreDestination != null,
            enter = if (motionEnabled) {
                slideInHorizontally(tween(280)) { width -> width / 6 } + fadeIn(tween(190))
            } else {
                fadeIn(tween(90))
            },
            exit = if (motionEnabled) {
                slideOutHorizontally(tween(220)) { width -> width / 8 } + fadeOut(tween(160))
            } else {
                fadeOut(tween(70))
            },
            label = "more_page_visibility",
        ) {
            val destination = moreDestination ?: renderedMoreDestination
            MorePage(
                destination = destination,
                motionEnabled = motionEnabled,
                checkResult = updateCheckResult,
                checking = checkingUpdate,
                installing = downloadingUpdate,
                releaseNotes = releaseNotes,
                releaseNotesLoading = releaseNotesLoading,
                releaseNotesError = releaseNotesError,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                glassIntensity = glassIntensity,
                onGlassIntensityChange = onGlassIntensityChange,
                onGlassIntensityChangeFinished = onGlassIntensityChangeFinished,
                onResetGlassIntensity = onResetGlassIntensity,
                onCheckForUpdate = { checkForUpdate(true) },
                onLoadReleaseNotes = loadReleaseNotes,
                onNavigate = { nextDestination ->
                    renderedMoreDestination = nextDestination
                    moreDestination = nextDestination
                },
                onClearAllUserData = {
                    // Drop the previous registry as well as changing its key.
                    // This prevents cleared form values from remaining in the
                    // nested SaveableStateHolder until the next recreation.
                    saveableStateHolder.removeState("${AppScreen.CONFIG.name}:$configStateGeneration")
                    configStateGeneration++
                    moreDestination = null
                    renderedMoreDestination = MorePageDestination.OVERVIEW
                    onClearAllUserData()
                },
                onOpenExternal = { url ->
                    runCatching { uriHandler.openUri(url) }
                        .onFailure {
                            viewModel.reportFeedback(
                                FeedbackEvent.FeedbackType.ERROR,
                                "无法打开链接，请检查浏览器后重试。",
                            )
                        }
                },
                onBack = {
                    moreDestination = if (destination == MorePageDestination.OVERVIEW) {
                        null
                    } else {
                        MorePageDestination.OVERVIEW.also {
                            renderedMoreDestination = MorePageDestination.OVERVIEW
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(tokens.meshBase)
                    .glassHazeSource(zIndex = 3f)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            )
        }
    }
}

@Composable
private fun rememberValidatedNetworkState(): Boolean {
    val context = LocalContext.current
    val connectivityManager = remember(context) {
        context.applicationContext.getSystemService(ConnectivityManager::class.java)
    }
    var networkValidated by remember(connectivityManager) {
        mutableStateOf(connectivityManager?.isDefaultNetworkValidated() == true)
    }

    DisposableEffect(connectivityManager) {
        if (connectivityManager == null) {
            networkValidated = false
            onDispose { }
        } else {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    networkValidated = connectivityManager.isDefaultNetworkValidated()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    networkValidated = if (network == connectivityManager.activeNetwork) {
                        networkCapabilities.hasValidatedInternet()
                    } else {
                        connectivityManager.isDefaultNetworkValidated()
                    }
                }

                override fun onLost(network: Network) {
                    networkValidated = connectivityManager.isDefaultNetworkValidated()
                }

                override fun onUnavailable() {
                    networkValidated = false
                }
            }
            var registered = false
            try {
                connectivityManager.registerDefaultNetworkCallback(
                    callback,
                    Handler(Looper.getMainLooper()),
                )
                registered = true
            } catch (_: SecurityException) {
                networkValidated = false
            }

            onDispose {
                if (registered) {
                    runCatching { connectivityManager.unregisterNetworkCallback(callback) }
                }
            }
        }
    }

    return networkValidated
}

private fun ConnectivityManager.isDefaultNetworkValidated(): Boolean {
    val network = activeNetwork ?: return false
    return getNetworkCapabilities(network)?.hasValidatedInternet() == true
}

private fun NetworkCapabilities.hasValidatedInternet(): Boolean {
    return hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

@Composable
private fun FeedbackBanner(
    event: FeedbackEvent,
    onDismiss: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val (icon, accent) = when (event.type) {
        FeedbackEvent.FeedbackType.SUCCESS -> Icons.Outlined.CheckCircle to tokens.accentSuccess
        FeedbackEvent.FeedbackType.ERROR -> Icons.Outlined.ErrorOutline to tokens.accentError
        FeedbackEvent.FeedbackType.WARNING -> Icons.Outlined.WarningAmber to tokens.accentWarning
    }

    Row(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .glassMaterial(
                material = GlassMaterial.Floating,
                cornerRadius = 18.dp,
            )
            .semantics {
                liveRegion = if (event.type == FeedbackEvent.FeedbackType.ERROR) {
                    LiveRegionMode.Assertive
                } else {
                    LiveRegionMode.Polite
                }
                stateDescription = event.message
            }
            .padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = event.message,
            color = tokens.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        GlassPressable(
            onClick = onDismiss,
            material = GlassMaterial.EmbeddedControl,
            cornerRadius = 16.dp,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "关闭提示",
                tint = tokens.textSecondary,
            )
        }
    }
}

@Composable
internal fun AppHeader(
    connectionStatus: AppConnectionStatus = AppConnectionStatus.UNKNOWN,
    onOpenMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalGlassTokens.current
    val connectionStatusColor = when (connectionStatus) {
        AppConnectionStatus.USER_OFFLINE -> tokens.accentError
        AppConnectionStatus.DEVICE_OFFLINE -> tokens.accentWarning
        AppConnectionStatus.DEVICE_ONLINE -> tokens.accentSuccess
        AppConnectionStatus.UNKNOWN -> Color.Transparent
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "趣智校园热水",
                color = tokens.textPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (connectionStatus != AppConnectionStatus.UNKNOWN) {
                Row(
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = connectionStatus.label.orEmpty()
                        stateDescription = connectionStatus.accessibilityDescription.orEmpty()
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(connectionStatusColor),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = connectionStatus.label.orEmpty(),
                        color = connectionStatusColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = "一键控制你的校园热水",
                color = tokens.textSecondary,
                fontSize = 14.sp,
            )
        }
        GlassPressable(
            onClick = onOpenMore,
            material = GlassMaterial.Control,
            cornerRadius = 24.dp,
            modifier = Modifier
                .size(48.dp)
                .testTag(OverflowMenuButtonTag)
                .semantics { contentDescription = "打开更多页面" },
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = null,
                tint = tokens.textPrimary,
            )
        }
    }
}

@Composable
internal fun bottomDockClearance(): Dp {
    val density = LocalDensity.current
    val bottomSafeInset = WindowInsets.safeDrawing.getBottom(density)
    return 76.dp + with(density) { bottomSafeInset.toDp() }
}

internal const val OverflowMenuButtonTag = "overflow_menu_button"

@Composable
private fun GlassDialogAction(
    text: String,
    onClick: () -> Unit,
    emphasized: Boolean = false,
    hapticFeedback: (() -> HapticFeedbackType?)? = {
        HapticFeedbackType.SegmentTick
    },
) {
    val tokens = LocalGlassTokens.current
    GlassPressable(
        onClick = onClick,
        material = GlassMaterial.EmbeddedControl,
        cornerRadius = 16.dp,
        hapticFeedback = hapticFeedback,
        modifier = Modifier.height(48.dp),
    ) {
        Text(
            text = text,
            color = if (emphasized) tokens.accentPrimary else tokens.textSecondary,
            fontSize = 14.sp,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
    }
}
