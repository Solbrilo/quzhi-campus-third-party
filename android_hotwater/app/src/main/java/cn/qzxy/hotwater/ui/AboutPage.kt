package cn.qzxy.hotwater.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.AppUpdateCheckResult
import cn.qzxy.hotwater.AppUpdateInstallResult
import cn.qzxy.hotwater.AppReleaseNote
import cn.qzxy.hotwater.BuildConfig
import cn.qzxy.hotwater.FeedbackCategory
import cn.qzxy.hotwater.FeedbackClient
import cn.qzxy.hotwater.FeedbackHistoryEntry
import cn.qzxy.hotwater.FeedbackHistoryStore
import cn.qzxy.hotwater.FeedbackSubmitResult
import cn.qzxy.hotwater.R
import cn.qzxy.hotwater.designsystem.component.GlassButton
import cn.qzxy.hotwater.designsystem.component.GlassInput
import cn.qzxy.hotwater.designsystem.component.GlassPressable
import cn.qzxy.hotwater.designsystem.theme.DEFAULT_GLASS_INTENSITY
import cn.qzxy.hotwater.designsystem.theme.GlassMaterial
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.ThemePalette
import cn.qzxy.hotwater.designsystem.theme.ThemeMode
import cn.qzxy.hotwater.designsystem.theme.glassMaterial
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/** The in-app destinations opened from the home screen's overflow button. */
internal enum class MorePageDestination(val title: String) {
    OVERVIEW("设置"),
    FEEDBACK("问题反馈"),
    PRIVACY("隐私政策"),
    CREATORS("制作者"),
    OPEN_SOURCE("开源支持"),
    THEME("主题"),
}

private const val ProjectRepositoryUrl =
    "https://gitee.com/Solbrilo/quzhi-campus-third-party"

