package cn.qzxy.hotwater.ui

import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.FeedbackOption
import cn.qzxy.hotwater.FeedbackPreferences
import cn.qzxy.hotwater.designsystem.component.GlassButton
import cn.qzxy.hotwater.designsystem.motion.performGlassHapticFeedback
import cn.qzxy.hotwater.designsystem.theme.*

@Composable
internal fun FeedbackSettingsPanel() {
    val context = LocalContext.current
    val tokens = LocalGlassTokens.current
    val haptic = LocalHapticFeedback.current
    val preferences = remember(context) { FeedbackPreferences.preferences(context) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val options = remember(revision) { FeedbackOption.entries.associateWith { FeedbackPreferences.enabled(context, it) } }
    CollapsingGlassCard(header = {
        Text("振动与通知", color = tokens.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }) {
        val labels = listOf(
            Triple(FeedbackOption.HAPTICS, "振动反馈", "按钮、切换和操作结果的触感反馈"),
            Triple(FeedbackOption.OPEN_NOTIFICATION, "开水通知", "热水开启操作的结果提醒"),
            Triple(FeedbackOption.CLOSE_NOTIFICATION, "关水通知", "关闭热水和自动结束用水的提醒"),
            Triple(FeedbackOption.LIVE_NOTIFICATION, "用水计时通知", "用水期间显示计时与关水入口"),
            Triple(FeedbackOption.PROMOTED_NOTIFICATION, "实况窗提醒", "在支持的系统上将计时显示为实况窗"),
        )
        labels.forEach { (option, title, description) ->
            val checked = options.getValue(option)
            val enabled = option != FeedbackOption.PROMOTED_NOTIFICATION || options.getValue(FeedbackOption.LIVE_NOTIFICATION)
            Row(Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch,
                onValueChange = { next ->
                    FeedbackPreferences.set(context, option, next)
                    haptic.performGlassHapticFeedback(if (next) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = if (enabled) tokens.textPrimary else tokens.textTertiary, fontSize = 15.sp)
                    Text(description, color = tokens.textSecondary, fontSize = 12.sp)
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = checked, onCheckedChange = null, enabled = enabled,
                    colors = SwitchDefaults.colors(checkedTrackColor = tokens.accentPrimary,
                        checkedThumbColor = tokens.accentButtonText))
            }
        }
        Text("通知还需系统授权；关闭计时通知后，后台用水状态将在下次打开应用时同步。",
            color = tokens.textSecondary, fontSize = 12.sp)
        GlassButton(text = "系统通知设置", onClick = {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }, modifier = Modifier.fillMaxWidth())
    }
}
