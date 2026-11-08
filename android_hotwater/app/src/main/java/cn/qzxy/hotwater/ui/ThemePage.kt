package cn.qzxy.hotwater.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.designsystem.component.GlassButton
import cn.qzxy.hotwater.designsystem.component.GlassInput
import cn.qzxy.hotwater.designsystem.component.GlassPressable
import cn.qzxy.hotwater.designsystem.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun ThemePage(selected: ThemePalette, onSelected: (ThemePalette) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokens = LocalGlassTokens.current
    val settings = rememberCustomThemeSettings()
    var error by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var showCustomEditor by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            runCatching { CustomThemePreferences.importImage(context, uri) }
                .onSuccess { CustomThemePreferences.write(context, CustomThemePreferences.read(context).copy(image = true, imageRevision = System.currentTimeMillis())) }
                .onFailure { error = "无法读取这张图片，请选择其他图片" }
            importing = false
        }
    }
    fun update(next: CustomThemeSettings) = CustomThemePreferences.write(context, next)

    val editing = showCustomEditor && selected == ThemePalette.CUSTOM
    BackHandler(editing) { showCustomEditor = false }
    val previewFraction by animateFloatAsState(if (editing) .48f else .75f,
        animationSpec = tween(if (LocalGlassEnvironment.current.motionEnabled) 320 else 0), label = "theme_preview_size")
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.weight(previewFraction).fillMaxWidth(), contentAlignment = Alignment.Center) {
            PhoneThemePreview(selected)
        }
        Column(Modifier.weight(1f - previewFraction), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (editing) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("自定义主题", color = tokens.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton({ showCustomEditor = false }) { Text("完成", color = tokens.accentPrimary) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (!editing) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemePalette.entries.forEach { palette ->
                            GlassPressable(onClick = { onSelected(palette) }, selected = selected == palette,
                                cornerRadius = 20.dp, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(palette.label, color = if (selected == palette) tokens.accentPrimary else tokens.textPrimary,
                                    fontWeight = if (selected == palette) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                    Text(selected.description, color = tokens.textSecondary, fontSize = 13.sp)
                    AnimatedVisibility(selected == ThemePalette.CUSTOM) {
                        GlassButton("编辑自定义主题", { showCustomEditor = true }, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (editing) {
                    Column(Modifier.fillMaxWidth().glassMaterial(GlassMaterial.Panel, 24.dp).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        GlassButton(if (importing) "正在处理图片…" else "选择背景图片", { picker.launch(arrayOf("image/*")) },
                            enabled = !importing, modifier = Modifier.fillMaxWidth())
                        if (settings.image) {
                            TextButton(onClick = { update(settings.copy(image = false)) }) { Text("使用背景颜色") }
                            ThemeSlider("水平位置", settings.positionX, -1f..1f) { update(settings.copy(positionX = it)) }
                            ThemeSlider("垂直位置", settings.positionY, -1f..1f) { update(settings.copy(positionY = it)) }
                            ThemeSlider("模糊度", settings.blur, 0f..24f) { update(settings.copy(blur = it)) }
                        }
                        ColorEditor("背景颜色", settings.background) { update(settings.copy(background = it, image = false)) }
                        ColorEditor("主题色", settings.accent) { update(settings.copy(accent = it)) }
                        ThemeSlider("圆角 · ${settings.corner.roundToInt()}", settings.corner, 0f..36f) { update(settings.copy(corner = it)) }
                        Text("热水开关动画", color = tokens.textPrimary, fontWeight = FontWeight.Medium)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(ThemePalette.SKY, ThemePalette.TIDE, ThemePalette.EMBER, ThemePalette.MONO).forEach { p ->
                                GlassPressable({ update(settings.copy(animation = p.storageValue)) }, selected = settings.animation == p.storageValue,
                                    cornerRadius = 18.dp, contentPadding = PaddingValues(12.dp)) {
                                    Text(when (p) { ThemePalette.SKY -> "流转"; ThemePalette.TIDE -> "涟漪"; ThemePalette.EMBER -> "日晕"; else -> "刻度" },
                                        color = tokens.accentPrimary)
                                }
                            }
                        }
                        Text("图片仅保存在本机。拖动位置和模糊度后，预览会同步更新。", color = tokens.textTertiary, fontSize = 12.sp)
                        error?.let { Text(it, color = tokens.accentError) }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, change: (Float) -> Unit) {
    Column {
        Text(title, color = LocalGlassTokens.current.textSecondary, fontSize = 13.sp)
        Slider(value, change, valueRange = range, colors = SliderDefaults.colors(thumbColor = LocalGlassTokens.current.accentPrimary,
            activeTrackColor = LocalGlassTokens.current.accentPrimary))
    }
}

@Composable
private fun ColorEditor(label: String, value: Long, onChange: (Long) -> Unit) {
    val tokens = LocalGlassTokens.current
    var expanded by remember { mutableStateOf(false) }
    var showHex by remember { mutableStateOf(false) }
    var text by remember(value) { mutableStateOf("#%06X".format(value and 0xFFFFFF)) }
    var invalid by remember { mutableStateOf(false) }
    val initial = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(value.toInt(), it) } }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var saturation by remember { mutableFloatStateOf(initial[1]) }
    var brightness by remember { mutableFloatStateOf(initial[2]) }
    var lastValue by remember { mutableLongStateOf(value) }
    LaunchedEffect(value) {
        if (value != lastValue) {
            val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(value.toInt(), it) }
            hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]; lastValue = value
        }
    }
    fun publish(h: Float = hue, s: Float = saturation, v: Float = brightness) {
        hue = h; saturation = s; brightness = v
        lastValue = Color.hsv(h, s, v).toArgb().toLong() and 0xFFFFFFFF
        onChange(lastValue)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = tokens.textPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Box(Modifier.size(28.dp).background(Color(value), RoundedCornerShape(8.dp))
                .border(1.dp, tokens.textTertiary.copy(alpha = .4f), RoundedCornerShape(8.dp)))
            TextButton({ expanded = !expanded }) { Text(if (expanded) "收起选色" else "更多颜色", color = tokens.accentPrimary) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf(0xFFF0F4F8, 0xFF15232D, 0xFFEAF1E8, 0xFF3674AE, 0xFF2C7762, 0xFF9B4729, 0xFF70529D).forEach { color ->
                GlassPressable({ onChange(color) }, selected = value == color, modifier = Modifier.size(40.dp)
                    .semantics { contentDescription = "$label #%06X".format(color and 0xFFFFFF) }, cornerRadius = 20.dp) {
                    Box(Modifier.size(27.dp).background(Color(color), RoundedCornerShape(14.dp))
                        .border(1.dp, tokens.textTertiary.copy(alpha = .3f), RoundedCornerShape(14.dp)))
                }
            }
        }
        AnimatedVisibility(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ColorSlider("色相", hue, 0f..360f,
                    (0..6).map { Color.hsv(it * 60f, 1f, 1f) }) { publish(h = it) }
                ColorSlider("饱和度", saturation, 0f..1f,
                    listOf(Color.hsv(hue, 0f, brightness), Color.hsv(hue, 1f, brightness))) { publish(s = it) }
                ColorSlider("明度", brightness, 0f..1f,
                    listOf(Color.Black, Color.hsv(hue, saturation, 1f))) { publish(v = it) }
                TextButton({ showHex = !showHex }) { Text(if (showHex) "收起色值" else "输入色值", color = tokens.accentPrimary) }
                AnimatedVisibility(showHex) {
                    GlassInput(text, { next ->
                        text = next.take(7)
                        invalid = !Regex("^#?[0-9a-fA-F]{6}$").matches(text)
                        if (!invalid) onChange(0xFF000000 or text.removePrefix("#").toLong(16))
                    }, label = "$label · HEX", errorMessage = if (invalid) "请输入六位颜色代码，例如 #3674AE" else null)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, colors: List<Color>, onChange: (Float) -> Unit) {
    Column {
        Text(label, color = LocalGlassTokens.current.textSecondary, fontSize = 12.sp)
        Slider(value, onChange, valueRange = range, modifier = Modifier.semantics { contentDescription = label },
            colors = SliderDefaults.colors(thumbColor = LocalGlassTokens.current.accentPrimary),
            track = { Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))
                .background(Brush.horizontalGradient(colors))) })
    }
}

@Composable
private fun PhoneThemePreview(palette: ThemePalette) {
    val custom = LocalCustomTheme.current
    val tokens = if (palette == ThemePalette.CUSTOM) custom.tokens(LocalGlassEnvironment.current.darkTheme)
        else palette.tokens(LocalGlassEnvironment.current.darkTheme)
    val geometry = if (palette == ThemePalette.CUSTOM) ThemeGeometry(custom.corner / 24f, (custom.corner * 4).dp) else palette.geometry
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Scale a complete virtual phone instead of squeezing its contents as controls expand.
        val scale = minOf(maxHeight / 612.dp, maxWidth / 300.dp, 1f)
        Box(Modifier.requiredSize(300.dp, 612.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(34.dp))
            .background(Color(0xFF20252B)).padding(6.dp)) {
            CompositionLocalProvider(LocalGlassTokens provides tokens, LocalThemeGeometry provides geometry, LocalThemePalette provides palette) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(29.dp)).background(tokens.meshBase)) {
                    if (palette == ThemePalette.CUSTOM && custom.image) CustomThemeBackground(custom)
                    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.width(52.dp).height(13.dp).align(Alignment.CenterHorizontally)
                            .background(Color(0xFF20252B), RoundedCornerShape(10.dp)))
                        Text("● 已连接", color = tokens.accentSuccess, fontSize = 10.sp)
                        Text("你好，校园", color = tokens.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Column(Modifier.fillMaxWidth().glassMaterial(GlassMaterial.Panel, 24.dp).padding(14.dp)) {
                            Text("可用余额", color = tokens.textSecondary, fontSize = 10.sp)
                            Text("¥ 28.60", color = tokens.textPrimary, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                            Text("宿舍热水器", color = tokens.textSecondary, fontSize = 10.sp)
                        }
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            val anim = if (palette == ThemePalette.CUSTOM) ThemePalette.fromStorageValue(custom.animation) else palette
                            Box(Modifier.fillMaxSize(.8f), contentAlignment = Alignment.Center) {
                                // The same activity renderer as the real switch, scaled to the phone shell.
                                Box(Modifier.requiredSize(288.dp).graphicsLayer { scaleX = .48f; scaleY = .48f }) {
                                    ThemedActivityRing(anim, tokens.accentPrimary, LocalGlassEnvironment.current.motionEnabled)
                                }
                            }
                            Column(Modifier.size(106.dp).glassMaterial(GlassMaterial.Control, geometry.powerCorner * .45f),
                                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(themeWaterIcon(anim), null, Modifier.size(26.dp), tint = tokens.accentPrimary)
                                Text("开热水", color = tokens.textPrimary, fontSize = 14.sp)
                            }
                        }
                        Row(Modifier.fillMaxWidth().glassMaterial(GlassMaterial.Floating, 24.dp).padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf("用水", "账单", "设置").forEachIndexed { i, label -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(themeNavigationIcon(palette, i), null, Modifier.size(15.dp), tint = if (i == 0) tokens.accentPrimary else tokens.textSecondary)
                                Text(label, fontSize = 9.sp, color = tokens.textSecondary)
                            } }
                        }
                    }
                }
            }
        }
    }
}
