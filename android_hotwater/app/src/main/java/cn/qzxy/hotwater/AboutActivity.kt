package cn.qzxy.hotwater

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.qzxy.hotwater.designsystem.component.GlassPressable
import cn.qzxy.hotwater.designsystem.motion.performGlassHapticFeedback
import cn.qzxy.hotwater.designsystem.theme.GlassIntensityPreferences
import cn.qzxy.hotwater.designsystem.theme.GlassMaterial
import cn.qzxy.hotwater.designsystem.theme.LocalGlassEnvironment
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.GlassTheme
import cn.qzxy.hotwater.designsystem.theme.ThemeModePreferences
import cn.qzxy.hotwater.designsystem.theme.glassHazeSource
import cn.qzxy.hotwater.ui.AboutPage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AboutActivity : ComponentActivity() {
    private lateinit var updateManager: AppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updateManager = AppUpdateManager(applicationContext)
        val themeMode = ThemeModePreferences.read(this)
        val glassIntensity = GlassIntensityPreferences.read(this)
        setContent {
            GlassTheme(
                themeMode = themeMode,
                glassIntensity = glassIntensity,
            ) {
                StandaloneAboutPage(
                    updateManager = updateManager,
                    onFinish = { finish() },
                )
            }
        }
    }
}

@Composable
private fun StandaloneAboutPage(
    updateManager: AppUpdateManager,
    onFinish: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val environment = LocalGlassEnvironment.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val slideDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    val motionEnabled = environment.motionEnabled
    val visibility = remember { MutableTransitionState(false) }
    var closing by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var installJob by remember { mutableStateOf<Job?>(null) }
    var resumeInstallJob by remember { mutableStateOf<Job?>(null) }
    var downloadProgress by remember { mutableStateOf<AppUpdateDownloadProgress?>(null) }
    var checkResult by remember { mutableStateOf<AppUpdateCheckResult?>(null) }
    var installResult by remember { mutableStateOf<AppUpdateInstallResult?>(null) }

    val checkForUpdate: (Boolean) -> Unit = { userInitiated ->
        scope.launch {
            checking = true
            installResult = null
            val result = updateManager.checkForUpdate()
            checkResult = result
            if (userInitiated) {
                haptic.performGlassHapticFeedback(
                    when (result) {
                        is AppUpdateCheckResult.Failed,
                        is AppUpdateCheckResult.InvalidRelease,
                        -> HapticFeedbackType.Reject

                        else -> HapticFeedbackType.Confirm
                    },
                )
            }
            checking = false
        }
    }
    val installUpdate: (AppUpdateInfo) -> Unit = installUpdate@{ update ->
        if (installJob?.isActive == true) return@installUpdate
        installJob = scope.launch {
            installing = true
            downloadProgress = null
            try {
                val result = updateManager.downloadAndInstall(update) { progress ->
                    downloadProgress = progress
                }
                installResult = result
                haptic.performGlassHapticFeedback(
                    when (result) {
                        AppUpdateInstallResult.InstallationLaunched -> HapticFeedbackType.Confirm
                        AppUpdateInstallResult.SourcePermissionRequired -> HapticFeedbackType.SegmentTick
                        is AppUpdateInstallResult.Failed -> HapticFeedbackType.Reject
                    },
                )
            } finally {
                installing = false
                installJob = null
            }
        }
    }
    val closePage: () -> Unit = {
        if (!closing) {
            closing = true
            installJob?.cancel()
            resumeInstallJob?.cancel()
            visibility.targetState = false
        }
    }

    LaunchedEffect(Unit) {
        visibility.targetState = true
        checkForUpdate(false)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (resumeInstallJob?.isActive != true) {
            resumeInstallJob = scope.launch {
                try {
                    installJob?.join()
                    val result = updateManager.resumePendingInstall {
                        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    } ?: return@launch
                    installResult = result
                    haptic.performGlassHapticFeedback(
                        when (result) {
                            AppUpdateInstallResult.InstallationLaunched -> HapticFeedbackType.Confirm
                            AppUpdateInstallResult.SourcePermissionRequired -> HapticFeedbackType.SegmentTick
                            is AppUpdateInstallResult.Failed -> HapticFeedbackType.Reject
                        },
                    )
                } finally {
                    resumeInstallJob = null
                }
            }
        }
    }
    LaunchedEffect(closing, visibility.isIdle, visibility.currentState) {
        if (closing && visibility.isIdle && !visibility.currentState) {
            onFinish()
        }
    }
    BackHandler(onBack = closePage)

    AnimatedVisibility(
        visibleState = visibility,
        modifier = Modifier.fillMaxSize(),
        enter = if (motionEnabled) {
            fadeIn(tween(140)) + slideInHorizontally(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 400f,
                ),
            ) { it * slideDirection / 5 }
        } else {
            fadeIn(tween(80))
        },
        exit = if (motionEnabled) {
            fadeOut(tween(140)) + slideOutHorizontally(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 400f,
                ),
            ) { it * slideDirection / 5 }
        } else {
            fadeOut(tween(70))
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .glassHazeSource(zIndex = 1f)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassPressable(
                    onClick = closePage,
                    material = GlassMaterial.Control,
                    cornerRadius = 16.dp,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = tokens.textPrimary,
                    )
                }
                Text(
                    text = "关于",
                    color = tokens.textPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            AboutPage(
                checkResult = checkResult,
                installResult = installResult,
                checking = checking,
                installing = installing,
                downloadProgress = downloadProgress,
                onCheckForUpdate = { checkForUpdate(true) },
                onInstallUpdate = installUpdate,
                onCancelInstall = { installJob?.cancel() },
            )
        }
    }
}
