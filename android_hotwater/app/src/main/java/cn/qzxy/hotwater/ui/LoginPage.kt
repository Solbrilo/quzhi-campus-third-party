package cn.qzxy.hotwater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.QzxyLoginState
import cn.qzxy.hotwater.designsystem.component.GlassButton
import cn.qzxy.hotwater.designsystem.component.GlassInput
import cn.qzxy.hotwater.designsystem.component.GlassPressable
import cn.qzxy.hotwater.designsystem.theme.GlassMaterial
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.glassMaterial

@Composable
fun LoginPage(
    initialTelephone: String,
    loginState: QzxyLoginState,
    onLogin: (String, String) -> Unit,
    onBack: () -> Unit,
    onSkip: (() -> Unit)? = null,
) {
    val tokens = LocalGlassTokens.current
    val focusManager = LocalFocusManager.current
    var telephone by rememberSaveable(initialTelephone) { mutableStateOf(initialTelephone) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Horizontal))
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassPressable(
                    onClick = onBack,
                    material = GlassMaterial.Control,
                    cornerRadius = 24.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "返回" },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = null,
                        tint = tokens.textPrimary,
                    )
                }
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = "登录趣智校园",
                        color = tokens.textPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "连接你的校园账号",
                        color = tokens.textSecondary,
                        fontSize = 14.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassMaterial(GlassMaterial.Panel, 24.dp)
                    .padding(20.dp),
            ) {
                GlassInput(
                    value = telephone,
                    onValueChange = { telephone = it },
                    label = "手机号",
                    placeholder = "趣智校园手机号",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                )
                Spacer(modifier = Modifier.height(14.dp))
                GlassInput(
                    value = password,
                    onValueChange = { password = it },
                    label = "密码",
                    placeholder = "趣智校园密码",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (telephone.isNotBlank() && password.isNotBlank() && !loginState.loading) {
                                onLogin(telephone.trim(), password)
                            }
                        },
                    ),
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingContent = {
                        GlassPressable(
                            onClick = { passwordVisible = !passwordVisible },
                            material = GlassMaterial.EmbeddedControl,
                            cornerRadius = 18.dp,
                            modifier = Modifier
                                .size(40.dp)
                                .semantics {
                                    contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                                },
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = null,
                                tint = tokens.textSecondary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                )

                if (loginState.message.isNotBlank() && !loginState.success) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = loginState.message,
                        color = tokens.accentError,
                        fontSize = 13.sp,
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                GlassButton(
                    text = if (loginState.loading) "正在登录" else "登录",
                    onClick = {
                        focusManager.clearFocus()
                        onLogin(telephone.trim(), password)
                    },
                    enabled = !loginState.loading && telephone.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "密码只用于本次登录，不会保存在设备中。",
                    color = tokens.textTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            if (onSkip != null) {
                Spacer(modifier = Modifier.height(18.dp))
                GlassPressable(
                    onClick = onSkip,
                    material = GlassMaterial.EmbeddedControl,
                    cornerRadius = 20.dp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .height(48.dp),
                ) {
                    Text(
                        text = "跳过登录",
                        color = tokens.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }
    }
}
