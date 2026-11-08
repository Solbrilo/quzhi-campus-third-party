package cn.qzxy.hotwater.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import cn.qzxy.hotwater.ServerMessage
import cn.qzxy.hotwater.ServerMessagesState
import cn.qzxy.hotwater.designsystem.component.GlassButton
import cn.qzxy.hotwater.designsystem.component.GlassPressable
import cn.qzxy.hotwater.designsystem.theme.GlassMaterial
import cn.qzxy.hotwater.designsystem.theme.LocalGlassEnvironment
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.glassMaterial

/** Native text rendering: remote HTML never executes script or reads app credentials. */
internal fun messagePlainText(html: String): String = HtmlCompat.fromHtml(
    html.replace(Regex("<(head|script|style)\\b[^>]*>.*?</\\1>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), ""),
    HtmlCompat.FROM_HTML_MODE_LEGACY,
).toString().trim()

@Composable
internal fun SubpageHeader(title: String, onBack: () -> Unit) {
    val tokens = LocalGlassTokens.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GlassPressable(onClick = onBack, cornerRadius = 24.dp,
            modifier = Modifier.size(48.dp).semantics { contentDescription = "返回" }) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = tokens.textPrimary)
        }
        Spacer(Modifier.width(12.dp))
        Text(title, color = tokens.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun MessagesPage(
    state: ServerMessagesState,
    selectedRevision: String?,
    onSelect: (ServerMessage) -> Unit,
    onRefresh: () -> Unit,
    onMarkAllRead: () -> Unit,
    onLogin: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    responseState: cn.qzxy.hotwater.AnnouncementResponseState = cn.qzxy.hotwater.AnnouncementResponseState(),
    onRespond: (ServerMessage, String, String) -> Unit = { _, _, _ -> },
) {
    val tokens = LocalGlassTokens.current
    val selected = state.messages.firstOrNull { it.revision == selectedRevision }
    val motionEnabled = LocalGlassEnvironment.current.motionEnabled
    val listState = rememberLazyListState()
    var filter by rememberSaveable { mutableStateOf("全部消息") }
    var choosingFilter by remember { mutableStateOf(false) }
    val filtered = state.messages.filter { when (filter) {
        "校园消息" -> it.systemNotice == null
        "系统公告" -> it.systemNotice != null
        else -> true
    } }
    AnimatedContent(
        targetState = selected,
        contentKey = { it?.revision },
        modifier = modifier,
        transitionSpec = { subpageTransition(targetState != null, motionEnabled) },
        label = "message_detail_transition",
    ) { selected ->
        Column(Modifier.fillMaxSize().background(tokens.meshBase),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SubpageHeader(if (selected == null) "消息" else "消息详情", onBack)
            if (selected != null) {
                val body = remember(selected) {
                    if (selected.systemNotice != null) selected.contentHtml else
                        messageBodyWithoutRepeatedHeader(messagePlainText(selected.contentHtml), selected.title, selected.publishedAt)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Column(Modifier.fillMaxWidth().glassMaterial(GlassMaterial.Panel, 24.dp).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(selected.title, color = tokens.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(selected.publishedAt, color = tokens.textTertiary, fontSize = 12.sp)
                        SelectionContainer {
                            Text(body.ifBlank { "此消息暂无正文。" }, color = tokens.textPrimary,
                                fontSize = 15.sp, lineHeight = 25.sp)
                        }
                        AnnouncementOptions(selected, responseState, onRespond)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        GlassPressable({ choosingFilter = true }, cornerRadius = 20.dp, contentPadding = PaddingValues(12.dp)) {
                            Text("$filter ▾", color = tokens.accentPrimary, fontSize = 14.sp)
                        }
                        FilterMenu(choosingFilter, { choosingFilter = false },
                            listOf("全部消息", "校园消息", "系统公告"), { it }, { filter = it })
                    }
                    GlassPressable(onClick = onMarkAllRead, enabled = state.hasUnread,
                        material = GlassMaterial.EmbeddedControl, cornerRadius = 24.dp,
                        modifier = Modifier.height(48.dp)
                            .semantics { contentDescription = "一键标记全部消息已读" }) {
                        Text("全部已读", color = if (state.hasUnread) tokens.accentPrimary else tokens.textTertiary,
                            fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    GlassPressable(onClick = onRefresh, enabled = !state.loading, cornerRadius = 24.dp,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "刷新消息" }) {
                        Icon(Icons.Outlined.Refresh, null, tint = tokens.accentPrimary)
                    }
                }
                state.error?.let { Text(it, color = tokens.accentWarning, fontSize = 13.sp) }
                if (!state.loggedIn && filter != "系统公告") {
                    Text("登录后可查看校园消息。", color = tokens.textSecondary)
                    GlassButton(text = "登录", onClick = onLogin, modifier = Modifier.fillMaxWidth())
                }
                if (state.loading) Text("正在同步消息…", color = tokens.textTertiary, fontSize = 12.sp)
                if (!state.loading && filtered.isEmpty()) {
                    Text("暂无消息", color = tokens.textSecondary)
                }
                LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(filtered, key = { it.revision }) { message ->
                        val summary = remember(message) { if (message.systemNotice != null) message.summaryHtml else messagePlainText(message.summaryHtml) }
                        GlassPressable(onClick = { onSelect(message) }, material = GlassMaterial.Panel,
                            cornerRadius = 24.dp, contentPadding = PaddingValues(18.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(message.title, color = tokens.textPrimary, fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    if (message.revision !in state.read) Box(Modifier.padding(start = 10.dp)
                                        .size(8.dp).clip(CircleShape).background(tokens.accentError)
                                        .semantics { contentDescription = "未读" })
                                }
                                Text("${if (message.systemNotice != null) "系统公告" else "校园消息"} · ${message.publishedAt}", color = tokens.textTertiary, fontSize = 12.sp)
                                if (summary.isNotBlank()) Text(summary, color = tokens.textSecondary,
                                    fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
