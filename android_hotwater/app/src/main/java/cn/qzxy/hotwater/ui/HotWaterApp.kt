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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import cn.qzxy.hotwater.designsystem.theme.GlassPageSurface
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
import androidx.compose.material.icons.outlined.NotificationsNone
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
import cn.qzxy.hotwater.accountRequestKey
import cn.qzxy.hotwater.hasAccount
import cn.qzxy.hotwater.messageAccountKey
import cn.qzxy.hotwater.MainViewModel
import cn.qzxy.hotwater.designsystem.component.GlassPressable
import cn.qzxy.hotwater.designsystem.motion.performGlassHapticFeedback
import cn.qzxy.hotwater.designsystem.theme.GlassMaterial
import cn.qzxy.hotwater.designsystem.theme.LocalGlassEnvironment
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.ThemePalette
import cn.qzxy.hotwater.designsystem.theme.ThemeMode
import cn.qzxy.hotwater.designsystem.theme.glassHazeSource
import cn.qzxy.hotwater.designsystem.theme.glassMaterial
import cn.qzxy.hotwater.ui.navigation.BottomDock
import cn.qzxy.hotwater.ui.navigation.rememberMainTabNavigation
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class AppScreen(val tabIndex: Int) {
    OPERATIONS(0),
    RECORDS(1),
    SETTINGS(2),
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun HotWaterApp(
    viewModel: MainViewModel,
    updateManager: AppUpdateManager,
    themePalette: ThemePalette = ThemePalette.SKY,
    onThemePaletteChange: (ThemePalette) -> Unit = {},
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    glassIntensity: Float,
    onGlassIntensityChange: (Float) -> Unit,
    onGlassIntensityChangeFinished: () -> Unit,
    onResetGlassIntensity: () -> Unit,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
    openConfigRequest: Int = 0,
    onPayWithAlipay: (Long) -> Unit,
    onRequestNotificationPermission: ((() -> Unit) -> Unit),
    onClearAllUserData: () -> Unit,
    onOpenLogin: () -> Unit,
    onScanSnCode: () -> Unit,
    onAddDeviceByQr: () -> Unit,
    scannedSnCode: String?,
    onScannedSnCodeConsumed: () -> Unit,
    automaticPromptsEnabled: Boolean = true,
) {
    val tokens = LocalGlassTokens.current
    val environment = LocalGlassEnvironment.current
    // A config page can be kept in the saveable state holder while another tab
    // is visible. Bump this key when all data is cleared so stale form values
    // cannot be restored when the config page is opened again.
    var configStateGeneration by rememberSaveable { mutableStateOf(0) }
    val mainPager = rememberPagerState { AppScreen.entries.size }
    val tabNavigation = rememberMainTabNavigation(mainPager)
    val currentScreen = AppScreen.entries[mainPager.settledPage]
    var moreDestination by rememberSaveable { mutableStateOf<MorePageDestination?>(null) }
    var renderedMoreDestination by rememberSaveable {
        mutableStateOf(MorePageDestination.OVERVIEW)
    }
    var showConfig by rememberSaveable { mutableStateOf(false) }
    var configRequestPending by remember(openConfigRequest) {
        mutableStateOf(openConfigRequest > 0)
    }
    var showMessages by rememberSaveable { mutableStateOf(false) }
    var selectedMessageRevision by rememberSaveable { mutableStateOf<String?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var accountEditor by remember { mutableStateOf<Boolean?>(null) }
    var showLowBalanceDialog by remember { mutableStateOf(false) }
    var showDevicePicker by rememberSaveable { mutableStateOf(false) }
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
            configRequestPending = true
            tabNavigation.select(AppScreen.SETTINGS.tabIndex, animated = false)
            showConfig = true
            configRequestPending = false
        }
    }

    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val billsState by viewModel.serverBills.collectAsStateWithLifecycle()
    val campusMessages by viewModel.serverMessages.collectAsStateWithLifecycle()
    val systemMessages by viewModel.systemAnnouncements.collectAsStateWithLifecycle()
    val announcementResponse by viewModel.announcementResponse.collectAsStateWithLifecycle()
    val messages = campusMessages.copy(
        messages = (campusMessages.messages + systemMessages.messages).sortedByDescending { it.publishedAt },
        read = campusMessages.read + systemMessages.read,
        loading = campusMessages.loading || systemMessages.loading,
        error = listOfNotNull(campusMessages.error, systemMessages.error).joinToString(" · ").ifBlank { null },
        announcement = systemMessages.announcement ?: campusMessages.announcement,
    )
    val credentialsExpired by viewModel.credentialExpired.collectAsStateWithLifecycle()
    val recharge by viewModel.rechargeState.collectAsStateWithLifecycle()
    val profile by viewModel.accountProfile.collectAsStateWithLifecycle()
    val accountActionState by viewModel.accountAction.collectAsStateWithLifecycle()
    LaunchedEffect(config.accountRequestKey(), currentScreen) {
        if (currentScreen == AppScreen.SETTINGS) viewModel.loadAccountProfile()
    }
    LaunchedEffect(config.accountRequestKey()) { accountEditor = null }
    accountEditor?.let { changingPhone ->
        AccountSheet(changingPhone, config.telephone, accountActionState,
            onDismiss = { accountEditor = null }, onSendCode = viewModel::sendAccountCode,
            onSubmit = viewModel::updateAccount)
    }
    LaunchedEffect(recharge.paymentRequestId) {
        recharge.paymentRequestId?.let(onPayWithAlipay)
    }
    if (recharge.visible) {
        RechargeSheet(
            state = recharge, balance = recharge.balanceYuan ?: snapshot?.balanceYuan,
            refreshingBalance = recharge.refreshingBalance,
            onDismiss = viewModel::closeRecharge, onRetry = viewModel::reloadRecharge,
            onSelect = viewModel::selectRechargePackage, onSubmit = viewModel::submitRecharge,
            onRefreshBalance = viewModel::refreshRechargeBalance,
            onLogin = { viewModel.closeRecharge(); onOpenLogin() },
        )
    }
    val onAccountAction: () -> Unit = {
        if (config.hasAccount() && !credentialsExpired) showLogoutDialog = true else onOpenLogin()
    }
    LaunchedEffect(messageAccountKey(config)) { selectedMessageRevision = null }
    val feedbackEvent by viewModel.feedbackEvent.collectAsStateWithLifecycle()
    val networkValidated = rememberValidatedNetworkState()
    val serverReachable by cn.qzxy.hotwater.QzxyConnectionState.reachable.collectAsStateWithLifecycle()
    LaunchedEffect(networkValidated) {
        if (!networkValidated) cn.qzxy.hotwater.QzxyConnectionState.disconnected()
        else if (config.isComplete() && !credentialsExpired) viewModel.refreshBalance(announce = false, recordHistory = false)
    }
    val connectionStatus = resolveAppConnectionStatus(
        networkValidated = networkValidated,
        serverReachable = serverReachable && snapshot?.isDeviceOnline != false,
        deviceBusyByOther = snapshot?.isDeviceBusyByOther == true,
        usingHotWater = snapshot?.hasCachedOrder == true && snapshot?.usageStartedAtMillis != null,
    )
    val performToggleHaptic: (Boolean) -> Unit = { turningOn ->
        haptic.performGlassHapticFeedback(
            if (turningOn) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
        )
    }

    BackHandler(enabled = moreDestination != null && !showConfig && !showMessages) {
        moreDestination = null
    }

    BackHandler(
        enabled = moreDestination == null && !showConfig && !showMessages && currentScreen != AppScreen.OPERATIONS,
    ) {
        tabNavigation.select(AppScreen.OPERATIONS.tabIndex, motionEnabled)
    }

    BackHandler(enabled = showConfig && !showMessages) { showConfig = false }
    BackHandler(enabled = showMessages) {
        if (selectedMessageRevision != null) selectedMessageRevision = null else showMessages = false
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

    val checkForUpdate: (Boolean) -> Unit = { manual ->
        updateScope.launch {
            checkingUpdate = true
            val result = updateManager.checkForUpdate()
            updateCheckResult = result
            if (result is AppUpdateCheckResult.UpdateAvailable && updateManager.shouldPrompt(result.update, manual)) {
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

    LaunchedEffect(updateManager, automaticPromptsEnabled) {
        if (automaticPromptsEnabled) checkForUpdate(false)
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
                    text = "去充值",
                    onClick = {
                        showLowBalanceDialog = false
                        viewModel.openRecharge()
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

    if (showDevicePicker) {
        NearbyDevicePickerDialog(
            viewModel = viewModel,
            currentSnCode = config.snCode,
            onDismiss = { showDevicePicker = false },
            onScanQrCode = onAddDeviceByQr,
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    GlassDialogAction(text = "忽略此版本", onClick = {
                        updateManager.ignoreVersion(availableUpdate.update.versionName)
                        showUpdateDialog = false
                    })
                    GlassDialogAction(text = "立即更新", onClick = {
                        showUpdateDialog = false
                        installUpdate(availableUpdate.update)
                    }, emphasized = true)
                }
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
                onOpenMessages = {
                    selectedMessageRevision = null
                    showMessages = true
                    viewModel.dismissMessageAnnouncement()
                    viewModel.loadMessages()
                },
                hasUnreadMessages = messages.hasUnread,
                modifier = Modifier
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                        ),
                    )
                    .padding(start = 20.dp, top = 18.dp, end = 20.dp),
            )

            HorizontalPager(state = mainPager,
                flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(mainPager,
                    pagerSnapDistance = androidx.compose.foundation.pager.PagerSnapDistance.atMost(2)),
                userScrollEnabled = !showConfig && !showMessages && moreDestination == null,
                beyondViewportPageCount = 1,
                modifier = Modifier.weight(1f).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(top = 18.dp),
            ) { index ->
                val screen = AppScreen.entries[index]
                ScrollViewport(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                val stateKey = if (screen == AppScreen.SETTINGS) {
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
                                if (credentialsExpired || !config.hasAccount()) {
                                    onOpenLogin()
                                } else if (viewModel.shouldShowLowBalanceOpenWarning()) {
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
                            onOpenRecharge = viewModel::openRecharge,
                            onOpenNearbyDevices = {
                                viewModel.clearNearbyPairState()
                                showDevicePicker = true
                            },
                        )
                        AppScreen.RECORDS -> RecordsPage(
                            state = billsState,
                            accountKey = config.accountRequestKey(),
                            loggedIn = config.hasAccount(),
                            credentialsExpired = credentialsExpired,
                            onLogin = onOpenLogin,
                            active = currentScreen == AppScreen.RECORDS &&
                                !credentialsExpired && !configRequestPending,
                            onLoad = viewModel::loadServerBills,
                        )
                        AppScreen.SETTINGS -> MorePage(
                            destination = MorePageDestination.OVERVIEW,
                            motionEnabled = motionEnabled,
                            checkResult = updateCheckResult, checking = checkingUpdate,
                            installing = downloadingUpdate, releaseNotes = releaseNotes,
                            releaseNotesLoading = releaseNotesLoading, releaseNotesError = releaseNotesError,
                            themePalette = themePalette, onThemePaletteChange = onThemePaletteChange,
                            themeMode = themeMode, onThemeModeChange = onThemeModeChange,
                            glassIntensity = glassIntensity, onGlassIntensityChange = onGlassIntensityChange,
                            onGlassIntensityChangeFinished = onGlassIntensityChangeFinished,
                            onResetGlassIntensity = onResetGlassIntensity,
                            onCheckForUpdate = { checkForUpdate(true) }, onLoadReleaseNotes = loadReleaseNotes,
                            onNavigate = { renderedMoreDestination = it; moreDestination = it },
                            onClearAllUserData = {
                                saveableStateHolder.removeState("config:$configStateGeneration")
                                configStateGeneration++
                                showConfig = false
                                onClearAllUserData()
                            },
                            onOpenExternal = { url -> runCatching { uriHandler.openUri(url) } },
                            onBack = { tabNavigation.select(AppScreen.OPERATIONS.tabIndex, motionEnabled) },
                            embedded = true,
                            loggedIn = config.hasAccount() && !credentialsExpired,
                            accountBusy = busy,
                            onAccountAction = onAccountAction,
                            onOpenConfig = { showConfig = true },
                            accountName = profile.name.ifBlank { cn.qzxy.hotwater.HotWaterConfig.maskMiddle(config.telephone, 3, 4) },
                            schoolName = profile.school,
                            onAccountSettings = { viewModel.resetAccountAction(); accountEditor = it },
                        )
                    }
                }
            }
        }

        }
        BottomDock(
            labels = listOf("用水", "账单", "设置"),
            selectedIndex = mainPager.currentPage,
            pagePosition = { mainPager.currentPage + mainPager.currentPageOffsetFraction },
            onSelected = { index, _ -> tabNavigation.select(index, motionEnabled) },
            onDragStateChanged = {},
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
            enter = subpageEnter(motionEnabled),
            exit = subpageExit(motionEnabled),
            label = "more_page_visibility",
        ) {
            val destination = moreDestination ?: renderedMoreDestination
            GlassPageSurface(solidBackground = destination == MorePageDestination.THEME) {
            MorePage(
                destination = destination,
                motionEnabled = motionEnabled,
                checkResult = updateCheckResult,
                checking = checkingUpdate,
                installing = downloadingUpdate,
                releaseNotes = releaseNotes,
                releaseNotesLoading = releaseNotesLoading,
                releaseNotesError = releaseNotesError,
                themePalette = themePalette, onThemePaletteChange = onThemePaletteChange,
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
                    saveableStateHolder.removeState("config:$configStateGeneration")
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
                onBack = { moreDestination = null },
                modifier = Modifier
                    .fillMaxSize()

                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            )
        }
        }
        AnimatedVisibility(visible = showConfig, enter = subpageEnter(motionEnabled),
            exit = subpageExit(motionEnabled), label = "config_page_visibility") {
            GlassPageSurface {
            Column(Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                SubpageHeader("配置", onBack = { showConfig = false })
                saveableStateHolder.SaveableStateProvider("config:$configStateGeneration") {
                    ConfigPage(config = config, busy = busy, onSave = viewModel::saveConfig,
                        onExport = onExportConfig, onImport = onImportConfig,
                        onScanSnCode = onScanSnCode, scannedSnCode = scannedSnCode,
                        onScannedSnCodeConsumed = onScannedSnCodeConsumed)
                }
            }
        }
        }
        AnimatedVisibility(
            visible = showMessages,
            enter = subpageEnter(motionEnabled),
            exit = subpageExit(motionEnabled),
            label = "messages_page_visibility",
        ) {
            GlassPageSurface(solidBackground = true) {
            MessagesPage(state = messages, selectedRevision = selectedMessageRevision,
                responseState = announcementResponse, onRespond = viewModel::respondToAnnouncement,
                onMarkAllRead = viewModel::markAllMessagesRead,
                onSelect = { viewModel.markMessageRead(it); selectedMessageRevision = it.revision },
                onRefresh = { viewModel.loadMessages() }, onLogin = onOpenLogin,
                onBack = {
                    if (selectedMessageRevision != null) selectedMessageRevision = null else showMessages = false
                },
                modifier = Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp))
        }
        }
        if (showLogoutDialog) {
            AlertDialog(onDismissRequest = { showLogoutDialog = false },
                modifier = Modifier.glassMaterial(GlassMaterial.Overlay, 28.dp),
                containerColor = Color.Transparent, tonalElevation = 0.dp,
                title = { Text("退出登录？", color = tokens.textPrimary) },
                text = { Text("将移除本机账号和设备配置，显示设置会保留。用水中请先关水。", color = tokens.textSecondary) },
                confirmButton = { GlassDialogAction("退出登录", {
                    showLogoutDialog = false
                    saveableStateHolder.removeState("config:$configStateGeneration")
                    configStateGeneration++
                    viewModel.logout()
                }, emphasized = true) },
                dismissButton = { GlassDialogAction("取消", { showLogoutDialog = false }) })
        }
        messages.announcement?.takeIf { automaticPromptsEnabled && !showMessages && !showLogoutDialog && !showUpdateDialog && !recharge.visible && accountEditor == null }?.let { notice ->
            if (notice.systemNotice != null) {
                SystemAnnouncementDialog(notice, announcementResponse, viewModel::respondToAnnouncement,
                    onDismiss = viewModel::dismissMessageAnnouncement, onDetails = {
                        viewModel.markMessageRead(notice); selectedMessageRevision = notice.revision; showMessages = true
                    })
            } else {
            AlertDialog(onDismissRequest = viewModel::dismissMessageAnnouncement,
                modifier = Modifier.glassMaterial(GlassMaterial.Overlay, 28.dp),
                containerColor = Color.Transparent, tonalElevation = 0.dp,
                title = { Text("新消息", color = tokens.textPrimary) },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(notice.title, color = tokens.textPrimary, fontWeight = FontWeight.Bold)
                    Text(notice.publishedAt, color = tokens.textSecondary)
                } },
                confirmButton = { GlassDialogAction("查看详情", {
                    viewModel.markMessageRead(notice)
                    selectedMessageRevision = notice.revision
                    showMessages = true
                }, emphasized = true) },
                dismissButton = { GlassDialogAction("稍后查看", viewModel::dismissMessageAnnouncement) })
            }
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
    connectionStatus: AppConnectionStatus = AppConnectionStatus.OFFLINE,
    onOpenMessages: () -> Unit,
    hasUnreadMessages: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalGlassTokens.current
    val connectionStatusColor = connectionStatus.indicatorColor()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = currentTimeGreeting(),
                color = tokens.textPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.background(connectionStatusColor.copy(alpha = .13f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp).semantics(mergeDescendants = true) {
                    contentDescription = connectionStatus.label
                    stateDescription = connectionStatus.accessibilityDescription
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
                    text = connectionStatus.label,
                    color = connectionStatusColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        Box {
        GlassPressable(
            onClick = onOpenMessages,
            material = GlassMaterial.Control,
            cornerRadius = 24.dp,
            modifier = Modifier
                .size(48.dp)
                .testTag(MessageBellButtonTag)
                .semantics { contentDescription = if (hasUnreadMessages) "消息，有未读消息" else "消息" },
        ) {
            Icon(
                imageVector = Icons.Outlined.NotificationsNone,
                contentDescription = null,
                tint = tokens.textPrimary,
            )
        }
        if (hasUnreadMessages) Box(Modifier.align(Alignment.TopEnd).padding(3.dp)
            .size(9.dp).clip(CircleShape).background(tokens.accentError))
        }
    }
}

@Composable
internal fun bottomDockClearance(): Dp {
    val density = LocalDensity.current
    val bottomSafeInset = WindowInsets.safeDrawing.getBottom(density)
    return 76.dp + with(density) { bottomSafeInset.toDp() }
}

internal const val MessageBellButtonTag = "message_bell_button"
internal const val OverflowMenuButtonTag = MessageBellButtonTag

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
