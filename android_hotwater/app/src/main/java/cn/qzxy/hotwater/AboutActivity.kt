package cn.qzxy.hotwater

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.GlassTheme
import cn.qzxy.hotwater.ui.AboutPage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AboutActivity : ComponentActivity() {
    private lateinit var updateManager: AppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updateManager = AppUpdateManager(applicationContext)
        setContent {
            GlassTheme {
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
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf<AppUpdateDownloadProgress?>(null) }
    var checkResult by remember { mutableStateOf<AppUpdateCheckResult?>(null) }
    var installResult by remember { mutableStateOf<AppUpdateInstallResult?>(null) }

    val checkForUpdate: () -> Unit = {
        scope.launch {
            checking = true
            installResult = null
            checkResult = updateManager.checkForUpdate()
            checking = false
        }
    }
    val installUpdate: (AppUpdateInfo) -> Unit = { update ->
        scope.launch {
            installing = true
            downloadProgress = null
            try {
                installResult = updateManager.downloadAndInstall(update) { progress ->
                    downloadProgress = progress
                }
            } finally {
                installing = false
            }
        }
    }
    val closePage: () -> Unit = {
        if (!closing) {
            closing = true
            if (visible) {
                visible = false
                scope.launch {
                    delay(220)
                    onFinish()
                }
            } else {
                onFinish()
            }
        }
    }

    LaunchedEffect(Unit) {
        visible = true
        checkForUpdate()
    }
    BackHandler(onBack = closePage)

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(260)) + slideInHorizontally(tween(320)) { it / 5 },
        exit = fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 5 },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = closePage) {
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
                onCheckForUpdate = checkForUpdate,
                onInstallUpdate = installUpdate,
            )
        }
    }
}