@Composable
internal fun MorePage(
    destination: MorePageDestination,
    motionEnabled: Boolean,
    checkResult: AppUpdateCheckResult?,
    checking: Boolean,
    installing: Boolean,
    releaseNotes: List<AppReleaseNote>?,
    releaseNotesLoading: Boolean,
    releaseNotesError: String?,
    themePalette: ThemePalette = ThemePalette.SKY,
    onThemePaletteChange: (ThemePalette) -> Unit = {},
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    glassIntensity: Float,
    onGlassIntensityChange: (Float) -> Unit,
    onGlassIntensityChangeFinished: () -> Unit,
    onResetGlassIntensity: () -> Unit,
    onCheckForUpdate: () -> Unit,
    onLoadReleaseNotes: () -> Unit,
    onNavigate: (MorePageDestination) -> Unit,
    onClearAllUserData: () -> Unit,
    onOpenExternal: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
    loggedIn: Boolean = false,
    accountBusy: Boolean = false,
    onAccountAction: () -> Unit = {},
    onOpenConfig: () -> Unit = {},
    accountName: String = "",
    schoolName: String = "",
    onAccountSettings: (Boolean) -> Unit = {},
) {
    val pageStateHolder = rememberSaveableStateHolder()
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showReleaseNotes by rememberSaveable { mutableStateOf(false) }
    var releaseNotesDialogSession by rememberSaveable { mutableStateOf(0) }
    var showClearAllDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tokens = LocalGlassTokens.current
            if (!embedded || destination != MorePageDestination.OVERVIEW) GlassPressable(
                onClick = onBack,
                material = GlassMaterial.Control,
                cornerRadius = 18.dp,
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = if (destination == MorePageDestination.OVERVIEW) {
                            "返回用水页面"
                        } else {
                            "返回设置页面"
                        }
                    },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null,
                    tint = tokens.textPrimary,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = destination.title,
                color = tokens.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        AnimatedContent(
            targetState = destination,
            modifier = Modifier.weight(1f),
            transitionSpec = { subpageTransition(targetState != MorePageDestination.OVERVIEW, motionEnabled) },
            label = "more_page_content",
        ) { page ->
            pageStateHolder.SaveableStateProvider(page.name) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (page == MorePageDestination.THEME) Modifier else Modifier.verticalScroll(rememberScrollState())),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    when (page) {
                        MorePageDestination.OVERVIEW -> MoreOverviewPage(
                            checkResult = checkResult,
                            checking = checking,
                            installing = installing,
                            onOpenReleaseNotes = {
                                showReleaseNotes = true
                                releaseNotesDialogSession += 1
                                onLoadReleaseNotes()
                            },
                            themePalette = themePalette, onThemePaletteChange = onThemePaletteChange,
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            glassIntensity = glassIntensity,
                            onGlassIntensityChange = onGlassIntensityChange,
                            onGlassIntensityChangeFinished = onGlassIntensityChangeFinished,
                            onResetGlassIntensity = onResetGlassIntensity,
                            onCheckForUpdate = onCheckForUpdate,
                            onNavigate = onNavigate,
                            onRequestClearAllData = { showClearAllDialog = true },
                            onOpenExternal = onOpenExternal,
                            loggedIn = loggedIn,
                            accountBusy = accountBusy,
                            onAccountAction = onAccountAction,
                            onOpenConfig = onOpenConfig,
                            onAbout = { showAbout = true }, accountName = accountName, schoolName = schoolName,
                            onAccountSettings = onAccountSettings,
                        )

                        MorePageDestination.FEEDBACK -> FeedbackPage()
                        MorePageDestination.PRIVACY -> PrivacyPolicyPage()
                        MorePageDestination.CREATORS -> CreatorsPage(onOpenExternal = onOpenExternal)
                        MorePageDestination.OPEN_SOURCE -> OpenSourcePage(onOpenExternal = onOpenExternal)
                        MorePageDestination.THEME -> ThemePage(themePalette, onThemePaletteChange)
                    }
                    Spacer(modifier = Modifier.height(if (embedded) bottomDockClearance() else 12.dp))
                }
            }
        }
    }

    if (showAbout) {
        HalfPageSheet(null, { showAbout = false }) { close ->
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AboutApplicationContent(checkResult, checking, installing,
                    onOpenReleaseNotes = { showReleaseNotes = true; releaseNotesDialogSession += 1; onLoadReleaseNotes() },
                    onCheckForUpdate = onCheckForUpdate, onOpenConfig = { close(onOpenConfig) },
                    onNavigate = { next -> close { onNavigate(next) } }, onOpenExternal = onOpenExternal)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
    if (showReleaseNotes) {
        ReleaseNotesDialog(
            releases = releaseNotes,
            loading = releaseNotesLoading,
            errorMessage = releaseNotesError,
            sessionKey = releaseNotesDialogSession,
            onRetry = onLoadReleaseNotes,
            onDismiss = { showReleaseNotes = false },
        )
    }

    if (showClearAllDialog) {
        val tokens = LocalGlassTokens.current
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            modifier = Modifier.glassMaterial(GlassMaterial.Overlay, 28.dp),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            titleContentColor = tokens.textPrimary,
            textContentColor = tokens.textSecondary,
            title = { Text(text = "清空所有信息？", color = tokens.textPrimary) },
            text = {
                Text(
                    text = "将删除本机保存的账号和设备配置、账单与操作记录、反馈记录、显示设置及引导状态。此操作无法撤销，已经提交到反馈服务的内容不受影响。",
                    color = tokens.textSecondary,
                )
            },
            confirmButton = {
                GlassPressable(
                    onClick = {
                        showClearAllDialog = false
                        onClearAllUserData()
                    },
                    material = GlassMaterial.Control,
                    cornerRadius = 16.dp,
                    modifier = Modifier.height(40.dp),
                ) {
                    Text(
                        text = "确认清空",
                        color = tokens.accentWarning,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                }
            },
            dismissButton = {
                GlassPressable(
                    onClick = { showClearAllDialog = false },
                    material = GlassMaterial.EmbeddedControl,
                    cornerRadius = 16.dp,
                    modifier = Modifier.height(40.dp),
                ) {
                    Text(
                        text = "取消",
                        color = tokens.textSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                }
            },
        )
    }
}

@Composable
private fun MoreOverviewPage(
    checkResult: AppUpdateCheckResult?,
    checking: Boolean,
    installing: Boolean,
    onOpenReleaseNotes: () -> Unit,
    themePalette: ThemePalette = ThemePalette.SKY,
    onThemePaletteChange: (ThemePalette) -> Unit = {},
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    glassIntensity: Float,
    onGlassIntensityChange: (Float) -> Unit,
    onGlassIntensityChangeFinished: () -> Unit,
    onResetGlassIntensity: () -> Unit,
    onCheckForUpdate: () -> Unit,
    onNavigate: (MorePageDestination) -> Unit,
    onRequestClearAllData: () -> Unit,
    onOpenExternal: (String) -> Unit,
    loggedIn: Boolean,
    accountBusy: Boolean,
    onAccountAction: () -> Unit,
    onOpenConfig: () -> Unit,
    onAbout: () -> Unit,
    accountName: String,
    schoolName: String,
    onAccountSettings: (Boolean) -> Unit,
) {
    val tokens = LocalGlassTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.fillMaxWidth().glassMaterial(GlassMaterial.Panel, 28.dp).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (loggedIn) accountName.ifBlank { "校园账户" } else "尚未登录", color = tokens.textPrimary,
                        fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(if (loggedIn) schoolName.ifBlank { "学校信息暂不可用" } else "登录后同步校园信息",
                        color = tokens.textSecondary, fontSize = 13.sp)
                }
                GlassPressable(onAccountAction, enabled = !accountBusy, cornerRadius = 18.dp,
                    modifier = Modifier.height(48.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Text(if (loggedIn) "退出登录" else "登录", color = tokens.accentPrimary, fontSize = 13.sp)
                }
            }
            if (loggedIn) {
                MoreDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassPressable({ onAccountSettings(false) }, enabled = !accountBusy, cornerRadius = 16.dp,
                        modifier = Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
                        Text("修改密码", color = tokens.accentPrimary, fontSize = 13.sp)
                    }
                    GlassPressable({ onAccountSettings(true) }, enabled = !accountBusy, cornerRadius = 16.dp,
                        modifier = Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
                        Text("更换手机号", color = tokens.accentPrimary, fontSize = 13.sp)
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassMaterial(GlassMaterial.Panel, 28.dp)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                    tint = tokens.accentPrimary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "显示与外观",
                    color = tokens.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            MoreActionRow(MoreAction("主题", themePalette.label, Icons.Outlined.Palette,
                onClick = { onNavigate(MorePageDestination.THEME) }))
            MoreDivider()
            GlassIntensityPanel(
                glassIntensity = glassIntensity,
                onGlassIntensityChange = onGlassIntensityChange,
                onGlassIntensityChangeFinished = onGlassIntensityChangeFinished,
                onResetGlassIntensity = onResetGlassIntensity,
                embedded = true,
            )
            MoreDivider()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "深浅色模式",
                    color = tokens.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "选择应用显示模式",
                    color = tokens.textSecondary,
                    fontSize = 13.sp,
                )
                ThemeModeSelector(
                    selected = themeMode,
                    onSelected = onThemeModeChange,
                )
            }
        }

        FeedbackSettingsPanel()

        MoreActionGroup(
            actions = listOf(
                MoreAction(
                    title = "问题反馈",
                    summary = "提交建议、查看已提交记录",
                    icon = Icons.Outlined.Feedback,
                    onClick = { onNavigate(MorePageDestination.FEEDBACK) },
                ),
                MoreAction(
                    title = "隐私政策",
                    summary = "了解信息处理方式",
                    icon = Icons.Outlined.Description,
                    onClick = { onNavigate(MorePageDestination.PRIVACY) },
                ),
                MoreAction(
                    title = "清空所有信息",
                    summary = "删除本机保存的全部用户数据",
                    icon = Icons.Outlined.DeleteSweep,
                    onClick = onRequestClearAllData,
                ),
                MoreAction("关于应用", "版本、配置与项目支持", Icons.Outlined.Info, onClick = onAbout),
            ),
        )


    }
}


