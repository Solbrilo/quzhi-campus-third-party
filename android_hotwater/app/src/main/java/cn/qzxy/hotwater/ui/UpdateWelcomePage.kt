package cn.qzxy.hotwater.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.designsystem.component.GlassButton
import cn.qzxy.hotwater.designsystem.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdateWelcomePage(onContinue: () -> Unit) {
    var showHighlights by rememberSaveable { mutableStateOf(false) }
    OnboardingQuotePage(onNext = { showHighlights = true })
    BackHandler { /* Finish the introduction using its explicit action. */ }
    if (!showHighlights) return
    val tokens = LocalGlassTokens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden })
    ModalBottomSheet(
        onDismissRequest = {}, sheetState = sheetState,
        containerColor = tokens.fallbackSurface,
        contentColor = tokens.textPrimary,
        scrimColor = tokens.meshBase.copy(alpha = 0.48f),
        dragHandle = null,
    ) {
        Column(
            Modifier.fillMaxWidth().height((LocalConfiguration.current.screenHeightDp * 0.52f).dp)
                .testTag("update_welcome_sheet").padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("焕新体验 · v3.1.1", color = tokens.accentPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("熟悉的热水，全新的质感", color = tokens.textPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                WelcomeFeature(Icons.Outlined.AutoAwesome, "灵动液态玻璃", "按住底栏，透镜轻盈浮起；随手滑动，自由切换页面。")
                WelcomeFeature(Icons.Outlined.Palette, "四套全新主题", "澄空、青屿、暮紫、暖砂，搭配自然过渡的深浅色模式。")
                Text("也让设备状态更准确、小组件更可靠。", color = tokens.textSecondary, fontSize = 13.sp)
            }
            GlassButton("太棒了", onContinue, Modifier.fillMaxWidth().testTag("update_welcome_continue"))
        }
    }
}

@Composable
private fun WelcomeFeature(icon: ImageVector, title: String, description: String) {
    val tokens = LocalGlassTokens.current
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(46.dp).glassMaterial(GlassMaterial.Control, 16.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tokens.accentPrimary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = tokens.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(description, color = tokens.textSecondary, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}
