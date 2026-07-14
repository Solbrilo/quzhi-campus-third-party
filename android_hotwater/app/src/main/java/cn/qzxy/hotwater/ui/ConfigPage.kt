package cn.qzxy.hotwater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.HotWaterConfig
import cn.qzxy.hotwater.designsystem.component.GlassButton
import cn.qzxy.hotwater.designsystem.component.GlassInput
import cn.qzxy.hotwater.designsystem.motion.performGlassHapticFeedback
import cn.qzxy.hotwater.designsystem.theme.GlassMaterial
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.glassMaterial

@Composable
fun ConfigPage(
    config: HotWaterConfig,
    busy: Boolean,
    onSave: (HotWaterConfig) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val stackSecondaryActions = LocalDensity.current.fontScale > 1.3f
    val projectIdFocus = remember { FocusRequester() }
    val userIdFocus = remember { FocusRequester() }
    val accountIdFocus = remember { FocusRequester() }
    val loginCodeFocus = remember { FocusRequester() }
    val telephoneFocus = remember { FocusRequester() }
    val snCodeFocus = remember { FocusRequester() }

    // 本地输入状态
    var projectId by rememberSaveable(config.projectId) { mutableStateOf(config.projectId?.toString().orEmpty()) }
    var userId by rememberSaveable(config.userId) { mutableStateOf(config.userId?.toString().orEmpty()) }
    var accountId by rememberSaveable(config.accountId) { mutableStateOf(config.accountId?.toString().orEmpty()) }
    var loginCode by rememberSaveable(config.loginCode) { mutableStateOf(config.loginCode) }
    var telephone by rememberSaveable(config.telephone) { mutableStateOf(config.telephone) }
    var snCode by rememberSaveable(config.snCode) { mutableStateOf(config.snCode) }
    var showValidationErrors by rememberSaveable { mutableStateOf(false) }

    fun numericError(value: String, key: String): String? {
        if (!showValidationErrors) return null
        return when {
            value.isBlank() -> "请输入 $key"
            value.trim().toIntOrNull() == null -> "$key 需要是数字"
            else -> null
        }
    }

    fun requiredError(value: String, key: String): String? {
        return if (showValidationErrors && value.isBlank()) "请输入 $key" else null
    }

    val projectIdError = numericError(projectId, "projectId")
    val userIdError = numericError(userId, "userId")
    val accountIdError = numericError(accountId, "accountId")
    val loginCodeError = requiredError(loginCode, "loginCode")
    val telephoneError = requiredError(telephone, "telephone")
    val snCodeError = requiredError(snCode, "snCode")
    val nextActions = KeyboardActions(
        onNext = { focusManager.moveFocus(FocusDirection.Down) },
    )
    val doneActions = KeyboardActions(
        onDone = { focusManager.clearFocus() },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 配置表单卡片
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassMaterial(GlassMaterial.Panel, 28.dp)
                .padding(18.dp)
        ) {
            Text(
                text = "填写配置",
                color = tokens.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "从抓包工具中获取以下参数并手动填入，保存后即可使用。",
                color = tokens.textSecondary,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(16.dp))
            GlassInput(
                value = projectId,
                onValueChange = { projectId = it },
                label = "项目 ID",
                placeholder = "projectId",
                errorMessage = projectIdError,
                focusRequester = projectIdFocus,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = nextActions,
            )

            Spacer(modifier = Modifier.height(12.dp))
            GlassInput(
                value = userId,
                onValueChange = { userId = it },
                label = "用户 ID",
                placeholder = "userId",
                errorMessage = userIdError,
                focusRequester = userIdFocus,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = nextActions,
            )

            Spacer(modifier = Modifier.height(12.dp))
            GlassInput(
                value = accountId,
                onValueChange = { accountId = it },
                label = "账户 ID",
                placeholder = "accountId",
                errorMessage = accountIdError,
                focusRequester = accountIdFocus,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = nextActions,
            )

            Spacer(modifier = Modifier.height(12.dp))
            GlassInput(
                value = loginCode,
                onValueChange = { loginCode = it },
                label = "登录凭据",
                placeholder = "loginCode",
                errorMessage = loginCodeError,
                focusRequester = loginCodeFocus,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = nextActions,
            )

            Spacer(modifier = Modifier.height(12.dp))
            GlassInput(
                value = telephone,
                onValueChange = { telephone = it },
                label = "手机号",
                placeholder = "telephone",
                errorMessage = telephoneError,
                focusRequester = telephoneFocus,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = nextActions,
            )

            Spacer(modifier = Modifier.height(12.dp))
            GlassInput(
                value = snCode,
                onValueChange = { snCode = it },
                label = "设备序列号",
                placeholder = "snCode",
                errorMessage = snCodeError,
                focusRequester = snCodeFocus,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = doneActions,
            )

            // 配置状态
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = buildConfigSummary(config),
                color = tokens.textSecondary,
                fontSize = 13.sp
            )

            // 保存按钮
            Spacer(modifier = Modifier.height(16.dp))
            GlassButton(
                text = "保存配置",
                onClick = {
                    val rawConfig = HotWaterConfig(
                        projectId = projectId.trim().toIntOrNull(),
                        userId = userId.trim().toIntOrNull(),
                        accountId = accountId.trim().toIntOrNull(),
                        loginCode = loginCode.trim(),
                        telephone = telephone.trim(),
                        snCode = snCode.trim().replace(":", "").uppercase(),
                        macAddress = config.macAddress,
                        phoneSystem = config.phoneSystem,
                        version = config.version,
                    )
                    showValidationErrors = true
                    val firstInvalidFocus = when {
                        rawConfig.projectId == null -> projectIdFocus
                        rawConfig.userId == null -> userIdFocus
                        rawConfig.accountId == null -> accountIdFocus
                        rawConfig.loginCode.isBlank() -> loginCodeFocus
                        rawConfig.telephone.isBlank() -> telephoneFocus
                        rawConfig.snCode.isBlank() -> snCodeFocus
                        else -> null
                    }
                    if (firstInvalidFocus != null) {
                        haptic.performGlassHapticFeedback(HapticFeedbackType.Reject)
                        firstInvalidFocus.requestFocus()
                    } else {
                        haptic.performGlassHapticFeedback(HapticFeedbackType.SegmentTick)
                        focusManager.clearFocus()
                        onSave(rawConfig)
                    }
                },
                enabled = !busy,
                hapticFeedback = { null },
                modifier = Modifier.fillMaxWidth(),
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Save,
                        contentDescription = null,
                        tint = tokens.textPrimary,
                    )
                },
            )

            // 导出/导入按钮
            Spacer(modifier = Modifier.height(12.dp))
            @Composable
            fun ExportButton(modifier: Modifier) {
                GlassButton(
                    text = "导出配置",
                    onClick = onExport,
                    enabled = !busy,
                    modifier = modifier,
                    height = 44.dp,
                    cornerRadius = 22.dp,
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.FileUpload,
                            contentDescription = null,
                            tint = tokens.textPrimary,
                        )
                    },
                )
            }

            @Composable
            fun ImportButton(modifier: Modifier) {
                GlassButton(
                    text = "导入配置",
                    onClick = onImport,
                    enabled = !busy,
                    modifier = modifier,
                    height = 44.dp,
                    cornerRadius = 22.dp,
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.FileDownload,
                            contentDescription = null,
                            tint = tokens.textPrimary,
                        )
                    },
                )
            }

            if (stackSecondaryActions) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExportButton(Modifier.fillMaxWidth())
                    ImportButton(Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExportButton(Modifier.weight(1f))
                    ImportButton(Modifier.weight(1f))
                }
            }
        }

        // 设备信息卡片
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassMaterial(GlassMaterial.Panel, 28.dp)
                .padding(18.dp)
        ) {
            Text(
                text = "设备信息",
                color = tokens.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "系统信息",
                color = tokens.textSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = getDeviceInfoText(),
                color = tokens.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // 底部间距
        Spacer(modifier = Modifier.height(bottomDockClearance()))
    }
}

private fun buildConfigSummary(config: HotWaterConfig): String {
    return if (config.isComplete()) {
        buildString {
            appendLine("配置已完成")
            appendLine("账号: ${HotWaterConfig.maskMiddle(config.telephone, 3, 4)}")
            append("设备 snCode: ${config.snCode}")
        }
    } else {
        "还没完成配置。请填写上方所有字段后点击保存。"
    }
}

private fun getDeviceInfoText(): String {
    return "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}"
}
