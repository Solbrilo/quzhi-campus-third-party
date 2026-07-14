package cn.qzxy.hotwater.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import cn.qzxy.hotwater.designsystem.motion.rememberMotionEnabled
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.glassConvex
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
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
    onOpenAbout: () -> Unit,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
    onOpenAlipayRecharge: () -> Unit,
    onRequestNotificationPermission: ((() -> Unit) -> Unit),
) {
    val tokens = LocalGlassTokens.current
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.OPERATIONS) }
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
    val hazeState = remember { HazeState() }
    val saveableStateHolder = rememberSaveableStateHolder()
    val motionEnabled = rememberMotionEnabled()
    val haptic = LocalHapticFeedback.current

    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val feedbackEvent by viewModel.feedbackEvent.collectAsStateWithLifecycle()

    BackHandler(enabled = currentScreen != AppScreen.OPERATIONS) {
        currentScreen = AppScreen.OPERATIONS
    }

    LaunchedEffect(feedbackEvent) {
        val event = feedbackEvent ?: return@LaunchedEffect
        if (lastAnnouncedFeedbackId == event.id) return@LaunchedEffect
        lastAnnouncedFeedbackId = event.id
        displayedFeedback = event
        feedbackVisible = true
        haptic.performHapticFeedback(
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
            title = { Text(text = "余额不足5元") },
            text = { Text(text = "余额不足5元，建议通过支付宝进行充值。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLowBalanceDialog = false
                        onOpenAlipayRecharge()
                    },
                ) { Text(text = "前往支付宝充值") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLowBalanceDialog = false
                        onRequestNotificationPermission { viewModel.toggleWhenIdle() }
                    },
                ) { Text(text = "仍要开启") }
            },
        )
    }

    val availableUpdate = updateCheckResult as? AppUpdateCheckResult.UpdateAvailable
    if (downloadingUpdate) {
        AlertDialog(
            onDismissRequest = { updateDownloadJob?.cancel() },
            containerColor = tokens.meshBase,
            titleContentColor = tokens.textPrimary,
            textContentColor = tokens.textSecondary,
            title = { Text(text = "正在下载更新", color = tokens.textPrimary) },
            text = { UpdateDownloadProgressContent(progress = updateDownloadProgress) },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { updateDownloadJob?.cancel() }) {
                    Text(text = "取消下载", color = tokens.textSecondary)
                }
            },
        )
    }

    if (showUpdateDialog && availableUpdate != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            containerColor = tokens.meshBase,
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
                TextButton(
                    onClick = {
                        showUpdateDialog = false
                        installUpdate(availableUpdate.update)
                    },
                ) { Text(text = "立即更新", color = tokens.accentPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text(text = "稍后", color = tokens.textSecondary)
                }
            },
        )
    }

    installStatusMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { installStatusMessage = null },
            containerColor = tokens.meshBase,
            titleContentColor = tokens.textPrimary,
            textContentColor = tokens.textSecondary,
            title = { Text(text = "更新提示", color = tokens.textPrimary) },
            text = { Text(text = message, color = tokens.textSecondary) },
            confirmButton = {
                TextButton(onClick = { installStatusMessage = null }) {
                    Text(text = "知道了", color = tokens.accentPrimary)
                }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            AppHeader(
                overflowExpanded = showOverflowMenu,
                onOverflowExpandedChange = { showOverflowMenu = it },
                onOpenAbout = {
                    showOverflowMenu = false
                    onOpenAbout()
                },
            )

            AnimatedContent(
                targetState = currentScreen,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 18.dp),
                transitionSpec = {
                    val direction = if (targetState.tabIndex > initialState.tabIndex) 1 else -1
                    if (motionEnabled) {
                        slideInHorizontally(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            initialOffsetX = { it * direction / 6 },
                        ) + fadeIn(tween(140)) togetherWith
                            slideOutHorizontally(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
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
                                } else if (snapshot?.hasCachedOrder != true && config.isComplete()) {
                                    onRequestNotificationPermission { viewModel.toggleWhenIdle() }
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
            currentScreen = currentScreen,
            onScreenSelected = { currentScreen = it },
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        )

        AnimatedVisibility(
            visible = feedbackVisible && displayedFeedback != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
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
            .glassConvex(cornerRadius = 18.dp, tokens = tokens)
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
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "关闭提示",
                tint = tokens.textSecondary,
            )
        }
    }
}

