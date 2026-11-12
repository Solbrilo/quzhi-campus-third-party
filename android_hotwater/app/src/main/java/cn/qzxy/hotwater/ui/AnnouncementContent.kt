package cn.qzxy.hotwater.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.AnnouncementResponseState
import cn.qzxy.hotwater.ServerMessage
import cn.qzxy.hotwater.designsystem.component.*
import cn.qzxy.hotwater.designsystem.theme.*

@Composable
internal fun AnnouncementOptions(message: ServerMessage, state: AnnouncementResponseState,
    onRespond: (ServerMessage, String, String) -> Unit) {
    val notice = message.systemNotice ?: return
    if (notice.options.isEmpty()) return
    var choice by remember(message.revision) { mutableStateOf<String?>(null) }
    var text by remember(message.revision) { mutableStateOf("") }
    val tokens = LocalGlassTokens.current
    val sending = state.sending
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (message.revision in state.saved) "已提交回复，可重新选择修改" else "你的回应", color = tokens.textSecondary, fontSize = 13.sp)
        notice.options.forEach { option ->
            GlassPressable({ choice = option.id; text = "" }, selected = choice == option.id, enabled = !sending,
                cornerRadius = 18.dp, material = GlassMaterial.EmbeddedControl,
                modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                Text(option.label, color = if (choice == option.id) tokens.accentPrimary else tokens.textPrimary, fontSize = 14.sp)
            }
        }
        val input = notice.options.firstOrNull { it.id == choice }?.input == true
        AnimatedVisibility(input) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassInput(text, { if (!sending) text = it.take(1000) }, label = "填写回复", placeholder = "请输入你的想法")
                Text("${text.length}/1000 · 请勿填写账号、手机号或密码", color = tokens.textTertiary, fontSize = 10.sp)
            }
        }
        if (state.revision == message.revision) state.error?.let { Text(it, color = tokens.accentError, fontSize = 12.sp) }
        GlassButton(if (sending) "提交中…" else "提交", { choice?.let { onRespond(message, it, text) } },
            enabled = !sending && choice != null && (!input || text.isNotBlank()), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun SystemAnnouncementDialog(message: ServerMessage, state: AnnouncementResponseState,
    onRespond: (ServerMessage, String, String) -> Unit, onDismiss: () -> Unit, onDetails: () -> Unit) {
    val tokens = LocalGlassTokens.current
    AlertDialog(onDismissRequest = { if (!state.sending) onDismiss() },
        modifier = Modifier.glassMaterial(GlassMaterial.Overlay, 28.dp), containerColor = Color.Transparent,
        title = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("系统公告", color = tokens.accentPrimary, fontSize = 12.sp)
            Text(message.title, color = tokens.textPrimary, fontWeight = FontWeight.Bold)
        } },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(message.publishedAt, color = tokens.textTertiary, fontSize = 12.sp)
                Text(message.contentHtml, color = tokens.textPrimary, fontSize = 14.sp, lineHeight = 23.sp)
                AnnouncementOptions(message, state, onRespond)
            }
        }, confirmButton = { TextButton(onClick = onDetails, enabled = !state.sending) { Text("查看详情", color = tokens.accentPrimary) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.sending) { Text("稍后", color = tokens.textSecondary) } })
}
