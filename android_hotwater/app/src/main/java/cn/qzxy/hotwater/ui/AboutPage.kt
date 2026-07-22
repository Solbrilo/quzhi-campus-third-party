package cn.qzxy.hotwater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.AppUpdateCheckResult
import cn.qzxy.hotwater.AppUpdateDownloadProgress
import cn.qzxy.hotwater.AppUpdateInfo
import cn.qzxy.hotwater.AppUpdateInstallResult
import cn.qzxy.hotwater.BuildConfig
import cn.qzxy.hotwater.FeedbackCategory
import cn.qzxy.hotwater.FeedbackClient
import cn.qzxy.hotwater.FeedbackSubmitResult
import cn.qzxy.hotwater.designsystem.component.GlassButton
import cn.qzxy.hotwater.designsystem.component.GlassPressable
import cn.qzxy.hotwater.designsystem.theme.GlassMaterial
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.glassMaterial
import kotlinx.coroutines.launch

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
    val feedbackClient = remember { FeedbackClient() }
    val scope = rememberCoroutineScope()
    var feedbackCategory by rememberSaveable { mutableStateOf(FeedbackCategory.Bug) }
    var feedbackMessage by rememberSaveable { mutableStateOf("") }
    var feedbackError by rememberSaveable { mutableStateOf<String?>(null) }
    var feedbackStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var feedbackSucceeded by rememberSaveable { mutableStateOf(false) }
    var submittingFeedback by remember { mutableStateOf(false) }

    val submitFeedback: () -> Unit = submit@{
        if (submittingFeedback) return@submit
        val normalizedMessage = feedbackMessage.trim()
        val validationError = FeedbackClient.validationError(normalizedMessage)
        if (validationError != null) {
            feedbackError = validationError
            feedbackStatus = null
            feedbackSucceeded = false
            return@submit
        }
        val submittedCategory = feedbackCategory
        submittingFeedback = true
        scope.launch {
            feedbackError = null
            feedbackStatus = null
            feedbackSucceeded = false
            try {
                when (val result = feedbackClient.submit(
                    appVersion = BuildConfig.VERSION_NAME,
                    category = submittedCategory,
                    message = normalizedMessage,
                )) {
                    is FeedbackSubmitResult.Success -> {
                        if (feedbackMessage.trim() == normalizedMessage) {
                            feedbackMessage = ""
                        }
                        feedbackSucceeded = true
                        feedbackStatus = "提交成功，反馈编号：${result.feedbackId}"
                    }
                    is FeedbackSubmitResult.RateLimited -> {
                        val waitHint = result.retryAfterSeconds?.let { "请等待 ${it} 秒后" } ?: "请稍后"
                        feedbackStatus = "提交过于频繁，${waitHint}手动重试。反馈内容已保留。"
                    }
                    is FeedbackSubmitResult.Rejected -> feedbackError = result.message
                    is FeedbackSubmitResult.Failed -> feedbackStatus = result.message
                }
            } finally {
                submittingFeedback = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassMaterial(GlassMaterial.Panel, 28.dp)
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
                .glassMaterial(GlassMaterial.Panel, 28.dp)
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassMaterial(GlassMaterial.Panel, 28.dp)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "问题反馈",
                color = tokens.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "请选择类型",
                color = tokens.textSecondary,
                fontSize = 12.sp,
            )
            FeedbackCategoryRow(
                categories = listOf(FeedbackCategory.Bug, FeedbackCategory.Suggestion),
                selected = feedbackCategory,
                enabled = !submittingFeedback,
                onSelected = {
                    feedbackCategory = it
                    feedbackStatus = null
                    feedbackSucceeded = false
                },
            )
            FeedbackCategoryRow(
                categories = listOf(FeedbackCategory.Experience, FeedbackCategory.Other),
                selected = feedbackCategory,
                enabled = !submittingFeedback,
                onSelected = {
                    feedbackCategory = it
                    feedbackStatus = null
                    feedbackSucceeded = false
                },
            )
            FeedbackMessageInput(
                value = feedbackMessage,
                onValueChange = { value ->
                    if (FeedbackClient.countUnicodeCharacters(value) <= 2000) {
                        feedbackMessage = value
                        feedbackError = null
                        feedbackStatus = null
                        feedbackSucceeded = false
                    }
                },
                errorMessage = feedbackError,
                enabled = !submittingFeedback,
            )
            Text(
                text = "${FeedbackClient.countUnicodeCharacters(feedbackMessage)}/2000",
                color = tokens.textTertiary,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.End),
            )
            Text(
                text = "请勿填写账号、手机号、学号、校园卡号、密码或 Token。仅在点击提交后发送上述反馈内容。",
                color = tokens.textSecondary,
                fontSize = 12.sp,
            )
            if (feedbackStatus != null) {
                Text(
                    text = feedbackStatus.orEmpty(),
                    color = if (feedbackSucceeded) tokens.accentSuccess else tokens.accentWarning,
                    fontSize = 13.sp,
                    modifier = Modifier.semantics {
                        liveRegion = if (feedbackSucceeded) LiveRegionMode.Polite else LiveRegionMode.Assertive
                        stateDescription = feedbackStatus.orEmpty()
                    },
                )
            }
            GlassButton(
                text = "提交反馈",
                onClick = submitFeedback,
                modifier = Modifier.fillMaxWidth(),
                enabled = !submittingFeedback,
                isLoading = submittingFeedback,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun FeedbackCategoryRow(
    categories: List<FeedbackCategory>,
    selected: FeedbackCategory,
    enabled: Boolean,
    onSelected: (FeedbackCategory) -> Unit,
) {
    val tokens = LocalGlassTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { category ->
            GlassPressable(
                onClick = { onSelected(category) },
                enabled = enabled,
                selected = category == selected,
                material = if (category == selected) GlassMaterial.Control else GlassMaterial.EmbeddedControl,
                cornerRadius = 16.dp,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
            ) {
                Text(
                    text = category.label,
                    color = if (category == selected) tokens.accentPrimary else tokens.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (category == selected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun FeedbackMessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    errorMessage: String?,
    enabled: Boolean,
) {
    val tokens = LocalGlassTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "反馈内容",
            color = if (errorMessage == null) tokens.textSecondary else tokens.accentError,
            fontSize = 12.sp,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 132.dp)
                .glassMaterial(GlassMaterial.Input, 16.dp)
                .padding(16.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp)
                    .semantics {
                        contentDescription = "反馈内容"
                        errorMessage?.let { error(it) }
                    },
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                    color = tokens.textPrimary,
                    fontSize = 15.sp,
                ),
                cursorBrush = SolidColor(tokens.accentPrimary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (value.isEmpty()) {
                            Text(
                                text = "请描述遇到的问题或建议（2-2000 字）",
                                color = tokens.textTertiary,
                                fontSize = 15.sp,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = tokens.accentError,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun UpdateStatusText(
    checkResult: AppUpdateCheckResult?,
    installResult: AppUpdateInstallResult?,
) {
    val tokens = LocalGlassTokens.current
    val isError = installResult is AppUpdateInstallResult.Failed ||
        checkResult is AppUpdateCheckResult.Failed
    val (message, color) = when {
        installResult is AppUpdateInstallResult.InstallationLaunched ->
            "已打开系统安装确认，请完成更新。" to tokens.accentSuccess
        installResult is AppUpdateInstallResult.SourcePermissionRequired ->
            "请允许安装未知应用，返回后将自动继续安装。" to tokens.accentWarning
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
        modifier = Modifier.semantics {
            liveRegion = if (isError) LiveRegionMode.Assertive else LiveRegionMode.Polite
            stateDescription = message
        },
    )
}
