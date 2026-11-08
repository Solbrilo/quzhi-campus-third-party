package cn.qzxy.hotwater.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
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
import cn.qzxy.hotwater.designsystem.theme.ThemePalette
import cn.qzxy.hotwater.designsystem.theme.ThemeMode

private enum class RootScreen(val depth: Int) {
    MAIN(0),
    LOGIN(1),
    CONFIG_SCANNER(1),
    DEVICE_SCANNER(1),
    ONBOARDING_QUOTE(0),
    ONBOARDING_LOGIN(1),
    ONBOARDING_SCANNER(3),
    ONBOARDING_NEARBY(2),
    ONBOARDING_COMPLETE(4),
}

internal enum class OnboardingResumeDestination {
    MAIN,
    NEARBY,
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
            config.telephone.isNotBlank() -> OnboardingResumeDestination.NEARBY
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
    onPayWithAlipay: (Long) -> Unit,
    onRequestNotificationPermission: ((() -> Unit) -> Unit),
    onClearAllUserData: () -> Unit,
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
    val smsCodeState by viewModel.smsCodeState.collectAsStateWithLifecycle()
    val onboardingScanSaving = snCodeSaveState.loading

    LaunchedEffect(onboardingRequired) {
        val onOnboardingScreen = rootScreen == RootScreen.ONBOARDING_QUOTE ||
            rootScreen == RootScreen.ONBOARDING_LOGIN ||
            rootScreen == RootScreen.ONBOARDING_SCANNER ||
            rootScreen == RootScreen.ONBOARDING_NEARBY ||
            rootScreen == RootScreen.ONBOARDING_COMPLETE
        when {
            onboardingRequired && !onOnboardingScreen -> {
                pendingConfigSnCode = null
                onboardingScanResetKey = 0
                rootScreen = RootScreen.ONBOARDING_QUOTE
            }
            !onboardingRequired && onOnboardingScreen -> rootScreen = RootScreen.MAIN
        }
    }

