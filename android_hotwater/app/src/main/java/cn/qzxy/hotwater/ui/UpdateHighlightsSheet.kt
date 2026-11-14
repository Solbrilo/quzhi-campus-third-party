package cn.qzxy.hotwater.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.BuildConfig
import cn.qzxy.hotwater.R
import cn.qzxy.hotwater.designsystem.component.GlassButton
import cn.qzxy.hotwater.designsystem.theme.GlassMaterial
import cn.qzxy.hotwater.designsystem.theme.LocalGlassEnvironment
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.glassMaterial
import kotlinx.coroutines.launch

@Composable
internal fun UpdateHighlightsSheet(onDismiss: () -> Unit) {
    val tokens = LocalGlassTokens.current
    val motion = LocalGlassEnvironment.current.motionEnabled
    val pager = rememberPagerState { 4 }
    val scope = rememberCoroutineScope()
    HalfPageSheet("这一版，更顺手", onDismiss) { close ->
        Text("沐芳 · ${BuildConfig.VERSION_NAME}", color = tokens.textSecondary, fontSize = 13.sp)
        cn.qzxy.hotwater.ui.navigation.IntentAwarePager(pager,
            modifier = Modifier.weight(1f).fillMaxWidth(), pageSpacing = 18.dp) { page ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .glassMaterial(GlassMaterial.Panel, 26.dp).padding(if (page == 0) 18.dp else 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (page == 0) {
                    ThemeHighlightContent()
                } else {
                    val title = when (page) { 1 -> "自定义主题"; 2 -> "小组件"; else -> "充值页面" }
                    Text(title, color = tokens.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(when (page) { 1 -> R.drawable.update_custom; 2 -> R.drawable.update_widgets; else -> R.drawable.update_recharge }),
                            "$title 应用界面", Modifier.height(220.dp).aspectRatio(720f / 1600f).clip(RoundedCornerShape(12.dp)))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(when (page) {
                                1 -> "把喜欢的图片带进校园。拖动、缩放，实时调节模糊；背景取色，让每处细节协调。"
                                2 -> "轻触桌面，即可开关热水。余额与四色指示灯一眼可见，安静刷新。"
                                else -> "半屏选金额，直接拉起支付宝。充值完成后，同步查看余额。"
                            }, color = tokens.textSecondary, fontSize = 14.sp, lineHeight = 23.sp)
                            Text(when (page) { 1 -> "设置 → 主题"; 2 -> "桌面 → 添加小组件"; else -> "用水 → 充值" },
                                color = tokens.accentPrimary, fontSize = 12.sp, lineHeight = 20.sp)
                        }
                    }

                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            repeat(4) { index ->
                Box(Modifier.padding(horizontal = 4.dp).size(if (pager.currentPage == index) 8.dp else 5.dp)
                    .background(if (pager.currentPage == index) tokens.accentPrimary else tokens.textTertiary.copy(alpha = .35f), CircleShape))
            }
            Spacer(Modifier.width(10.dp))
            AnimatedContent(pager.currentPage, label = "update_feature_count") { page ->
                Text("${page + 1} / 4", color = tokens.textTertiary, fontSize = 12.sp)
            }
        }
        GlassButton(if (pager.currentPage == 3) "太棒啦！" else "下一项", {
            if (pager.currentPage == 3) close {} else scope.launch {
                if (motion) pager.animateScrollToPage(pager.currentPage + 1) else pager.scrollToPage(pager.currentPage + 1)
            }
        }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ThemeHighlightContent() {
    val tokens = LocalGlassTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("新主题", color = tokens.textPrimary,
            fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                R.drawable.update_theme_tide to "潮汐",
                R.drawable.update_theme_ember to "余晖",
                R.drawable.update_theme_mono to "墨序",
            ).forEach { (image, name) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val shape = RoundedCornerShape(9.dp)
                    Image(painterResource(image), "$name 主题应用界面",
                        modifier = Modifier.height(140.dp).aspectRatio(720f / 1600f)
                            .clip(shape).border(1.dp, tokens.textTertiary.copy(alpha = .2f), shape))
                    Text(name, color = tokens.textSecondary, fontSize = 12.sp)
                }
            }
        }
        Text("七款预设，也能自定义背景、颜色、圆角与开关动画。完整手机预览，边改边看。",
            color = tokens.textSecondary, fontSize = 14.sp, lineHeight = 22.sp)
        Text("设置 → 主题 → 自定义", color = tokens.accentPrimary, fontSize = 12.sp, lineHeight = 20.sp)
    }
}
