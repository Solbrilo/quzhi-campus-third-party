package cn.qzxy.hotwater.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.qzxy.hotwater.AppUpdateCheckResult
import cn.qzxy.hotwater.AppUpdateDownloadProgress
import cn.qzxy.hotwater.AppUpdateInfo
import cn.qzxy.hotwater.AppUpdateInstallResult
import cn.qzxy.hotwater.AppUpdateManager
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
    onOpenAbout: () -> Unit,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
    onOpenAlipayRecharge: () -> Unit,
    onRequestNotificationPermission: ((() -> Unit) -> Unit),
) {
    val tokens = LocalGlassTokens.current
    val environment = LocalGlassEnvironment.current
    val layoutDirection = LocalLayoutDirection.current
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.OPERATIONS) }
    var dockDragging by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showLowBalanceDialog by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateCheckResult by remember { mutableStateOf<AppUpdateCheckResult?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var downloadingUpdate by remember { mutableStateOf(false) }
    var updateDownloadJob by remember { mutableStateOf<Job?>(null) }
    var updateDownloadProgress by remember { mutableStateOf<AppUpdateDownloadProgress?>(null) }
    var installStatusMessage by remember { mutableStateOf<String?>(null) }
    var displayedFeedback by remember { mutableStateOf<FeedbackEvent?>(null) }
    var feedbackVisible by remember { mutableStateOf(false) }
    var lastAnnouncedFeedbackId by rememberSaveable { mutableStateOf<Long?>(null) }
    val updateScope = rememberCoroutineScope()
    val saveableStateHolder = rememberSaveableStateHolder()
    val motionEnabled = environment.motionEnabled
    val haptic = LocalHapticFeedback.current

    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val feedbackEvent by viewModel.feedbackEvent.collectAsStateWithLifecycle()
    val performToggleHaptic: (Boolean) -> Unit = { turningOn ->
        haptic.performGlassHapticFeedback(
            if (turningOn) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
        )
    }

    BackHandler(enabled = currentScreen != AppScreen.OPERATIONS) {
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
                        installStatusMessage = "请允许安装未知应用后，再次点击立即更新。"
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
            .glassHazeSource(zIndex = 1f)
            .imePadding(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            AppHeader(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                overflowExpanded = showOverflowMenu,
                onOverflowExpandedChange = { showOverflowMenu = it },
                onOpenAbout = {
                    showOverflowMenu = false
                    onOpenAbout()
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
                saveableStateHolder.SaveableStateProvider(screen.name) {
                    when (screen) {
                        AppScreen.OPERATIONS -> OperationsPage(
                            snapshot = snapshot,
                            busy = busy,
                            statusMessage = statusMessage,
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
                        )
                        AppScreen.RECORDS -> RecordsPage(history = history)
                        AppScreen.CONFIG -> ConfigPage(
                            config = config,
                            busy = busy,
                            onSave = { viewModel.saveConfig(it) },
                            onExport = onExportConfig,
                            onImport = onImportConfig,
                        )
                    }
                }
            }
        }

        BottomDock(
            labels = listOf("操作", "记录", "配置"),
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
    }
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
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    overflowExpanded: Boolean,
    onOverflowExpandedChange: (Boolean) -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalGlassTokens.current
    val nextThemeMode = themeMode.next()
    val currentThemeLabel = themeMode.accessibilityLabel()
    val nextThemeLabel = nextThemeMode.accessibilityLabel()
    val themeIcon = when (themeMode) {
        ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
        ThemeMode.LIGHT -> Icons.Outlined.LightMode
        ThemeMode.DARK -> Icons.Outlined.DarkMode
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
            Text(
                text = "一键控制你的校园热水",
                color = tokens.textSecondary,
                fontSize = 14.sp,
            )
        }
        Box {
            GlassPressable(
                onClick = { onOverflowExpandedChange(!overflowExpanded) },
                material = GlassMaterial.Control,
                cornerRadius = 24.dp,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(OverflowMenuButtonTag)
                    .semantics {
                        contentDescription = "更多选项"
                        stateDescription = if (overflowExpanded) "菜单已展开" else "菜单已收起"
                    },
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = null,
                    tint = tokens.textPrimary,
                )
            }
            DropdownMenu(
                expanded = overflowExpanded,
                onDismissRequest = { onOverflowExpandedChange(false) },
                modifier = Modifier.testTag(OverflowMenuHostTag),
                shape = RoundedCornerShape(0.dp),
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .width(112.dp)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    GlassPressable(
                        onClick = onOpenAbout,
                        material = GlassMaterial.Control,
                        cornerRadius = 24.dp,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag(OverflowAboutButtonTag)
                            .semantics { contentDescription = "关于" },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = tokens.textPrimary,
                        )
                    }
                    GlassPressable(
                        onClick = { onThemeModeChange(nextThemeMode) },
                        onClickLabel = "切换为${nextThemeLabel}主题",
                        material = GlassMaterial.Control,
                        cornerRadius = 24.dp,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag(OverflowThemeButtonTag)
                            .semantics {
                                contentDescription = "主题模式"
                                stateDescription =
                                    "当前${currentThemeLabel}主题，下一模式为${nextThemeLabel}主题"
                            },
                    ) {
                        Icon(
                            imageVector = themeIcon,
                            contentDescription = null,
                            tint = tokens.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun bottomDockClearance(): Dp {
    val density = LocalDensity.current
    val bottomSafeInset = WindowInsets.safeDrawing.getBottom(density)
    return 76.dp + with(density) { bottomSafeInset.toDp() }
}

private fun ThemeMode.accessibilityLabel(): String = when (this) {
    ThemeMode.SYSTEM -> "系统"
    ThemeMode.LIGHT -> "明亮"
    ThemeMode.DARK -> "暗夜"
}

internal const val OverflowMenuButtonTag = "overflow_menu_button"
internal const val OverflowMenuHostTag = "overflow_menu_host"
internal const val OverflowAboutButtonTag = "overflow_about_button"
internal const val OverflowThemeButtonTag = "overflow_theme_button"

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
