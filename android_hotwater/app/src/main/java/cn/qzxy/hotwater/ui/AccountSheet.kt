package cn.qzxy.hotwater.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.AccountActionState
import cn.qzxy.hotwater.designsystem.component.*
import cn.qzxy.hotwater.designsystem.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HalfPageSheet(title: String?, onDismiss: () -> Unit, busy: Boolean = false,
    content: @Composable ColumnScope.(close: (() -> Unit) -> Unit) -> Unit) {
    val latestBusy by rememberUpdatedState(busy)
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !latestBusy })
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val close: (() -> Unit) -> Unit = { after ->
        if (!latestBusy && !closing) {
            closing = true
            scope.launch { try { state.hide(); if (!state.isVisible) { onDismiss(); after() } } finally { closing = false } }
        }
    }
    val tokens = LocalGlassTokens.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = tokens.meshBase,
        contentColor = tokens.textPrimary, shape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = LocalThemeGeometry.current.corner(28.dp), topEnd = LocalThemeGeometry.current.corner(28.dp))) {
        Column(Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .78f)
            .height((LocalConfiguration.current.screenHeightDp.dp * .62f).coerceAtLeast(320.dp))
            .padding(horizontal = 22.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title.orEmpty(), Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                GlassPressable({ close {} }, enabled = !busy && !closing, modifier = Modifier.size(42.dp), cornerRadius = 21.dp) {
                    Icon(Icons.Outlined.Close, "关闭", tint = tokens.textSecondary)
                }
            }
            content(close)
        }
    }
}

@Composable
internal fun AccountSheet(changingPhone: Boolean, telephone: String, state: AccountActionState,
    onDismiss: () -> Unit, onSendCode: (String, Boolean) -> Unit,
    onSubmit: (String?, String, String, String?) -> Unit) {
    // Passwords and verification codes deliberately never enter saved instance state.
    var phone by remember { mutableStateOf("") }
    var old by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var sms by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val tokens = LocalGlassTokens.current
    HalfPageSheet(if (changingPhone) "更换手机号" else "修改密码", onDismiss, state.busy) { close ->
        LaunchedEffect(state.success) { if (state.success) { old = ""; password = ""; confirm = ""; code = ""; close {} } }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("当前账户 ${cn.qzxy.hotwater.HotWaterConfig.maskMiddle(telephone, 3, 4)}", color = tokens.textSecondary, fontSize = 13.sp)
            AnimatedContent(changingPhone || sms, label = "account_auth_method") { useCode ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (changingPhone) GlassInput(phone, { if (!state.busy && !state.sending) { phone = it.filter(Char::isDigit).take(11); code = "" } }, label = "新手机号",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    if (useCode) {
                        Text(if (changingPhone) "验证码将发送到新手机号" else "验证码将发送到当前绑定手机号", color = tokens.textSecondary, fontSize = 12.sp)
                        GlassInput(code, { if (!state.busy) code = it.filter(Char::isDigit).take(6) }, label = "短信验证码",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        TextButton(onClick = { onSendCode(if (changingPhone) phone else telephone, changingPhone) },
                            enabled = !state.busy && !state.sending && state.remaining == 0) {
                            Text(if (state.sending) "发送中…" else if (state.remaining > 0) "${state.remaining} 秒后重发" else "获取验证码")
                        }
                    } else GlassInput(old, { if (!state.busy) old = it }, label = "当前密码", visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                }
            }
            if (!changingPhone) {
                GlassInput(password, { if (!state.busy) password = it }, label = "新密码", visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                GlassInput(confirm, { if (!state.busy) confirm = it }, label = "确认新密码", visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                TextButton(onClick = { sms = !sms; old = ""; code = ""; error = null }, enabled = !state.busy) {
                    Text(if (sms) "使用原密码验证" else "没有设置密码？使用短信验证")
                }
            }
            (error ?: state.error)?.let { Text(it, color = tokens.accentError, fontSize = 13.sp) }
            Text("修改成功后需要重新登录。请先结束用水并等待账单同步。", color = tokens.textTertiary, fontSize = 12.sp)
        }
        GlassButton(if (state.busy) "正在提交…" else "确认修改", {
            error = when {
                !changingPhone && password != confirm -> "两次输入的新密码不一致"
                !changingPhone && password.isBlank() -> "请输入新密码"
                else -> null
            }
            if (error == null) onSubmit(if (changingPhone) phone else null, old, password, if (changingPhone || sms) code else null)
        }, enabled = !state.busy && !state.sending, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
    }
}
