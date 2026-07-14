package cn.qzxy.hotwater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.AppUpdateCheckResult
import cn.qzxy.hotwater.AppUpdateDownloadProgress
import cn.qzxy.hotwater.AppUpdateInfo
import cn.qzxy.hotwater.AppUpdateInstallResult
import cn.qzxy.hotwater.BuildConfig
import cn.qzxy.hotwater.designsystem.component.GlassButton
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.glassConvex

@Composable
fun AboutPage(
    checkResult: AppUpdateCheckResult?,
    installResult: AppUpdateInstallResult?,
    checking: Boolean,
    installing: Boolean,
    downloadProgress: AppUpdateDownloadProgress?,
    onCheckForUpdate: () -> Unit,
    onInstallUpdate: (AppUpdateInfo) -> Unit,
    onCancelInstall: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val availableUpdate = checkResult as? AppUpdateCheckResult.UpdateAvailable

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassConvex(cornerRadius = 28.dp, tokens = tokens)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "趣智校园热水",
                color = tokens.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "第三方热水控制工具",
                color = tokens.textSecondary,
                fontSize = 14.sp,
            )
            Text(
                text = "当前版本 v${BuildConfig.VERSION_NAME}",
                color = tokens.accentPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassConvex(cornerRadius = 28.dp, tokens = tokens)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "检查更新",
                color = tokens.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            UpdateStatusText(checkResult = checkResult, installResult = installResult)
            GlassButton(
                text = "检查更新",
                onClick = onCheckForUpdate,
                modifier = Modifier.fillMaxWidth(),
                enabled = !installing,
                isLoading = checking,
            )

            if (installing) {
                UpdateDownloadProgressContent(
                    progress = downloadProgress,
                    modifier = Modifier.fillMaxWidth(),
                )
                GlassButton(
                    text = "取消下载",
                    onClick = onCancelInstall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (availableUpdate != null) {
                Text(
                    text = "发现 v${availableUpdate.update.versionName}",
                    color = tokens.accentPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = availableUpdate.update.releaseNotes.ifBlank { "暂无更新说明。" },
                    color = tokens.textSecondary,
                    fontSize = 13.sp,
                )
                GlassButton(
                    text = "立即更新",
                    onClick = { onInstallUpdate(availableUpdate.update) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !checking && !installing,
                    isLoading = installing,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun UpdateStatusText(
    checkResult: AppUpdateCheckResult?,
    installResult: AppUpdateInstallResult?,
) {
    val tokens = LocalGlassTokens.current
    val (message, color) = when {
        installResult is AppUpdateInstallResult.InstallationLaunched ->
            "已打开系统安装确认，请完成更新。" to tokens.accentSuccess
        installResult is AppUpdateInstallResult.SourcePermissionRequired ->
            "请允许安装未知应用后，再次点击立即更新。" to tokens.accentWarning
        installResult is AppUpdateInstallResult.Failed -> installResult.message to tokens.accentError
        checkResult is AppUpdateCheckResult.UpToDate -> "当前已是最新版本。" to tokens.accentSuccess
        checkResult is AppUpdateCheckResult.NoRelease -> "暂无已发布版本。" to tokens.textSecondary
        checkResult is AppUpdateCheckResult.InvalidRelease -> checkResult.message to tokens.accentWarning
        checkResult is AppUpdateCheckResult.Failed -> checkResult.message to tokens.accentError
        else -> "通过 Gitee Release 检查新版本。" to tokens.textSecondary
    }
    Text(
        text = message,
        color = color,
        fontSize = 13.sp,
    )
}
