package cn.qzxy.hotwater.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.qzxy.hotwater.MainViewModel
import cn.qzxy.hotwater.designsystem.theme.GlassAccent
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.glassConcave
import cn.qzxy.hotwater.designsystem.theme.glassConvex

enum class AppPage {
    OPERATIONS,
    CONFIG,
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun HotWaterApp(
    viewModel: MainViewModel,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    var currentPage by remember { mutableStateOf(AppPage.OPERATIONS) }

    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        // 顶部标题
        Text(
            text = "趣智校园热水",
            color = tokens.textPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "一键控制你的校园热水",
            color = tokens.textSecondary,
            fontSize = 14.sp
        )

        // 页面内容
        AnimatedContent(
            targetState = currentPage,
            modifier = Modifier
                .weight(1f)
                .padding(top = 18.dp),
            transitionSpec = {
                val direction = if (targetState == AppPage.OPERATIONS) -1 else 1
                slideInHorizontally(
                    animationSpec = tween(280),
                    initialOffsetX = { it * direction }
                ) + fadeIn(tween(280)) togetherWith
                    slideOutHorizontally(
                        animationSpec = tween(220),
                        targetOffsetX = { -(it * direction * 0.16f).toInt() }
                    ) + fadeOut(tween(220))
            },
            label = "page_transition"
        ) { page ->
            when (page) {
                AppPage.OPERATIONS -> OperationsPage(
                    snapshot = snapshot,
                    busy = busy,
                    statusMessage = statusMessage,
                    history = history,
                    onToggle = { viewModel.toggle() },
                    onRefreshBalance = { viewModel.refreshBalance() },
                )
                AppPage.CONFIG -> ConfigPage(
                    config = config,
                    busy = busy,
                    onSave = { viewModel.saveConfig(it) },
                    onExport = onExportConfig,
                    onImport = onImportConfig,
                )
            }
        }

        // 底部 Tab 栏
        BottomDock(
            currentPage = currentPage,
            onPageSelected = { currentPage = it }
        )
    }
}

@Composable
private fun BottomDock(
    currentPage: AppPage,
    onPageSelected: (AppPage) -> Unit,
) {
    val tokens = LocalGlassTokens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .glassConvex(cornerRadius = 28.dp, tokens = tokens)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TabButton(
            text = "操作页",
            isSelected = currentPage == AppPage.OPERATIONS,
            onClick = { onPageSelected(AppPage.OPERATIONS) },
            modifier = Modifier.weight(1f)
        )
        TabButton(
            text = "配置页",
            isSelected = currentPage == AppPage.CONFIG,
            onClick = { onPageSelected(AppPage.CONFIG) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalGlassTokens.current

    Box(
        modifier = modifier
            .height(48.dp)
            .then(
                if (isSelected) Modifier.glassConcave(cornerRadius = 22.dp, tokens = tokens)
                else Modifier
            )
            .clip(RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) GlassAccent.primary else tokens.textSecondary,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
