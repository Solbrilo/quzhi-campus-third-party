package cn.qzxy.hotwater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.HotWaterConfig
import cn.qzxy.hotwater.designsystem.component.GlassButton
import cn.qzxy.hotwater.designsystem.component.GlassInput
import cn.qzxy.hotwater.designsystem.component.GlassPressable
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
    onOpenLogin: () -> Unit,
    onOpenNearbyDevices: () -> Unit,
    onScanSnCode: () -> Unit,
    scannedSnCode: String?,
    onScannedSnCodeConsumed: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val projectIdFocus = remember { FocusRequester() }
    val userIdFocus = remember { FocusRequester() }
    val accountIdFocus = remember { FocusRequester() }
    val loginCodeFocus = remember { FocusRequester() }
    val telephoneFocus = remember { FocusRequester() }
    val snCodeFocus = remember { FocusRequester() }

    // 本地输入状态
    var projectId by rememberSaveable { mutableStateOf(config.projectId?.toString().orEmpty()) }
    var userId by rememberSaveable { mutableStateOf(config.userId?.toString().orEmpty()) }
    var accountId by rememberSaveable { mutableStateOf(config.accountId?.toString().orEmpty()) }
    var loginCode by rememberSaveable { mutableStateOf(config.loginCode) }
    var telephone by rememberSaveable { mutableStateOf(config.telephone) }
    var snCode by rememberSaveable { mutableStateOf(config.snCode) }
    var snCodeDirty by rememberSaveable { mutableStateOf(false) }
    var lastAppliedConfigFingerprint by rememberSaveable {
        mutableStateOf(configFormFingerprint(config))
    }
    var lastAppliedConfigTelephone by rememberSaveable { mutableStateOf(config.telephone.trim()) }
    var projectIdVisible by rememberSaveable { mutableStateOf(false) }
    var userIdVisible by rememberSaveable { mutableStateOf(false) }
    var accountIdVisible by rememberSaveable { mutableStateOf(false) }
    var loginCodeVisible by rememberSaveable { mutableStateOf(false) }
    var telephoneVisible by rememberSaveable { mutableStateOf(false) }
    var snCodeVisible by rememberSaveable { mutableStateOf(false) }
    var showValidationErrors by rememberSaveable { mutableStateOf(false) }

    val currentConfigFingerprint = configFormFingerprint(config)
    LaunchedEffect(currentConfigFingerprint) {
        if (!shouldRefreshConfigForm(lastAppliedConfigFingerprint, currentConfigFingerprint)) {
            return@LaunchedEffect
        }
        val preserveSnCodeDraft = shouldKeepSnCodeDraft(
            snCodeDirty = snCodeDirty,
            draftSourceTelephone = lastAppliedConfigTelephone,
            refreshedTelephone = config.telephone,
        )
        val refreshed = refreshConfigFormFromConfig(
            config = config,
            currentSnCode = snCode,
            snCodeDirty = snCodeDirty,
            preserveSnCodeDraft = preserveSnCodeDraft,
        )
        projectId = refreshed.projectId
        userId = refreshed.userId
        accountId = refreshed.accountId
        loginCode = refreshed.loginCode
        telephone = refreshed.telephone
        snCode = refreshed.snCode
        snCodeDirty = refreshed.snCodeDirty
        lastAppliedConfigFingerprint = currentConfigFingerprint
        lastAppliedConfigTelephone = config.telephone.trim()
    }

    LaunchedEffect(scannedSnCode) {
        val scanned = scannedSnCode ?: return@LaunchedEffect
        snCode = normalizeConfigSnCode(scanned)
        snCodeDirty = true
        onScannedSnCodeConsumed()
    }

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
    fun sensitiveTransformation(isVisible: Boolean): VisualTransformation =
        if (isVisible) VisualTransformation.None else PasswordVisualTransformation()

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
                text = "登录趣智校园自动填入账号字段，也可以手动补全或扫码绑定设备。",
                color = tokens.textSecondary,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(14.dp))
            GlassButton(
                text = "登录趣智校园",
                onClick = onOpenLogin,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))
            GlassButton(
                text = "选择附近设备",
                onClick = onOpenNearbyDevices,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
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
                visualTransformation = sensitiveTransformation(projectIdVisible),
                trailingContent = {
                    SensitiveFieldVisibilityButton(
                        visible = projectIdVisible,
                        fieldLabel = "项目 ID",
                        onToggle = { projectIdVisible = !projectIdVisible },
                    )
                },
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
                visualTransformation = sensitiveTransformation(userIdVisible),
                trailingContent = {
                    SensitiveFieldVisibilityButton(
                        visible = userIdVisible,
                        fieldLabel = "用户 ID",
                        onToggle = { userIdVisible = !userIdVisible },
                    )
                },
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
                visualTransformation = sensitiveTransformation(accountIdVisible),
                trailingContent = {
                    SensitiveFieldVisibilityButton(
                        visible = accountIdVisible,
                        fieldLabel = "账户 ID",
                        onToggle = { accountIdVisible = !accountIdVisible },
                    )
                },
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
                visualTransformation = sensitiveTransformation(loginCodeVisible),
                trailingContent = {
                    SensitiveFieldVisibilityButton(
                        visible = loginCodeVisible,
                        fieldLabel = "登录凭据",
                        onToggle = { loginCodeVisible = !loginCodeVisible },
                    )
                },
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
                visualTransformation = sensitiveTransformation(telephoneVisible),
                trailingContent = {
                    SensitiveFieldVisibilityButton(
                        visible = telephoneVisible,
                        fieldLabel = "手机号",
                        onToggle = { telephoneVisible = !telephoneVisible },
                    )
                },
            )

            Spacer(modifier = Modifier.height(12.dp))
            GlassInput(
                value = snCode,
                onValueChange = {
                    snCode = it
                    snCodeDirty = true
                },
                label = "设备序列号",
                placeholder = "snCode",
                errorMessage = snCodeError,
                focusRequester = snCodeFocus,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = doneActions,
                visualTransformation = sensitiveTransformation(snCodeVisible),
                trailingContentWidth = 88.dp,
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SensitiveFieldVisibilityButton(
                            visible = snCodeVisible,
                            fieldLabel = "设备序列号",
                            onToggle = { snCodeVisible = !snCodeVisible },
                        )
                        GlassPressable(
                            onClick = onScanSnCode,
                            enabled = !busy,
                            material = GlassMaterial.EmbeddedControl,
                            cornerRadius = 18.dp,
                            modifier = Modifier
                                .size(40.dp)
                                .semantics { contentDescription = "扫描设备二维码" },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.QrCodeScanner,
                                contentDescription = null,
                                tint = tokens.textPrimary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                },
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
                        snCode = normalizeConfigSnCode(snCode),
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
                        snCodeDirty = false
                        lastAppliedConfigTelephone = telephone.trim()
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
            ConfigTransferActions(
                busy = busy,
                onExport = onExport,
                onImport = onImport,
            )
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

@Composable
private fun SensitiveFieldVisibilityButton(
    visible: Boolean,
    fieldLabel: String,
    onToggle: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    GlassPressable(
        onClick = onToggle,
        material = GlassMaterial.EmbeddedControl,
        cornerRadius = 18.dp,
        modifier = Modifier
            .size(40.dp)
            .semantics {
                contentDescription = if (visible) "隐藏$fieldLabel" else "显示$fieldLabel"
            },
    ) {
        Icon(
            imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            contentDescription = null,
            tint = tokens.textPrimary,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
internal fun ConfigTransferActions(
    busy: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val buttonTextStyle = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
    )

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widestLabel = with(density) {
            maxOf(
                textMeasurer.measure(
                    text = ExportConfigLabel,
                    style = buttonTextStyle,
                    maxLines = 1,
                    softWrap = false,
                ).size.width.toDp(),
                textMeasurer.measure(
                    text = ImportConfigLabel,
                    style = buttonTextStyle,
                    maxLines = 1,
                    softWrap = false,
                ).size.width.toDp(),
            )
        }
        val minimumButtonWidth = maxOf(
            120.dp,
            48.dp + 24.dp + 8.dp + widestLabel,
        )
        val minimumTwoColumnWidth = minimumButtonWidth * 2f + 12.dp + 2.dp
        val stackButtons = maxWidth < minimumTwoColumnWidth

        if (stackButtons) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ConfigTransferButton(
                    text = ExportConfigLabel,
                    onClick = onExport,
                    enabled = !busy,
                    export = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ConfigTransferButton(
                    text = ImportConfigLabel,
                    onClick = onImport,
                    enabled = !busy,
                    export = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ConfigTransferButton(
                    text = ExportConfigLabel,
                    onClick = onExport,
                    enabled = !busy,
                    export = true,
                    modifier = Modifier.weight(1f),
                )
                ConfigTransferButton(
                    text = ImportConfigLabel,
                    onClick = onImport,
                    enabled = !busy,
                    export = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ConfigTransferButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    export: Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalGlassTokens.current

    GlassButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        height = 44.dp,
        cornerRadius = 22.dp,
        singleLineText = true,
        icon = {
            Icon(
                imageVector = if (export) Icons.Outlined.FileUpload else Icons.Outlined.FileDownload,
                contentDescription = null,
                tint = tokens.textPrimary,
                modifier = Modifier.size(24.dp),
            )
        },
    )
}

private const val ExportConfigLabel = "导出配置"
private const val ImportConfigLabel = "导入配置"

private fun buildConfigSummary(config: HotWaterConfig): String {
    return if (config.isComplete()) {
        buildString {
            appendLine("配置已完成")
            appendLine("账号: ${HotWaterConfig.maskMiddle(config.telephone, 3, 4)}")
            append("设备 snCode：已隐藏")
        }
    } else {
        "还没完成配置。请填写上方所有字段后点击保存。"
    }
}

private fun getDeviceInfoText(): String {
    return "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}"
}
