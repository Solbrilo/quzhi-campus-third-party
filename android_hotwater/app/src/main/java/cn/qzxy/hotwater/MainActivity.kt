package cn.qzxy.hotwater

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.qzxy.hotwater.designsystem.theme.DEFAULT_GLASS_INTENSITY
import cn.qzxy.hotwater.designsystem.theme.GlassIntensityPreferences
import cn.qzxy.hotwater.designsystem.theme.GlassTheme
import cn.qzxy.hotwater.designsystem.theme.ThemePalette
import cn.qzxy.hotwater.designsystem.theme.ThemeMode
import cn.qzxy.hotwater.designsystem.theme.ThemeModePreferences
import cn.qzxy.hotwater.designsystem.theme.ThemePalettePreferences
import cn.qzxy.hotwater.designsystem.theme.StartupTheme
import cn.qzxy.hotwater.designsystem.theme.sanitizeGlassIntensity
import cn.qzxy.hotwater.ui.HotWaterRoot
import cn.qzxy.hotwater.ui.UpdateWelcomePage

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var updateManager: AppUpdateManager
    private lateinit var onboardingPreferences: OnboardingPreferences
    private lateinit var updateWelcomePreferences: UpdateWelcomePreferences
    private var updateWelcomeRequired by mutableStateOf(false)
    private var onboardingRequired by mutableStateOf(false)
    private var themePalette by mutableStateOf(ThemePalette.SKY)
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var glassIntensity by mutableFloatStateOf(DEFAULT_GLASS_INTENSITY)
    private var notificationPermissionRequested = false
    private var pendingOpenRequested = false
    private var pendingOpenAction: (() -> Unit)? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val action = pendingOpenAction
            val shouldOpen = pendingOpenRequested
            pendingOpenAction = null
            pendingOpenRequested = false
            if (!granted && shouldOpen) {
                Toast.makeText(
                    this,
                    "未授予通知权限，热水可继续开启，但无法显示实况窗。请在系统通知设置中开启后重试。",
                    Toast.LENGTH_LONG,
                ).show()
            } else if (granted && shouldOpen) {
                notifyLiveUpdateFallbackIfNeeded()
            }
            action?.invoke()
            if (action == null && shouldOpen) viewModel.toggleWhenIdle()
        }

    private val exportConfigLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let { exportConfigToUri(it) }
    }

    private val importConfigLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { importConfigFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        themePalette = ThemePalettePreferences.read(this)
        themeMode = ThemeModePreferences.read(this)
        setTheme(StartupTheme.resource(themePalette, themeMode))
        super.onCreate(savedInstanceState)
        StartupTheme.sync(this, themePalette, themeMode)
        notificationPermissionRequested =
            savedInstanceState?.getBoolean(STATE_PERMISSION_REQUESTED) ?: false
        pendingOpenRequested = savedInstanceState?.getBoolean(STATE_PENDING_OPEN) ?: false
        enableEdgeToEdge()
        updateManager = AppUpdateManager(applicationContext)
        onboardingPreferences = OnboardingPreferences(applicationContext)
        updateWelcomePreferences = UpdateWelcomePreferences(applicationContext)
        @Suppress("DEPRECATION")
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        updateWelcomeRequired = updateWelcomePreferences.shouldShow(
            onboardingPreferences.completedVersionCode(), packageInfo.firstInstallTime, packageInfo.lastUpdateTime,
        )
        onboardingRequired = !updateWelcomeRequired && onboardingPreferences.shouldShowForCurrentVersion()
        glassIntensity = if (savedInstanceState?.containsKey(STATE_GLASS_INTENSITY) == true) {
            sanitizeGlassIntensity(savedInstanceState.getFloat(STATE_GLASS_INTENSITY))
        } else {
            GlassIntensityPreferences.read(this)
        }

        setContent {
            GlassTheme(
                palette = themePalette,
                themeMode = themeMode,
                glassIntensity = glassIntensity,
            ) {
                if (!updateWelcomeRequired) {
                    HotWaterRoot(
                        viewModel = viewModel,
                        updateManager = updateManager,
                        startupOverlayVisible = updateWelcomeRequired,
                        onboardingRequired = onboardingRequired,
                        onOnboardingCompleted = {
                            onboardingPreferences.markCurrentVersionCompleted()
                            onboardingRequired = false
                        },
                        themePalette = themePalette,
                        onThemePaletteChange = { next ->
                            themePalette = next
                            ThemePalettePreferences.write(this, next)
                            StartupTheme.sync(this, next, themeMode)
                        },
                        themeMode = themeMode,
                        onThemeModeChange = { nextMode ->
                            if (themeMode != nextMode) {
                                themeMode = nextMode
                                ThemeModePreferences.write(this, nextMode)
                                StartupTheme.sync(this, themePalette, nextMode)
                            }
                        },
                        glassIntensity = glassIntensity,
                        onGlassIntensityChange = { nextIntensity ->
                            glassIntensity = sanitizeGlassIntensity(nextIntensity)
                        },
                        onGlassIntensityChangeFinished = {
                            GlassIntensityPreferences.write(this, glassIntensity)
                        },
                        onResetGlassIntensity = {
                            glassIntensity = DEFAULT_GLASS_INTENSITY
                            GlassIntensityPreferences.write(this, glassIntensity)
                        },
                        onExportConfig = { exportConfig() },
                        onImportConfig = { importConfig() },
                        onOpenAlipayRecharge = { openAlipayForRecharge() },
                        onRequestNotificationPermission = { action ->
                            requestNotificationPermissionThen(action)
                        },
                        onClearAllUserData = { clearAllUserData() },
                    )
                }
                if (updateWelcomeRequired) {
                    UpdateWelcomePage(onContinue = {
                        if (updateWelcomePreferences.markSeen()) {
                            onboardingPreferences.markCurrentVersionCompleted()
                            updateWelcomeRequired = false
                        } else {
                            Toast.makeText(this, "未能保存确认状态，请重试。", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }
        }

        viewModel.initialize()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_PERMISSION_REQUESTED, notificationPermissionRequested)
        outState.putBoolean(STATE_PENDING_OPEN, pendingOpenRequested)
        outState.putFloat(STATE_GLASS_INTENSITY, glassIntensity)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppForegrounded()
        lifecycleScope.launch(Dispatchers.IO) { AnonymousActivity.recordOpen(applicationContext) }
    }

    override fun onStop() {
        viewModel.onAppBackgrounded()
        super.onStop()
    }

    private fun requestNotificationPermissionThen(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                pendingOpenRequested = false
                pendingOpenAction = null
                notifyLiveUpdateFallbackIfNeeded()
                action()
            } else if (!notificationPermissionRequested) {
                notificationPermissionRequested = true
                pendingOpenRequested = true
                pendingOpenAction = action
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                pendingOpenRequested = false
                pendingOpenAction = null
                Toast.makeText(
                    this,
                    "通知权限未开启，热水可继续开启，但无法显示实况窗。请在系统通知设置中开启后重试。",
                    Toast.LENGTH_LONG,
                ).show()
                action()
            }
        } else {
            pendingOpenRequested = false
            pendingOpenAction = null
            notifyLiveUpdateFallbackIfNeeded()
            action()
        }
    }

    private fun notifyLiveUpdateFallbackIfNeeded() {
        val message = when {
            Build.VERSION.SDK_INT < 36 -> "当前 Android 版本不支持实况窗，已使用常驻计时通知。"
            !NotificationHelper.canPostPromotedNotifications(this) ->
                "系统未允许实况通知提升，已使用常驻计时通知。请在系统通知设置中开启实况通知。"
            else -> null
        } ?: return
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun openAlipayForRecharge() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            buildAlipayRechargeUri(),
        ).apply {
            setPackage(ALIPAY_PACKAGE_NAME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            showAlipayUnavailableMessage()
        } catch (_: SecurityException) {
            showAlipayUnavailableMessage()
        }
    }

    private fun buildAlipayRechargeUri(): Uri = Uri.Builder()
        .scheme("alipays")
        .authority("platformapi")
        .appendPath("startapp")
        .appendQueryParameter("appId", QZXY_ALIPAY_MINI_APP_ID)
        .appendQueryParameter("page", QZXY_ALIPAY_RECHARGE_PAGE)
        .build()

    private fun showAlipayUnavailableMessage() {
        Toast.makeText(
            this,
            "未能打开支付宝，请确认已安装支付宝后重试。",
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun exportConfig() {
        val config = viewModel.getConfig()
        val validationError = HotWaterConfigManager.validateConfig(config)
        if (validationError != null) {
            viewModel.reportFeedback(
                FeedbackEvent.FeedbackType.WARNING,
                "配置不完整，暂时无法导出",
            )
            return
        }

        val fileName = HotWaterConfigManager.generateExportFileName()
        exportConfigLauncher.launch(fileName)
    }

    private fun exportConfigToUri(uri: android.net.Uri) {
        val config = viewModel.getConfig()
        HotWaterConfigManager.writeConfigToUri(this, uri, config)
            .onSuccess {
                viewModel.reportFeedback(FeedbackEvent.FeedbackType.SUCCESS, "配置已导出")
            }
            .onFailure { error ->
                viewModel.reportFeedback(
                    FeedbackEvent.FeedbackType.ERROR,
                    error.message ?: "导出配置失败",
                )
            }
    }

    private fun importConfig() {
        importConfigLauncher.launch(arrayOf("application/json"))
    }

    private fun importConfigFromUri(uri: android.net.Uri) {
        val result = HotWaterConfigManager.readConfigFromUri(this, uri)
        result.onSuccess { config ->
            val validationError = HotWaterConfigManager.validateConfig(config)
            if (validationError == null) {
                viewModel.saveConfig(config)
            } else {
                viewModel.reportFeedback(FeedbackEvent.FeedbackType.ERROR, validationError)
            }
        }.onFailure { error ->
            viewModel.reportFeedback(
                FeedbackEvent.FeedbackType.ERROR,
                error.message ?: "导入配置失败",
            )
        }
    }

    private fun clearAllUserData() {
        val activityCleared = AnonymousActivity.clear(applicationContext)
        val cleared = viewModel.clearAllUserData() && activityCleared
        onboardingPreferences.clear()
        ThemeModePreferences.clear(this)
        themePalette = ThemePalette.SKY
        themeMode = ThemeMode.SYSTEM
        StartupTheme.sync(this, themePalette, themeMode)
        glassIntensity = DEFAULT_GLASS_INTENSITY
        onboardingRequired = true
        pendingOpenRequested = false
        pendingOpenAction = null
        Toast.makeText(
            this,
            if (cleared) "已清空所有信息，请重新配置。" else "未能完整清空本机信息，请重试。",
            Toast.LENGTH_SHORT,
        ).show()
    }

    companion object {
        private const val STATE_PENDING_OPEN = "pending_open_after_notification_permission"
        private const val STATE_PERMISSION_REQUESTED = "notification_permission_requested"
        private const val STATE_GLASS_INTENSITY = "glass_intensity"
        private const val ALIPAY_PACKAGE_NAME = "com.eg.android.AlipayGphone"
        private const val QZXY_ALIPAY_MINI_APP_ID = "2018090661238647"
        private const val QZXY_ALIPAY_RECHARGE_PAGE = "pages/index/index"
    }
}
