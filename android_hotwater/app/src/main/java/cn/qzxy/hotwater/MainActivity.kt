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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.qzxy.hotwater.designsystem.theme.DEFAULT_GLASS_INTENSITY
import cn.qzxy.hotwater.designsystem.theme.GlassIntensityPreferences
import cn.qzxy.hotwater.designsystem.theme.GlassTheme
import cn.qzxy.hotwater.designsystem.theme.ThemeMode
import cn.qzxy.hotwater.designsystem.theme.ThemeModePreferences
import cn.qzxy.hotwater.designsystem.theme.sanitizeGlassIntensity
import cn.qzxy.hotwater.ui.HotWaterApp

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var updateManager: AppUpdateManager
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var glassIntensity by mutableFloatStateOf(DEFAULT_GLASS_INTENSITY)
    private var notificationPermissionRequested = false
    private var pendingOpenRequested = false
    private var pendingOpenAction: (() -> Unit)? = null
    private var hasEnteredForeground = false

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
        super.onCreate(savedInstanceState)
        notificationPermissionRequested =
            savedInstanceState?.getBoolean(STATE_PERMISSION_REQUESTED) ?: false
        pendingOpenRequested = savedInstanceState?.getBoolean(STATE_PENDING_OPEN) ?: false
        enableEdgeToEdge()
        updateManager = AppUpdateManager(applicationContext)
        themeMode = ThemeModePreferences.read(this)
        glassIntensity = if (savedInstanceState?.containsKey(STATE_GLASS_INTENSITY) == true) {
            sanitizeGlassIntensity(savedInstanceState.getFloat(STATE_GLASS_INTENSITY))
        } else {
            GlassIntensityPreferences.read(this)
        }

        setContent {
            GlassTheme(
                themeMode = themeMode,
                glassIntensity = glassIntensity,
            ) {
                HotWaterApp(
                    viewModel = viewModel,
                    updateManager = updateManager,
                    themeMode = themeMode,
                    onThemeModeChange = { nextMode ->
                        if (themeMode != nextMode) {
                            themeMode = nextMode
                            ThemeModePreferences.write(this, nextMode)
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
                    onOpenAbout = { startActivity(Intent(this, AboutActivity::class.java)) },
                    onExportConfig = { exportConfig() },
                    onImportConfig = { importConfig() },
                    onOpenAlipayRecharge = { openAlipayForRecharge() },
                    onRequestNotificationPermission = { action ->
                        requestNotificationPermissionThen(action)
                    },
                )
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
        if (hasEnteredForeground) {
            viewModel.refreshBalance(announce = false)
        } else {
            hasEnteredForeground = true
        }
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
            Uri.parse("alipays://platformapi/startapp?appId=20000001"),
        ).apply {
            setPackage(ALIPAY_PACKAGE_NAME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "未能打开支付宝，请手动通过支付宝进行充值。", Toast.LENGTH_SHORT).show()
        }
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

        val fileName = HotWaterConfigManager.generateExportFileName(config)
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

    companion object {
        private const val STATE_PENDING_OPEN = "pending_open_after_notification_permission"
        private const val STATE_PERMISSION_REQUESTED = "notification_permission_requested"
        private const val STATE_GLASS_INTENSITY = "glass_intensity"
        private const val ALIPAY_PACKAGE_NAME = "com.eg.android.AlipayGphone"
    }
}
