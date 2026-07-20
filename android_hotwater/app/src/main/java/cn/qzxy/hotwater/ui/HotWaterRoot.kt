package cn.qzxy.hotwater.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.qzxy.hotwater.AppUpdateManager
import cn.qzxy.hotwater.HotWaterConfig
import cn.qzxy.hotwater.MainViewModel
import cn.qzxy.hotwater.designsystem.theme.LocalGlassEnvironment
import cn.qzxy.hotwater.designsystem.theme.ThemeMode

private enum class RootScreen {
    MAIN,
    LOGIN,
    CONFIG_SCANNER,
    ONBOARDING_QUOTE,
    ONBOARDING_LOGIN,
    ONBOARDING_SCANNER,
    ONBOARDING_COMPLETE,
}

internal enum class OnboardingResumeDestination {
    MAIN,
    SCANNER,
    LOGIN,
}

internal fun resolveOnboardingResumeDestination(
    config: HotWaterConfig,
): OnboardingResumeDestination {
    return when {
        config.isComplete() -> OnboardingResumeDestination.MAIN
        config.projectId != null &&
            config.userId != null &&
            config.accountId != null &&
            config.loginCode.isNotBlank() &&
            config.telephone.isNotBlank() -> OnboardingResumeDestination.SCANNER
        else -> OnboardingResumeDestination.LOGIN
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun HotWaterRoot(
    viewModel: MainViewModel,
    updateManager: AppUpdateManager,
    onboardingRequired: Boolean,
    onOnboardingCompleted: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    glassIntensity: Float,
    onGlassIntensityChange: (Float) -> Unit,
    onGlassIntensityChangeFinished: () -> Unit,
    onResetGlassIntensity: () -> Unit,
    onOpenAbout: () -> Unit,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
    onOpenAlipayRecharge: () -> Unit,
    onRequestNotificationPermission: ((() -> Unit) -> Unit),
) {
    var rootScreen by rememberSaveable(onboardingRequired) {
        mutableStateOf(
            if (onboardingRequired) RootScreen.ONBOARDING_QUOTE else RootScreen.MAIN,
        )
    }
    var pendingConfigSnCode by rememberSaveable { mutableStateOf<String?>(null) }
    var onboardingScanResetKey by rememberSaveable { mutableStateOf(0) }
    val saveableStateHolder = rememberSaveableStateHolder()
    val motionEnabled = LocalGlassEnvironment.current.motionEnabled
    val config by viewModel.config.collectAsStateWithLifecycle()
    val loginState by viewModel.qzxyLoginState.collectAsStateWithLifecycle()
    val snCodeSaveState by viewModel.snCodeSaveState.collectAsStateWithLifecycle()
    val onboardingScanSaving = snCodeSaveState.loading

    LaunchedEffect(onboardingRequired) {
        val onOnboardingScreen = rootScreen == RootScreen.ONBOARDING_QUOTE ||
            rootScreen == RootScreen.ONBOARDING_LOGIN ||
            rootScreen == RootScreen.ONBOARDING_SCANNER ||
            rootScreen == RootScreen.ONBOARDING_COMPLETE
        when {
            onboardingRequired && !onOnboardingScreen -> rootScreen = RootScreen.ONBOARDING_QUOTE
            !onboardingRequired && onOnboardingScreen -> rootScreen = RootScreen.MAIN
        }
    }

    LaunchedEffect(rootScreen, loginState.success) {
        if (!loginState.success) return@LaunchedEffect
        if (rootScreen == RootScreen.ONBOARDING_LOGIN) {
            viewModel.clearSnCodeSaveState()
        }
        rootScreen = when (rootScreen) {
            RootScreen.LOGIN -> RootScreen.MAIN
            RootScreen.ONBOARDING_LOGIN -> RootScreen.ONBOARDING_SCANNER
            else -> return@LaunchedEffect
        }
        viewModel.clearQzxyLoginState()
    }

    LaunchedEffect(
        rootScreen,
        snCodeSaveState.loading,
        snCodeSaveState.success,
        snCodeSaveState.message,
    ) {
        if (rootScreen != RootScreen.ONBOARDING_SCANNER || snCodeSaveState.loading) {
            return@LaunchedEffect
        }
        when {
            snCodeSaveState.success -> {
                viewModel.clearSnCodeSaveState()
                rootScreen = RootScreen.ONBOARDING_COMPLETE
            }
            snCodeSaveState.message.isNotBlank() -> {
                viewModel.clearSnCodeSaveState()
                onboardingScanResetKey++
            }
        }
    }

    BackHandler(
        enabled = rootScreen != RootScreen.MAIN && rootScreen != RootScreen.ONBOARDING_QUOTE,
    ) {
        if (rootScreen == RootScreen.ONBOARDING_SCANNER && onboardingScanSaving) {
            return@BackHandler
        }
        if (rootScreen == RootScreen.ONBOARDING_SCANNER) {
            viewModel.clearSnCodeSaveState()
        }
        val leavingLogin = rootScreen == RootScreen.LOGIN || rootScreen == RootScreen.ONBOARDING_LOGIN
        rootScreen = when (rootScreen) {
            RootScreen.LOGIN, RootScreen.CONFIG_SCANNER -> RootScreen.MAIN
            RootScreen.ONBOARDING_LOGIN -> RootScreen.ONBOARDING_QUOTE
            RootScreen.ONBOARDING_SCANNER -> RootScreen.ONBOARDING_LOGIN
            RootScreen.ONBOARDING_COMPLETE -> RootScreen.ONBOARDING_SCANNER
            else -> rootScreen
        }
        if (leavingLogin) viewModel.cancelQzxyLogin() else viewModel.clearQzxyLoginState()
    }

    AnimatedContent(
        targetState = rootScreen,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (motionEnabled) {
                slideInHorizontally(tween(360)) { it / 10 } + fadeIn(tween(280)) togetherWith
                    slideOutHorizontally(tween(260)) { -it / 12 } + fadeOut(tween(220))
            } else {
                fadeIn(tween(90)) togetherWith fadeOut(tween(70))
            }
        },
        label = "root_flow_transition",
    ) { screen ->
        saveableStateHolder.SaveableStateProvider(screen.name) {
            when (screen) {
                RootScreen.MAIN -> HotWaterApp(
                    viewModel = viewModel,
                    updateManager = updateManager,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    glassIntensity = glassIntensity,
                    onGlassIntensityChange = onGlassIntensityChange,
                    onGlassIntensityChangeFinished = onGlassIntensityChangeFinished,
                    onResetGlassIntensity = onResetGlassIntensity,
                    onOpenAbout = onOpenAbout,
                    onExportConfig = onExportConfig,
                    onImportConfig = onImportConfig,
                    onOpenAlipayRecharge = onOpenAlipayRecharge,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenLogin = {
                        viewModel.clearQzxyLoginState()
                        rootScreen = RootScreen.LOGIN
                    },
                    onScanSnCode = { rootScreen = RootScreen.CONFIG_SCANNER },
                    scannedSnCode = pendingConfigSnCode,
                    onScannedSnCodeConsumed = { pendingConfigSnCode = null },
                )

                RootScreen.LOGIN -> LoginPage(
                    initialTelephone = config.telephone,
                    loginState = loginState,
                    onLogin = viewModel::loginToQzxy,
                    onBack = {
                        viewModel.cancelQzxyLogin()
                        rootScreen = RootScreen.MAIN
                    },
                )

                RootScreen.CONFIG_SCANNER -> QrScannerPage(
                    onBack = { rootScreen = RootScreen.MAIN },
                    onScanned = { snCode ->
                        pendingConfigSnCode = snCode
                        rootScreen = RootScreen.MAIN
                    },
                )

                RootScreen.ONBOARDING_QUOTE -> OnboardingQuotePage(
                    onNext = {
                        when (resolveOnboardingResumeDestination(config)) {
                            OnboardingResumeDestination.MAIN -> {
                                onOnboardingCompleted()
                                rootScreen = RootScreen.MAIN
                            }
                            OnboardingResumeDestination.SCANNER -> {
                                viewModel.clearSnCodeSaveState()
                                rootScreen = RootScreen.ONBOARDING_SCANNER
                            }
                            OnboardingResumeDestination.LOGIN -> {
                                viewModel.clearQzxyLoginState()
                                rootScreen = RootScreen.ONBOARDING_LOGIN
                            }
                        }
                    },
                )

                RootScreen.ONBOARDING_LOGIN -> LoginPage(
                    initialTelephone = config.telephone,
                    loginState = loginState,
                    onLogin = viewModel::loginToQzxy,
                    onBack = {
                        viewModel.cancelQzxyLogin()
                        rootScreen = RootScreen.ONBOARDING_QUOTE
                    },
                    onSkip = {
                        viewModel.cancelQzxyLogin()
                        viewModel.clearSnCodeSaveState()
                        rootScreen = RootScreen.ONBOARDING_SCANNER
                    },
                )

                RootScreen.ONBOARDING_SCANNER -> QrScannerPage(
                    title = "绑定你的热水器",
                    allowManualSnCodeEntry = true,
                    scanResetKey = onboardingScanResetKey,
                    navigationEnabled = !onboardingScanSaving,
                    isProcessing = onboardingScanSaving,
                    onBack = {
                        viewModel.clearSnCodeSaveState()
                        rootScreen = RootScreen.ONBOARDING_LOGIN
                    },
                    onScanned = { snCode ->
                        if (onboardingScanSaving) return@QrScannerPage
                        viewModel.clearSnCodeSaveState()
                        viewModel.saveSnCode(snCode)
                    },
                    onSkip = {
                        viewModel.clearSnCodeSaveState()
                        onOnboardingCompleted()
                        rootScreen = RootScreen.MAIN
                    },
                )

                RootScreen.ONBOARDING_COMPLETE -> OnboardingCompletePage(
                    onStart = {
                        onOnboardingCompleted()
                        rootScreen = RootScreen.MAIN
                    },
                )
            }
        }
    }
}
