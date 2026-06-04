package cn.qzxy.hotwater

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import cn.qzxy.hotwater.designsystem.theme.GlassTheme
import cn.qzxy.hotwater.ui.HotWaterApp

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    private val exportConfigLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { exportConfigToUri(it) }
    }

    private val importConfigLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importConfigFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GlassTheme {
                HotWaterApp(
                    viewModel = viewModel,
                    onExportConfig = { exportConfig() },
                    onImportConfig = { importConfig() },
                )
            }
        }

        viewModel.initialize()
        requestNotificationPermission()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun exportConfig() {
        val config = viewModel.getConfig()
        val validationError = HotWaterConfigManager.validateConfig(config)
        if (validationError != null) {
            viewModel.clearFeedbackEvent()
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
}