@Composable
private fun AppHeader(
    overflowExpanded: Boolean,
    onOverflowExpandedChange: (Boolean) -> Unit,
    onOpenAbout: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
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
            IconButton(onClick = { onOverflowExpandedChange(true) }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "更多",
                    tint = tokens.textPrimary,
                )
            }
            DropdownMenu(
                expanded = overflowExpanded,
                onDismissRequest = { onOverflowExpandedChange(false) },
                modifier = Modifier.glassConvex(cornerRadius = 14.dp, tokens = tokens),
                shape = RoundedCornerShape(14.dp),
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                DropdownMenuItem(
                    text = { Text(text = "关于", color = tokens.textPrimary) },
                    onClick = onOpenAbout,
                    colors = MenuDefaults.itemColors(
                        textColor = tokens.textPrimary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun BottomDock(
    currentScreen: AppScreen,
    onScreenSelected: (AppScreen) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalGlassTokens.current
    val dockShape = RoundedCornerShape(28.dp)
    val lowerMeshColor = lerp(
        tokens.meshSpots[2].color.copy(alpha = 1f),
        tokens.meshSpots[3].color.copy(alpha = 1f),
        0.5f,
    )
    val dockAmbientColor = lerp(tokens.meshBase, lowerMeshColor, 0.16f)
    var tabWidthPx by remember { mutableStateOf(0) }
    val indicatorOffset by animateIntOffsetAsState(
        targetValue = IntOffset(tabWidthPx * currentScreen.tabIndex, 0),
        animationSpec = if (rememberMotionEnabled()) {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        } else {
            snap()
        },
        label = "tab_indicator",
    )

    Box(
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .height(60.dp)
            .shadow(
                elevation = 10.dp,
                shape = dockShape,
                ambientColor = tokens.outerShadow,
                spotColor = tokens.outerShadow,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(dockShape)
                .hazeEffect(state = hazeState) {
                    blurEnabled = true
                    blurRadius = 22.dp
                    noiseFactor = 0.035f
                    backgroundColor = dockAmbientColor
                    tints = listOf(HazeTint(tokens.textPrimary.copy(alpha = 0.025f)))
                    fallbackTint = HazeTint(dockAmbientColor.copy(alpha = 0.94f))
                },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(dockShape)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to tokens.borderHi.copy(alpha = 0.045f),
                            0.45f to Color.Transparent,
                            1f to tokens.textPrimary.copy(alpha = 0.025f),
                        ),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { indicatorOffset }
                    .fillMaxWidth(1f / 3f)
                    .height(48.dp)
                    .onSizeChanged { tabWidthPx = it.width }
                    .padding(horizontal = 3.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(tokens.textPrimary.copy(alpha = 0.07f)),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabButton("操作", currentScreen == AppScreen.OPERATIONS, { onScreenSelected(AppScreen.OPERATIONS) }, Modifier.weight(1f))
                TabButton("记录", currentScreen == AppScreen.RECORDS, { onScreenSelected(AppScreen.RECORDS) }, Modifier.weight(1f))
                TabButton("配置", currentScreen == AppScreen.CONFIG, { onScreenSelected(AppScreen.CONFIG) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun bottomDockClearance(): Dp {
    return 76.dp
}

@Composable
private fun TabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalGlassTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val motionEnabled = rememberMotionEnabled()
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (motionEnabled && isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = if (isPressed) Spring.StiffnessHigh else Spring.StiffnessMedium,
        ),
        label = "tab_button_scale",
    )
    Box(
        modifier = modifier
            .height(48.dp)
            .scale(scale)
            .clip(RoundedCornerShape(22.dp))
            .selectable(
                selected = isSelected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = {
                    if (!isSelected) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onClick()
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (isSelected) tokens.accentPrimary else tokens.textSecondary,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
