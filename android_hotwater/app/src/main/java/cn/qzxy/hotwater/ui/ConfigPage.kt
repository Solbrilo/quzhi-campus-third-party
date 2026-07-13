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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.HotWaterConfig
import cn.qzxy.hotwater.designsystem.component.GlassButton
import cn.qzxy.hotwater.designsystem.component.GlassInput
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.glassConvex

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

    // 本地输入状态
    var projectId by remember(config.projectId) { mutableStateOf(config.projectId?.toString().orEmpty()) }
    var userId by remember(config.userId) { mutableStateOf(config.userId?.toString().orEmpty()) }
    var accountId by remember(config.accountId) { mutableStateOf(config.accountId?.toString().orEmpty()) }
    var loginCode by remember(config.loginCode) { mutableStateOf(config.loginCode) }
    var telephone by remember(config.telephone) { mutableStateOf(config.telephone) }
    var snCode by remember(config.snCode) { mutableStateOf(config.snCode) }

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
                .glassConvex(cornerRadius = 28.dp, tokens = tokens)
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
                placeholder = "projectId",
            )

            Spacer(modifier = Modifier.height(12.dp))
            GlassInput(
                value = userId,
                onValueChange = { userId = it },
                placeholder = "userId",
            )

            Spacer(modifier = Modifier.height(12.dp))
            GlassInput(
                value = accountId,
                onValueChange = { accountId = it },
                placeholder = "accountId",
            )

            Spacer(modifier = Modifier.height(12.dp))
            GlassInput(
                value = loginCode,
                onValueChange = { loginCode = it },
                placeholder = "loginCode",
            )

            Spacer(modifier = Modifier.height(12.dp))
            GlassInput(
                value = telephone,
                onValueChange = { telephone = it },
                placeholder = "telephone",
            )

            Spacer(modifier = Modifier.height(12.dp))
            GlassInput(
                value = snCode,
                onValueChange = { snCode = it },
                placeholder = "snCode",
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
                    onSave(rawConfig)
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )

            // 导出/导入按钮
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassButton(
                    text = "导出配置",
                    onClick = onExport,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    height = 44.dp,
                    cornerRadius = 22.dp,
                )
                GlassButton(
                    text = "导入配置",
                    onClick = onImport,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    height = 44.dp,
                    cornerRadius = 22.dp,
                )
            }
        }

        // 设备信息卡片
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassConvex(cornerRadius = 28.dp, tokens = tokens)
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