@Composable
private fun AboutApplicationContent(checkResult: AppUpdateCheckResult?, checking: Boolean, installing: Boolean,
    onOpenReleaseNotes: () -> Unit, onCheckForUpdate: () -> Unit, onOpenConfig: () -> Unit,
    onNavigate: (MorePageDestination) -> Unit, onOpenExternal: (String) -> Unit) {
    val tokens = LocalGlassTokens.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(painterResource(R.mipmap.ic_launcher), "应用图标", Modifier.size(78.dp).clip(RoundedCornerShape(24.dp)))
        Text("趣智校园热水", color = tokens.textPrimary, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("简洁、轻盈的校园用水体验", color = tokens.textSecondary, fontSize = 12.sp)
    }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassMaterial(GlassMaterial.Panel, 28.dp)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = tokens.accentPrimary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "应用信息",
                    color = tokens.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "当前版本 v${BuildConfig.VERSION_NAME}",
                color = tokens.textSecondary,
                fontSize = 14.sp,
            )
            UpdateStatusText(checkResult = checkResult, installResult = null)
            GlassPressable(
                onClick = onOpenReleaseNotes,
                material = GlassMaterial.EmbeddedControl,
                cornerRadius = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .semantics { contentDescription = "查看更新日志" },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = tokens.accentPrimary,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "更新日志",
                        color = tokens.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = tokens.textTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            GlassButton(
                text = "检查更新",
                onClick = onCheckForUpdate,
                modifier = Modifier.fillMaxWidth(),
                enabled = !installing,
                isLoading = checking,
            )
            MoreActionRow(MoreAction(
                title = "查看配置", summary = "查看、编辑或导入导出账号与设备配置",
                icon = Icons.Outlined.Settings, onClick = onOpenConfig,
            ))
        }

        MoreActionGroup(
            actions = listOf(
                MoreAction(
                    title = "制作者",
                    summary = "查看项目参与者",
                    icon = Icons.Outlined.Group,
                    onClick = { onNavigate(MorePageDestination.CREATORS) },
                ),
                MoreAction(
                    title = "开源支持",
                    summary = "项目、开发套件与许可",
                    icon = Icons.Outlined.Code,
                    onClick = { onNavigate(MorePageDestination.OPEN_SOURCE) },
                ),
                MoreAction(
                    title = "项目仓库",
                    summary = "在 Gitee 查看源码与版本记录",
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    external = true,
                    onClick = { onOpenExternal(ProjectRepositoryUrl) },
                ),
                MoreAction(
                    title = "项目网站",
                    summary = "了解趣智校园热水项目",
                    icon = Icons.Outlined.Language,
                    drawable = R.drawable.ic_nebularive,
                    external = true,
                    onClick = { onOpenExternal("https://nebularive.top/project-qzxy-hotwater.php") },
                ),
            ),
        )
}

