package cn.qzxy.hotwater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.AppUpdateDownloadProgress
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens

@Composable
internal fun UpdateDownloadProgressContent(
    progress: AppUpdateDownloadProgress?,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalGlassTokens.current
    val fraction = progress?.fraction

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (fraction == null) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = tokens.accentPrimary,
                trackColor = tokens.textPrimary.copy(alpha = 0.12f),
            )
        } else {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = tokens.accentPrimary,
                trackColor = tokens.textPrimary.copy(alpha = 0.12f),
            )
        }
        Text(
            text = downloadProgressLabel(progress),
            color = tokens.textSecondary,
            fontSize = 13.sp,
        )
    }
}

private fun downloadProgressLabel(progress: AppUpdateDownloadProgress?): String {
    if (progress == null) return "正在连接更新服务..."
    val totalBytes = progress.totalBytes ?: return "已下载 ${formatDownloadSize(progress.downloadedBytes)}"
    val percentage = ((progress.fraction ?: 0f) * 100).toInt()
    return "已下载 ${formatDownloadSize(progress.downloadedBytes)} / ${formatDownloadSize(totalBytes)} ($percentage%)"
}

private fun formatDownloadSize(bytes: Long): String {
    return if (bytes < 1024L * 1024L) {
        if (bytes < 1024L) "${bytes.coerceAtLeast(0L)} B" else "${bytes / 1024L} KB"
    } else {
        "%.1f MB".format(bytes.toDouble() / (1024L * 1024L))
    }
}
