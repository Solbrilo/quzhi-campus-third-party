package cn.qzxy.hotwater.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.designsystem.component.GlassPressable
import cn.qzxy.hotwater.designsystem.theme.GlassMaterial
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens

private data class OpenSourceProject(val name: String, val author: String, val license: String,
    val description: String, val url: String)

private val referencedProjects = listOf(
    OpenSourceProject("LinYu", "yehu-imei / LinYu Contributors", "MIT",
        "短信登录、附近设备发现与校园账单接口的实现参考。", "https://github.com/yehu-imei/linyu"),
    OpenSourceProject("muguang · 沐光", "Ranxian / LinYu Contributors", "MIT",
        "充值金额与支付方式查询、订单签名及支付宝支付流程的实现参考。", "https://github.com/Ranxian/muguang"),
    OpenSourceProject("AndroidLiquidGlass / Backdrop", "Kyant", "Apache-2.0",
        "提供液态玻璃的背景采样、模糊、折射与高光效果。", "https://github.com/Kyant0/AndroidLiquidGlass"),
    OpenSourceProject("AndroidX / Jetpack", "Android Open Source Project", "Apache-2.0",
        "Compose 界面与动画、Activity、Lifecycle、WorkManager、CameraX、AppCompat 和 Security Crypto。", "https://github.com/androidx/androidx"),
    OpenSourceProject("Material Components for Android", "Google", "Apache-2.0",
        "Material 界面组件与 Android 主题基础。", "https://github.com/material-components/material-components-android"),
    OpenSourceProject("Material Design Icons", "Google", "Apache-2.0",
        "应用中使用的开源界面图标。", "https://github.com/google/material-design-icons"),
    OpenSourceProject("Kotlin / kotlinx.coroutines", "JetBrains and contributors", "Apache-2.0",
        "应用语言、协程、异步任务与状态流。", "https://github.com/Kotlin/kotlinx.coroutines"),
    OpenSourceProject("OkHttp / Okio", "Square and contributors", "Apache-2.0",
        "网络请求、连接复用、超时取消与底层 I/O。", "https://github.com/square/okhttp"),
    OpenSourceProject("Haze", "Chris Banes", "Apache-2.0",
        "兼容系统上的背景模糊和玻璃材质。", "https://github.com/chrisbanes/haze"),
    OpenSourceProject("Tink", "Google", "Apache-2.0",
        "AndroidX Security 使用的加密基础库，保护本机账号配置。", "https://github.com/tink-crypto/tink"),
    OpenSourceProject("Gradle / Android Gradle Plugin", "Gradle / Android Open Source Project", "Apache-2.0",
        "应用构建、资源处理与发布工具。", "https://github.com/gradle/gradle"),
    OpenSourceProject("JUnit", "JUnit contributors", "EPL-1.0",
        "协议、状态与账单逻辑的本地测试工具。", "https://github.com/junit-team/junit4"),
)

@Composable
internal fun OpenSourcePage(onOpenExternal: (String) -> Unit) {
    val tokens = LocalGlassTokens.current
    Text("这些开源项目为浴爱提供了灵感与技术支持。点击项目可查看源码与许可。",
        color = tokens.textSecondary, fontSize = 14.sp, lineHeight = 23.sp)
    referencedProjects.forEach { project ->
        GlassPressable(onClick = { onOpenExternal(project.url) }, material = GlassMaterial.Panel,
            cornerRadius = 24.dp, contentPadding = PaddingValues(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(project.name, color = tokens.textPrimary, fontSize = 18.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, "查看 ${project.name} 仓库",
                        modifier = Modifier.size(18.dp), tint = tokens.accentPrimary)
                }
                Text(project.description, color = tokens.textSecondary, fontSize = 14.sp, lineHeight = 22.sp)
                Text("${project.author}  ·  ${project.license}", color = tokens.textTertiary, fontSize = 11.sp)
            }
        }
    }
    Text("其他开发套件", color = tokens.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    listOf(
        "支付宝 Android SDK" to "https://opendocs.alipay.com/open/204",
        "Google ML Kit · 条码扫描" to "https://developers.google.com/ml-kit/terms",
    ).forEach { (name, url) ->
        GlassPressable({ onOpenExternal(url) }, material = GlassMaterial.Panel, cornerRadius = 24.dp,
            contentPadding = PaddingValues(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, color = tokens.textPrimary, fontWeight = FontWeight.Bold)
                Text("使用官方二进制 SDK，许可遵循服务商条款。点击查看文档。", color = tokens.textSecondary, fontSize = 12.sp)
            }
        }
    }
}