    LaunchedEffect(rootScreen, loginState.success) {
        if (!loginState.success) return@LaunchedEffect
        if (rootScreen == RootScreen.ONBOARDING_LOGIN) {
            viewModel.clearSnCodeSaveState()
            viewModel.clearNearbyPairState()
        }
        rootScreen = when (rootScreen) {
            RootScreen.LOGIN -> RootScreen.MAIN
            RootScreen.ONBOARDING_LOGIN -> RootScreen.ONBOARDING_NEARBY
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
        if (rootScreen !in listOf(RootScreen.ONBOARDING_SCANNER, RootScreen.DEVICE_SCANNER) || snCodeSaveState.loading) {
            return@LaunchedEffect
        }
        when {
            snCodeSaveState.success -> {
                viewModel.clearSnCodeSaveState()
                rootScreen = if (rootScreen == RootScreen.DEVICE_SCANNER) RootScreen.MAIN else RootScreen.ONBOARDING_COMPLETE
            }
            snCodeSaveState.message.isNotBlank() -> onboardingScanResetKey++
        }
    }

    BackHandler(
        enabled = rootScreen != RootScreen.MAIN && rootScreen != RootScreen.ONBOARDING_QUOTE,
    ) {
        if (rootScreen in listOf(RootScreen.ONBOARDING_SCANNER, RootScreen.DEVICE_SCANNER) && onboardingScanSaving) {
            return@BackHandler
        }
        if (rootScreen == RootScreen.ONBOARDING_SCANNER) {
            viewModel.clearSnCodeSaveState()
        }
        if (rootScreen == RootScreen.ONBOARDING_NEARBY) viewModel.cancelNearbyPairing()
        val leavingLogin = rootScreen == RootScreen.LOGIN || rootScreen == RootScreen.ONBOARDING_LOGIN
        rootScreen = when (rootScreen) {
            RootScreen.LOGIN, RootScreen.CONFIG_SCANNER, RootScreen.DEVICE_SCANNER -> RootScreen.MAIN
            RootScreen.ONBOARDING_LOGIN -> RootScreen.ONBOARDING_QUOTE
            RootScreen.ONBOARDING_SCANNER -> RootScreen.ONBOARDING_NEARBY
            RootScreen.ONBOARDING_NEARBY -> RootScreen.ONBOARDING_LOGIN
            RootScreen.ONBOARDING_COMPLETE -> RootScreen.ONBOARDING_NEARBY
            else -> rootScreen
        }
        if (leavingLogin) viewModel.cancelQzxyLogin() else viewModel.clearQzxyLoginState()
    }

    AnimatedContent(
        targetState = rootScreen,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            subpageTransition(
                forward = targetState != RootScreen.MAIN &&
                    (initialState == RootScreen.MAIN || targetState.depth > initialState.depth),
                motionEnabled = motionEnabled,
            )
        },
        label = "root_flow_transition",
    ) { screen ->
        saveableStateHolder.SaveableStateProvider(screen.name) {
            when (screen) {
                RootScreen.MAIN -> HotWaterApp(
                    viewModel = viewModel,
                    updateManager = updateManager,
                    themePalette = themePalette, onThemePaletteChange = onThemePaletteChange,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    glassIntensity = glassIntensity,
                    onGlassIntensityChange = onGlassIntensityChange,
                    onGlassIntensityChangeFinished = onGlassIntensityChangeFinished,
                    onResetGlassIntensity = onResetGlassIntensity,
                    onExportConfig = onExportConfig,
                    onImportConfig = onImportConfig,
                    onPayWithAlipay = onPayWithAlipay,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onClearAllUserData = onClearAllUserData,
                    onOpenLogin = {
                        viewModel.clearQzxyLoginState()
                        rootScreen = RootScreen.LOGIN
                    },
                    onScanSnCode = { rootScreen = RootScreen.CONFIG_SCANNER },
                    onAddDeviceByQr = {
                        viewModel.clearSnCodeSaveState()
                        rootScreen = RootScreen.DEVICE_SCANNER
                    },
                    scannedSnCode = pendingConfigSnCode,
                    onScannedSnCodeConsumed = { pendingConfigSnCode = null },
                )

                RootScreen.LOGIN -> LoginPage(
                    initialTelephone = config.telephone,
                    loginState = loginState,
                    smsCodeState = smsCodeState,
                    onLogin = viewModel::loginToQzxy,
                    onSendVerificationCode = viewModel::sendVerificationCode,
                    onCredentialsChanged = viewModel::invalidateLoginRequest,
                    showBackButton = true,
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
                            OnboardingResumeDestination.NEARBY -> {
                                viewModel.clearSnCodeSaveState()
                                viewModel.clearNearbyPairState()
                                rootScreen = RootScreen.ONBOARDING_NEARBY
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
                    smsCodeState = smsCodeState,
                    onLogin = viewModel::loginToQzxy,
                    onSendVerificationCode = viewModel::sendVerificationCode,
                    onCredentialsChanged = viewModel::invalidateLoginRequest,
                    onBack = {
                        viewModel.cancelQzxyLogin()
                        rootScreen = RootScreen.ONBOARDING_QUOTE
                    },
                    onSkip = {
                        viewModel.cancelQzxyLogin()
                        viewModel.clearSnCodeSaveState()
                        viewModel.clearNearbyPairState()
                        rootScreen = RootScreen.ONBOARDING_NEARBY
                    },
                )

                RootScreen.ONBOARDING_NEARBY -> NearbyDevicesPage(
                    viewModel = viewModel,
                    active = rootScreen == RootScreen.ONBOARDING_NEARBY,
                    currentSnCode = config.snCode,
                    onBack = {
                        viewModel.clearNearbyPairState()
                        rootScreen = RootScreen.ONBOARDING_LOGIN
                    },
                    onScanQrCode = { rootScreen = RootScreen.ONBOARDING_SCANNER },
                    onPaired = {
                        viewModel.clearNearbyPairState()
                        rootScreen = RootScreen.ONBOARDING_COMPLETE
                    },
                    onSkip = {
                        viewModel.clearNearbyPairState()
                        onOnboardingCompleted()
                        rootScreen = RootScreen.MAIN
                    },
                )

                RootScreen.ONBOARDING_SCANNER, RootScreen.DEVICE_SCANNER -> QrScannerPage(
                    title = "绑定你的热水器",
                    allowManualSnCodeEntry = true,
                    scanResetKey = onboardingScanResetKey,
                    navigationEnabled = !onboardingScanSaving,
                    isProcessing = onboardingScanSaving,
                    bindingError = snCodeSaveState.message,
                    onBack = {
                        viewModel.clearSnCodeSaveState()
                        rootScreen = if (screen == RootScreen.DEVICE_SCANNER) RootScreen.MAIN else RootScreen.ONBOARDING_NEARBY
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