@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 288.dp
        val options = listOf(
            ThemeMode.DARK to "深色",
            ThemeMode.LIGHT to "浅色",
            ThemeMode.SYSTEM to "跟随系统",
        )
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (mode, label) ->
                    ThemeModeOption(
                        mode = mode,
                        label = label,
                        selected = selected == mode,
                        onSelected = onSelected,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (mode, label) ->
                    ThemeModeOption(
                        mode = mode,
                        label = label,
                        selected = selected == mode,
                        onSelected = onSelected,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeModeOption(
    mode: ThemeMode,
    label: String,
    selected: Boolean,
    onSelected: (ThemeMode) -> Unit,
    modifier: Modifier,
) {
    val tokens = LocalGlassTokens.current
    val icon = when (mode) {
        ThemeMode.DARK -> Icons.Outlined.DarkMode
        ThemeMode.LIGHT -> Icons.Outlined.LightMode
        ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
    }
    GlassPressable(
        onClick = { onSelected(mode) },
        selected = selected,
        material = if (selected) GlassMaterial.Control else GlassMaterial.EmbeddedControl,
        cornerRadius = 16.dp,
        modifier = modifier.height(44.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) tokens.accentPrimary else tokens.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                color = if (selected) tokens.accentPrimary else tokens.textSecondary,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun GlassIntensityPanel(
    glassIntensity: Float,
    onGlassIntensityChange: (Float) -> Unit,
    onGlassIntensityChangeFinished: () -> Unit,
    onResetGlassIntensity: () -> Unit,
    embedded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalGlassTokens.current
    val normalizedIntensity = glassIntensity
        .takeIf { it.isFinite() }
        ?.coerceIn(0f, 1f)
        ?: DEFAULT_GLASS_INTENSITY
    val percentage = (normalizedIntensity * 100f).roundToInt()
    val percentageText = "$percentage%"
    val resetEnabled = abs(normalizedIntensity - DEFAULT_GLASS_INTENSITY) > 0.0001f
    val containerModifier = modifier
        .fillMaxWidth()
        .testTag(GlassIntensityPanelTag)
        .then(
            if (embedded) {
                Modifier
            } else {
                Modifier
                    .glassMaterial(GlassMaterial.Panel, 28.dp)
                    .padding(18.dp)
            },
        )

    Column(modifier = containerModifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "液态玻璃效果",
                color = tokens.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = percentageText,
                color = tokens.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.testTag(GlassIntensityValueTag),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Slider(
                value = normalizedIntensity,
                onValueChange = { onGlassIntensityChange(it.coerceIn(0f, 1f)) },
                onValueChangeFinished = onGlassIntensityChangeFinished,
                valueRange = 0f..1f,
                steps = 0,
                modifier = Modifier
                    .weight(1f)
                    .testTag(GlassIntensitySliderTag)
                    .semantics {
                        contentDescription = GlassIntensitySliderDescription
                        stateDescription = percentageText
                    },
            )
            GlassPressable(
                onClick = onResetGlassIntensity,
                enabled = resetEnabled,
                cornerRadius = 24.dp,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(GlassIntensityResetTag)
                    .semantics {
                        contentDescription = GlassIntensityResetDescription
                        stateDescription = if (resetEnabled) {
                            "可恢复为默认强度 75%"
                        } else {
                            "当前已是默认强度 75%"
                        }
                    },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Restore,
                    contentDescription = null,
                    tint = tokens.textPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

internal const val GlassIntensityPanelTag = "glass_intensity_panel"
internal const val GlassIntensitySliderTag = "glass_intensity_slider"
internal const val GlassIntensityValueTag = "glass_intensity_value"
internal const val GlassIntensityResetTag = "glass_intensity_reset"
internal const val GlassIntensitySliderDescription = "液态玻璃效果强度"
internal const val GlassIntensityResetDescription = "恢复默认液态玻璃强度"

private data class MoreAction(
    val title: String,
    val summary: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val external: Boolean = false,
    val drawable: Int? = null,
    val onClick: () -> Unit,
)

@Composable
private fun MoreActionGroup(actions: List<MoreAction>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassMaterial(GlassMaterial.Panel, 28.dp)
            .padding(vertical = 4.dp),
    ) {
        actions.forEachIndexed { index, action ->
            MoreActionRow(action)
            if (index < actions.lastIndex) MoreDivider(modifier = Modifier.padding(start = 64.dp))
        }
    }
}

@Composable
private fun MoreActionRow(action: MoreAction) {
    val tokens = LocalGlassTokens.current
    GlassPressable(
        onClick = action.onClick,
        material = null,
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics { contentDescription = action.title },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tokens.controlSurface),
                contentAlignment = Alignment.Center,
            ) {
                if (action.drawable != null) Image(painterResource(action.drawable), null, Modifier.size(25.dp)) else Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = tokens.accentPrimary,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = action.title,
                    color = tokens.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = action.summary,
                    color = tokens.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (action.external) {
                    Icons.AutoMirrored.Outlined.OpenInNew
                } else {
                    Icons.Outlined.ChevronRight
                },
                contentDescription = null,
                tint = tokens.textTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun MoreDivider(modifier: Modifier = Modifier) {
    val tokens = LocalGlassTokens.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(tokens.borderLo.copy(alpha = 0.62f)),
    )
}

@Composable
private fun FeedbackPage() {
    val tokens = LocalGlassTokens.current
    val appContext = LocalContext.current.applicationContext
    val feedbackClient = remember { FeedbackClient() }
    val feedbackHistoryStore = remember(appContext) { FeedbackHistoryStore(appContext) }
    val scope = rememberCoroutineScope()
    var feedbackCategory by rememberSaveable { mutableStateOf(FeedbackCategory.Bug) }
    var feedbackEmail by rememberSaveable { mutableStateOf("") }
    var feedbackMessage by rememberSaveable { mutableStateOf("") }
    var feedbackEmailError by rememberSaveable { mutableStateOf<String?>(null) }
    var feedbackError by rememberSaveable { mutableStateOf<String?>(null) }
    var feedbackStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var feedbackSucceeded by rememberSaveable { mutableStateOf(false) }
    var showFeedbackHistory by rememberSaveable { mutableStateOf(false) }
    var feedbackHistory by remember(feedbackHistoryStore) { mutableStateOf(feedbackHistoryStore.load()) }
    var submittingFeedback by remember { mutableStateOf(false) }

    val submitFeedback: () -> Unit = submit@{
        if (submittingFeedback) return@submit
        val normalizedMessage = feedbackMessage.trim()
        val normalizedEmail = feedbackEmail.trim()
        val messageValidationError = FeedbackClient.validationError(normalizedMessage)
        val emailValidationError = FeedbackClient.emailValidationError(normalizedEmail)
        if (messageValidationError != null || emailValidationError != null) {
            feedbackError = messageValidationError
            feedbackEmailError = emailValidationError
            feedbackStatus = null
            feedbackSucceeded = false
            return@submit
        }
        val submittedCategory = feedbackCategory
        submittingFeedback = true
        scope.launch {
            feedbackError = null
            feedbackEmailError = null
            feedbackStatus = null
            feedbackSucceeded = false
            try {
                when (val result = feedbackClient.submit(
                    appVersion = BuildConfig.VERSION_NAME,
                    category = submittedCategory,
                    email = normalizedEmail,
                    message = normalizedMessage,
                )) {
                    is FeedbackSubmitResult.Success -> {
                        val historySaved = try {
                            feedbackHistoryStore.save(
                                FeedbackHistoryEntry(
                                    submittedAtMillis = System.currentTimeMillis(),
                                    feedbackId = result.feedbackId,
                                    category = submittedCategory,
                                    email = normalizedEmail,
                                    message = normalizedMessage,
                                ),
                            )
                            feedbackHistory = feedbackHistoryStore.load()
                            true
                        } catch (_: Exception) {
                            false
                        }
                        if (feedbackMessage.trim() == normalizedMessage) feedbackMessage = ""
                        feedbackSucceeded = true
                        feedbackStatus = if (historySaved) {
                            "提交成功，反馈编号：${result.feedbackId}。已保存到历史反馈。"
                        } else {
                            "提交成功，反馈编号：${result.feedbackId}。本地历史未能保存。"
                        }
                    }

                    is FeedbackSubmitResult.RateLimited -> {
                        val waitHint = result.retryAfterSeconds?.let { "请等待 ${it} 秒后" } ?: "请稍后"
                        feedbackStatus = "提交过于频繁，${waitHint}手动重试。反馈内容已保留。"
                    }

                    is FeedbackSubmitResult.Rejected -> feedbackError = result.message
                    is FeedbackSubmitResult.Failed -> feedbackStatus = result.message
                }
            } finally {
                submittingFeedback = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassMaterial(GlassMaterial.Panel, 28.dp)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "告诉我们你的想法",
                    color = tokens.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "提交成功后可在本机查看反馈记录。",
                    color = tokens.textSecondary,
                    fontSize = 12.sp,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            GlassPressable(
                onClick = {
                    feedbackHistory = feedbackHistoryStore.load()
                    showFeedbackHistory = true
                },
                material = GlassMaterial.EmbeddedControl,
                cornerRadius = 16.dp,
                modifier = Modifier
                    .height(36.dp)
                    .semantics { contentDescription = "查看历史反馈" },
            ) {
                Text(
                    text = "历史反馈",
                    color = tokens.accentPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
        Text(text = "请选择类型", color = tokens.textSecondary, fontSize = 12.sp)
        FeedbackCategoryRow(
            categories = listOf(FeedbackCategory.Bug, FeedbackCategory.Suggestion),
            selected = feedbackCategory,
            enabled = !submittingFeedback,
            onSelected = {
                feedbackCategory = it
                feedbackStatus = null
                feedbackSucceeded = false
            },
        )
        FeedbackCategoryRow(
            categories = listOf(FeedbackCategory.Experience, FeedbackCategory.Other),
            selected = feedbackCategory,
            enabled = !submittingFeedback,
            onSelected = {
                feedbackCategory = it
                feedbackStatus = null
                feedbackSucceeded = false
            },
        )
        GlassInput(
            value = feedbackEmail,
            onValueChange = { value ->
                feedbackEmail = value
                feedbackEmailError = null
                feedbackStatus = null
                feedbackSucceeded = false
            },
            label = "联系邮箱",
            placeholder = "name@example.com",
            errorMessage = feedbackEmailError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        FeedbackMessageInput(
            value = feedbackMessage,
            onValueChange = { value ->
                if (FeedbackClient.countUnicodeCharacters(value) <= 2000) {
                    feedbackMessage = value
                    feedbackError = null
                    feedbackStatus = null
                    feedbackSucceeded = false
                }
            },
            errorMessage = feedbackError,
            enabled = !submittingFeedback,
        )
        Text(
            text = "${FeedbackClient.countUnicodeCharacters(feedbackMessage)}/2000",
            color = tokens.textTertiary,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.End),
        )
        Text(
            text = "联系邮箱仅用于跟进反馈。请勿在反馈内容中填写账号、手机号、学号、校园卡号、密码或 Token。",
            color = tokens.textSecondary,
            fontSize = 12.sp,
        )
        if (feedbackStatus != null) {
            Text(
                text = feedbackStatus.orEmpty(),
                color = if (feedbackSucceeded) tokens.accentSuccess else tokens.accentWarning,
                fontSize = 13.sp,
                modifier = Modifier.semantics {
                    liveRegion = if (feedbackSucceeded) LiveRegionMode.Polite else LiveRegionMode.Assertive
                    stateDescription = feedbackStatus.orEmpty()
                },
            )
        }
        GlassButton(
            text = "提交反馈",
            onClick = submitFeedback,
            modifier = Modifier.fillMaxWidth(),
            enabled = !submittingFeedback,
            isLoading = submittingFeedback,
        )
        }

    }

    if (showFeedbackHistory) {
        FeedbackHistoryDialog(entries = feedbackHistory, onDismiss = { showFeedbackHistory = false })
    }
}

@Composable
private fun FeedbackCategoryRow(
    categories: List<FeedbackCategory>,
    selected: FeedbackCategory,
    enabled: Boolean,
    onSelected: (FeedbackCategory) -> Unit,
) {
    val tokens = LocalGlassTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { category ->
            GlassPressable(
                onClick = { onSelected(category) },
                enabled = enabled,
                selected = category == selected,
                material = if (category == selected) GlassMaterial.Control else GlassMaterial.EmbeddedControl,
                cornerRadius = 16.dp,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
            ) {
                Text(
                    text = category.label,
                    color = if (category == selected) tokens.accentPrimary else tokens.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (category == selected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun FeedbackMessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    errorMessage: String?,
    enabled: Boolean,
) {
    val tokens = LocalGlassTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "反馈内容",
            color = if (errorMessage == null) tokens.textSecondary else tokens.accentError,
            fontSize = 12.sp,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 132.dp)
                .glassMaterial(GlassMaterial.Input, 16.dp)
                .padding(16.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp)
                    .semantics {
                        contentDescription = "反馈内容"
                        errorMessage?.let { error(it) }
                    },
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                    color = tokens.textPrimary,
                    fontSize = 15.sp,
                ),
                cursorBrush = SolidColor(tokens.accentPrimary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (value.isEmpty()) {
                            Text(
                                text = "请描述遇到的问题或建议（2-2000 字）",
                                color = tokens.textTertiary,
                                fontSize = 15.sp,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
        if (errorMessage != null) {
            Text(text = errorMessage, color = tokens.accentError, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FeedbackHistoryDialog(
    entries: List<FeedbackHistoryEntry>,
    onDismiss: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.glassMaterial(GlassMaterial.Overlay, 28.dp),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        titleContentColor = tokens.textPrimary,
        textContentColor = tokens.textSecondary,
        title = {
            Text(text = "历史反馈", color = tokens.textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            if (entries.isEmpty()) {
                Text(text = "还没有提交成功的反馈。", color = tokens.textSecondary, fontSize = 14.sp)
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    entries.forEach { entry ->
                        FeedbackHistoryItem(entry)
                    }
                }
            }
        },
        confirmButton = {
            GlassPressable(
                onClick = onDismiss,
                material = GlassMaterial.Control,
                cornerRadius = 16.dp,
                modifier = Modifier.height(40.dp),
            ) {
                Text(
                    text = "关闭",
                    color = tokens.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        },
    )
}

@Composable
private fun ReleaseNotesDialog(
    releases: List<AppReleaseNote>?,
    loading: Boolean,
    errorMessage: String?,
    sessionKey: Int,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.glassMaterial(GlassMaterial.Overlay, 28.dp),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        titleContentColor = tokens.textPrimary,
        textContentColor = tokens.textSecondary,
        title = {
            Text(text = "更新日志", color = tokens.textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            when {
                loading -> Text(
                    text = "正在获取版本更新记录…",
                    color = tokens.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )

                errorMessage != null -> Text(
                    text = errorMessage,
                    color = tokens.accentError,
                    fontSize = 14.sp,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Assertive
                        stateDescription = errorMessage
                    },
                )

                releases.isNullOrEmpty() -> Text(
                    text = "暂未获取到已发布的版本日志。",
                    color = tokens.textSecondary,
                    fontSize = 14.sp,
                )

                else -> Column(
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    releases.forEachIndexed { index, release ->
                        ReleaseNotesItem(
                            release = release,
                            sessionKey = sessionKey,
                            // The list is sorted newest first. Keep the newest release visible even
                            // when an older installed package is opening the history.
                            initiallyExpanded = index == 0 || release.versionName == BuildConfig.VERSION_NAME,
                        )
                    }
                }
            }
        },
        confirmButton = {
            GlassPressable(
                onClick = onDismiss,
                material = GlassMaterial.Control,
                cornerRadius = 16.dp,
                modifier = Modifier.height(40.dp),
            ) {
                Text(
                    text = "关闭",
                    color = tokens.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        },
        dismissButton = if (errorMessage != null) {
            {
                GlassPressable(
                    onClick = onRetry,
                    material = GlassMaterial.EmbeddedControl,
                    cornerRadius = 16.dp,
                    modifier = Modifier.height(40.dp),
                ) {
                    Text(
                        text = "重试",
                        color = tokens.accentPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun ReleaseNotesItem(
    release: AppReleaseNote,
    sessionKey: Int,
    initiallyExpanded: Boolean,
) {
    val tokens = LocalGlassTokens.current
    var expanded by remember(sessionKey, release.versionName) { mutableStateOf(initiallyExpanded) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(160),
        label = "release_notes_chevron",
    )
    val publishedAt = release.publishedAt
        ?.replace('T', ' ')
        ?.take(10)
        ?.takeIf { it.isNotBlank() }

    GlassPressable(
        onClick = { expanded = !expanded },
        material = GlassMaterial.EmbeddedControl,
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "版本 v${release.versionName} 更新日志"
                stateDescription = if (expanded) "更新详情已展开" else "更新详情已折叠"
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(180)),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "v${release.versionName}",
                        color = tokens.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    val subtitle = buildString {
                        if (release.title.isNotBlank()) append(release.title)
                        if (publishedAt != null) {
                            if (isNotEmpty()) append(" · ")
                            append("发布于 ").append(publishedAt)
                        }
                    }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            color = tokens.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = tokens.textTertiary,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = chevronRotation },
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(180)) + fadeIn(tween(150)),
                exit = shrinkVertically(tween(150)) + fadeOut(tween(110)),
            ) {
                Text(
                    text = release.body.ifBlank { "本版本未填写更新详情。" },
                    color = tokens.textPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun FeedbackHistoryItem(entry: FeedbackHistoryEntry) {
    val tokens = LocalGlassTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassMaterial(GlassMaterial.EmbeddedControl, 16.dp)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "${formatFeedbackSubmittedAt(entry.submittedAtMillis)} · ${entry.category.label}",
            color = tokens.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(text = "反馈编号：${entry.feedbackId}", color = tokens.textSecondary, fontSize = 12.sp)
        Text(text = "联系邮箱：${entry.maskedEmail()}", color = tokens.textSecondary, fontSize = 12.sp)
        Text(text = entry.message, color = tokens.textPrimary, fontSize = 13.sp)
    }
}

@Composable
private fun PrivacyPolicyPage() {
    val tokens = LocalGlassTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassMaterial(GlassMaterial.Panel, 24.dp)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "隐私政策",
            color = tokens.textPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "最后更新于 2026 年 10 月 1 日。",
            color = tokens.textTertiary,
            fontSize = 12.sp,
        )
        Text(
            text = "趣智校园热水应用用于帮助你查看余额、了解设备状态并操作已绑定的热水设备。为完成这些功能，应用会处理你主动填写、导入或登录产生的账号与设备配置，以及必要的运行状态和消费记录。",
            color = tokens.textSecondary,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        )
        PrivacyPolicyHeading(text = "信息收集与使用")
        Text(
            text = "应用在你登录、导入配置、绑定设备或使用相关功能时处理必要信息，用于完成请求、恢复状态和展示结果。登录可使用密码或短信验证码，两者仅用于本次登录，不会长期保存。账单和校园消息从趣智校园服务器获取；应用进入前台会同步消息，并在本机保存未读与提醒状态，不用于广告或用户画像。",
            color = tokens.textSecondary,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        )
        PrivacyPolicyHeading(text = "充值与支付")
        Text(
            text = "充值金额和支付方式由趣智校园服务器提供。你确认充值后，应用向趣智校园创建订单，并将返回的支付订单交给支付宝 SDK 完成支付；支付宝会按其隐私政策处理支付所需的订单、设备与网络信息。应用不收集支付密码，不在本地保存充值支付凭证，付款返回后重新查询服务器余额。",
            color = tokens.textSecondary,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        )
        PrivacyPolicyHeading(text = "本地保存")
        Text(
            text = "账号凭据和设备配置使用系统支持的加密存储。必要的订单与结算状态、消息已读状态、反馈记录及显示偏好保存在本机；消息正文仅在内存中展示。",
            color = tokens.textSecondary,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        )
        PrivacyPolicyHeading(text = "网络与权限")
        Text(
            text = "网络用于热水服务、账单、消息、版本检查、主动反馈及匿名统计。相机仅用于扫描设备二维码；附近设备权限仅用于发现热水器。Android 11 及以下的蓝牙扫描需要位置权限和系统定位开启，应用不会获取或上传地理坐标。通知权限用于用水状态提醒；消息弹窗与未读红点在应用内展示。",
            color = tokens.textSecondary,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        )
        PrivacyPolicyHeading(text = "匿名使用统计")
        Text(
            text = "应用会发送使用日期、月份、应用版本和匿名标识，用于汇总日活、月活及版本分布。匿名统计始终开启，不包含账号、手机号或硬件标识；匿名标识按日或月变化，本机随机种子不会上传，统计不用于广告或用户画像。升级不会在同一统计周期重复计数。",
            color = tokens.textSecondary, fontSize = 14.sp, lineHeight = 22.sp,
        )
        PrivacyPolicyHeading(text = "系统公告与自定义主题")
        Text("系统公告由项目网站提供，无需登录即可查看。主动提交公告回复时，会发送你选择的选项、填写的文字及该公告独立的匿名标识，用于去重和汇总；不会发送趣智校园账号、手机号或匿名活跃统计标识。请勿在回复中填写敏感信息。自定义背景图片仅保存在本机，不上传服务器。", color = tokens.textSecondary, fontSize = 14.sp, lineHeight = 22.sp)
        PrivacyPolicyHeading(text = "反馈与用户选择")
        Text(
            text = "提交问题反馈时，仅发送你填写的邮箱、反馈内容、类型和应用版本。请勿在反馈中填写密码、Token、学号或其他不必要的敏感信息。你可以在设置页的“清空所有信息”入口移除应用在本机保存的配置、记录、消息已读状态、反馈、显示设置和引导状态；匿名统计标识会一并删除，再次打开应用时会重新生成匿名标识并继续统计，也可以通过 Android 系统设置清除应用数据。政策内容会在处理方式发生重要变化时更新。",
            color = tokens.textSecondary,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        )
    }
}

@Composable
private fun PrivacyPolicyHeading(text: String) {
    val tokens = LocalGlassTokens.current
    Text(
        text = text,
        color = tokens.textPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
    )
}

private data class Creator(
    val name: String,
    val profileUrl: String,
    val avatarUrl: String,
)

private val Creators = listOf(
    Creator(
        name = "Solbrilo",
        profileUrl = "https://gitee.com/Solbrilo",
        avatarUrl = "https://foruda.gitee.com/avatar/1746673105091619288/15502997_solbrilo_1746673105.png",
    ),
    Creator(
        name = "NCYOIGG",
        profileUrl = "https://gitee.com/NCYOIGG",
        // Keep the profile identity canonical even if Gitee changes its avatar CDN path.
        avatarUrl = "https://gitee.com/NCYOIGG.png",
    ),
)

@Composable
private fun CreatorsPage(onOpenExternal: (String) -> Unit) {
    val tokens = LocalGlassTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Creators.forEach { creator ->
            GlassPressable(
                onClick = { onOpenExternal(creator.profileUrl) },
                material = GlassMaterial.Panel,
                cornerRadius = 24.dp,
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "打开 ${creator.name} 的 Gitee 页面" },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RemoteCreatorAvatar(name = creator.name, avatarUrl = creator.avatarUrl)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = creator.name,
                            color = tokens.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = creator.profileUrl,
                            color = tokens.accentPrimary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        tint = tokens.textTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteCreatorAvatar(name: String, avatarUrl: String) {
    val tokens = LocalGlassTokens.current
    var avatar by remember(avatarUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(avatarUrl) {
        avatar = withContext(Dispatchers.IO) {
            runCatching {
                URL(avatarUrl).openConnection().apply {
                    connectTimeout = 5_000
                    readTimeout = 5_000
                    setRequestProperty("User-Agent", "HotWater Android")
                }.getInputStream().use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(tokens.controlSurface),
        contentAlignment = Alignment.Center,
    ) {
        val currentAvatar = avatar
        if (currentAvatar != null) {
            Image(
                bitmap = currentAvatar,
                contentDescription = "$name 的头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = name.take(1).uppercase(Locale.ROOT),
                color = tokens.accentPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun formatFeedbackSubmittedAt(timestampMillis: Long): String = runCatching {
    Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault()))
}.getOrDefault("未知时间")

@Composable
private fun UpdateStatusText(
    checkResult: AppUpdateCheckResult?,
    installResult: AppUpdateInstallResult?,
) {
    val tokens = LocalGlassTokens.current
    val isError = installResult is AppUpdateInstallResult.Failed ||
        checkResult is AppUpdateCheckResult.Failed
    val (message, color) = when {
        installResult is AppUpdateInstallResult.InstallationLaunched ->
            "已打开系统安装确认，请完成更新。" to tokens.accentSuccess

        installResult is AppUpdateInstallResult.SourcePermissionRequired ->
            "请允许安装未知应用，返回后将自动继续安装。" to tokens.accentWarning

        installResult is AppUpdateInstallResult.Failed -> installResult.message to tokens.accentError
        checkResult is AppUpdateCheckResult.UpToDate -> "当前已是最新版本。" to tokens.accentSuccess
        checkResult is AppUpdateCheckResult.NoRelease -> "暂无已发布版本。" to tokens.textSecondary
        checkResult is AppUpdateCheckResult.InvalidRelease -> checkResult.message to tokens.accentWarning
        checkResult is AppUpdateCheckResult.Failed -> checkResult.message to tokens.accentError
        else -> "通过 Gitee 公开更新清单检查新版本。" to tokens.textSecondary
    }
    Text(
        text = message,
        color = color,
        fontSize = 13.sp,
        modifier = Modifier.semantics {
            liveRegion = if (isError) LiveRegionMode.Assertive else LiveRegionMode.Polite
            stateDescription = message
        },
    )
}
