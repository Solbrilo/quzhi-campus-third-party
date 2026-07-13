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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import cn.qzxy.hotwater.designsystem.theme.GlassTheme
import cn.qzxy.hotwater.ui.HotWaterApp

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var updateManager: AppUpdateManager

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

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
        enableEdgeToEdge()
        updateManager = AppUpdateManager(applicationContext)

        setContent {
            GlassTheme {
                HotWaterApp(
                    viewModel = viewModel,
                    updateManager = updateManager,
                    onOpenAbout = { startActivity(Intent(this, AboutActivity::class.java)) },
                    onExportConfig = { exportConfig() },
                    onImportConfig = { importConfig() },
                    onOpenAlipayRecharge = { openAlipayForRecharge() },
                )
            }
        }

        hideNavigationBar()
        viewModel.initialize()
        requestNotificationPermission()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavigationBar()
    }

    private fun hideNavigationBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
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
            return
        }

        val fileName = HotWaterConfigManager.generateExportFileName(config)
        exportConfigLauncher.launch(fileName)
    }

    private fun exportConfigToUri(uri: android.net.Uri) {
        val config = viewModel.getConfig()
        HotWaterConfigManager.writeConfigToUri(this, uri, config)
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
            }
        }
    }

    companion object {
        private const val ALIPAY_PACKAGE_NAME = "com.eg.android.AlipayGphone"
    }
}
