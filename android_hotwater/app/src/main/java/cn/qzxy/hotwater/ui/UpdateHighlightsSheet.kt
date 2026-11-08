package cn.qzxy.hotwater.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.BuildConfig
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
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    HalfPageSheet("这一版，更顺手", onDismiss) { close ->
        Text("趣智校园热水 · ${BuildConfig.VERSION_NAME}", color = tokens.textSecondary, fontSize = 13.sp)
        HorizontalPager(pager, modifier = Modifier.weight(1f).fillMaxWidth(), pageSpacing = 18.dp) { page ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .glassMaterial(GlassMaterial.Panel, 26.dp).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(58.dp).glassMaterial(GlassMaterial.EmbeddedControl, 20.dp), contentAlignment = Alignment.Center) {
                    Icon(when (page) { 0 -> Icons.Outlined.Palette; 1 -> Icons.Outlined.AccountBalanceWallet; else -> Icons.Outlined.Forum },
                        null, Modifier.size(30.dp), tint = tokens.accentPrimary)
                }
                Text(when (page) { 0 -> "让主题，合你心意"; 1 -> "账户与充值，更近一步"; else -> "新消息，一目了然" },
                    color = tokens.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 33.sp)
                Text(when (page) {
                    0 -> "完整手机预览始终可见。选择背景图片，直观调节颜色、圆角与开关动画，边改边看。"
                    1 -> "半屏选金额，直接拉起支付宝。设置顶部查看账户、学校，也能修改密码或更换手机号。"
                    else -> "校园消息与系统公告分别筛选。公告可直接选择或填写回复；版本、配置和项目支持集中在关于应用。"
                }, color = tokens.textSecondary, fontSize = 15.sp, lineHeight = 25.sp)
                Text(when (page) { 0 -> "设置 → 主题 → 自定义"; 1 -> "主页 → 充值 · 设置 → 账户"; else -> "主页右上角 → 消息" },
                    color = tokens.accentPrimary, fontSize = 12.sp, lineHeight = 20.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { index ->
                Box(Modifier.padding(horizontal = 4.dp).size(if (pager.currentPage == index) 8.dp else 5.dp)
                    .background(if (pager.currentPage == index) tokens.accentPrimary else tokens.textTertiary.copy(alpha = .35f), CircleShape))
            }
            Spacer(Modifier.width(10.dp))
            AnimatedContent(pager.currentPage, label = "update_feature_count") { page ->
                Text("${page + 1} / 3", color = tokens.textTertiary, fontSize = 12.sp)
            }
        }
        GlassButton(if (pager.currentPage == 2) "开始使用" else "下一项", {
            if (pager.currentPage == 2) close {} else scope.launch {
                if (motion) pager.animateScrollToPage(pager.currentPage + 1) else pager.scrollToPage(pager.currentPage + 1)
            }
        }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
    }
}
