package cn.qzxy.hotwater.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import cn.qzxy.hotwater.DashboardSnapshot
import cn.qzxy.hotwater.ui.navigation.BottomDock
import kotlin.math.*
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
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
                .onSuccess { background ->
                    val revision = System.currentTimeMillis()
                    CustomThemePreferences.write(context, CustomThemePreferences.read(context).copy(image = true,
                        imageRevision = revision, imageColorRevision = revision, background = background,
                        positionX = 0f, positionY = 0f, zoom = 1f))
                }
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
            PhoneThemePreview(selected, editing) { update(it) }
        }
        Column(Modifier.weight(1f - previewFraction), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (editing) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("自定义主题", color = tokens.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton({ showCustomEditor = false }) { Text("完成", color = tokens.accentPrimary) }
            }
            ScrollViewport(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().cardVerticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                    CollapsingGlassCard(header = {
                        Text("背景", color = tokens.textPrimary, fontWeight = FontWeight.Medium)
                    }) {
                        GlassButton(if (importing) "正在处理图片…" else "选择背景图片", { picker.launch(arrayOf("image/*")) },
                            enabled = !importing, modifier = Modifier.fillMaxWidth())
                        if (settings.image) {
                            TextButton(onClick = { update(settings.copy(image = false)) }) { Text("使用背景颜色") }
                            Text("在上方手机中拖动图片，双指缩放", color = tokens.textSecondary, fontSize = 13.sp)
                            TextButton({ update(settings.copy(positionX = 0f, positionY = 0f, zoom = 1f)) }) { Text("重置构图") }
                            ThemeSlider("模糊度", settings.blur, 0f..24f) { update(settings.copy(blur = it)) }
                        }
                        ColorEditor(if (settings.image) "图片协调底色" else "背景颜色", settings.background) { update(settings.copy(background = it)) }
                    }
                    CollapsingGlassCard(header = {
                        Text("卡片与控件", color = tokens.textPrimary, fontWeight = FontWeight.Medium)
                    }) {
                        ColorEditor("主题色", settings.accent) { update(settings.copy(accent = it)) }
                        ThemeSlider("圆角 · ${settings.corner.roundToInt()}", settings.corner, 0f..36f) { update(settings.copy(corner = it)) }
                        ThemeSlider("卡片模糊度 · ${(settings.cardBlur / 32f * 100).roundToInt()}%", settings.cardBlur, 0f..32f) {
                            update(settings.copy(cardBlur = it))
                        }
                    }
                    CollapsingGlassCard(header = {
                        Text("开关动效", color = tokens.textPrimary, fontWeight = FontWeight.Medium)
                    }) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WaterAnimation.entries.forEach { animation ->
                                GlassPressable({ update(settings.copy(animation = animation.storageValue)) }, selected = settings.animation == animation.storageValue,
                                    cornerRadius = 18.dp, contentPadding = PaddingValues(12.dp)) {
                                    Text(animation.label, color = tokens.accentPrimary)
                                }
                            }
                        }
                        Text("图片仅保存在本机。构图、模糊和颜色均实时预览。", color = tokens.textTertiary, fontSize = 12.sp)
                        error?.let { Text(it, color = tokens.accentError) }
                        Spacer(Modifier.height(12.dp))
                    }
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
        AnimatedVisibility(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(0xFFF0F4F8, 0xFF15232D, 0xFFEAF1E8, 0xFF3674AE, 0xFF2C7762, 0xFF9B4729, 0xFF70529D).forEach { color ->
                        GlassPressable({ onChange(color) }, selected = value == color, modifier = Modifier.size(40.dp)
                            .semantics { contentDescription = "$label #%06X".format(color and 0xFFFFFF) }, cornerRadius = 20.dp) {
                            Box(Modifier.size(27.dp).background(Color(color), RoundedCornerShape(14.dp))
                                .border(1.dp, tokens.textTertiary.copy(alpha = .3f), RoundedCornerShape(14.dp)))
                        }
                    }
                }
                ColorWheel(hue, saturation, brightness) { h, sat -> publish(h = h, s = sat) }
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
private fun ColorWheel(hue: Float, saturation: Float, brightness: Float, onChange: (Float, Float) -> Unit) {
    val change by rememberUpdatedState(onChange)
    val colors = remember { (0..12).map { Color.hsv(it * 30f, 1f, 1f) } }
    fun positionToColor(point: Offset, size: androidx.compose.ui.unit.IntSize) {
        val delta = point - Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f
        change(((atan2(delta.y, delta.x) * 180f / PI.toFloat()) + 360f) % 360f,
            (delta.getDistance() / radius).coerceIn(0f, 1f))
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(196.dp).semantics { contentDescription = "色轮：环绕选择色相，向中心减小饱和度" }
            .pointerInput(Unit) { detectTapGestures { positionToColor(it, size) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> change.consume(); positionToColor(change.position, size) } }) {
            val radius = size.minDimension / 2f
            drawCircle(Brush.sweepGradient(colors))
            drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), radius = radius))
            val radians = hue * PI.toFloat() / 180f
            val point = center + Offset(cos(radians), sin(radians)) * (radius * saturation)
            drawCircle(Color.hsv(hue, saturation, brightness), 7.dp.toPx(), point)
            drawCircle(Color.Black.copy(alpha = .3f), 9.dp.toPx(), point, style = Stroke(3.dp.toPx()))
            drawCircle(Color.White, 8.dp.toPx(), point, style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
private fun PhoneThemePreview(palette: ThemePalette, editing: Boolean, update: (CustomThemeSettings) -> Unit) {
    val custom = LocalCustomTheme.current
    val latestCustom by rememberUpdatedState(custom)
    val change by rememberUpdatedState(update)
    val bitmap = rememberBackgroundBitmap(custom)
    val tokens = if (palette == ThemePalette.CUSTOM) custom.tokens(LocalGlassEnvironment.current.darkTheme)
        else palette.tokens(LocalGlassEnvironment.current.darkTheme)
    val geometry = if (palette == ThemePalette.CUSTOM) ThemeGeometry(custom.corner / 24f, (custom.corner * 4).dp) else palette.geometry
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val screenWidth = with(density) { window.width.toDp() }
    val screenHeight = with(density) { window.height.toDp() }
    val statusHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val shellWidth = screenWidth + 16.dp
    val shellHeight = screenHeight + 16.dp
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Lay out at the actual window size first, then shrink the entire phone. This keeps
        // every control in the same proportion as the real home screen, on any display density.
        val scale = minOf(maxHeight / shellHeight, maxWidth / shellWidth, 1f)
        Box(Modifier.requiredSize(shellWidth, shellHeight).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(38.dp)).background(Color(0xFF15191E)).padding(8.dp)) {
            CompositionLocalProvider(LocalGlassTokens provides tokens, LocalThemeGeometry provides geometry,
                LocalThemePalette provides palette) {
                val imageGesture = if (editing && custom.image && bitmap != null) Modifier.pointerInput(bitmap) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val old = latestCustom
                            val nextZoom = (old.zoom * zoom).coerceIn(1f, 4f)
                            val crop = max(size.width.toFloat() / bitmap.width, size.height.toFloat() / bitmap.height)
                            val oldX = (bitmap.width * crop * old.zoom - size.width).coerceAtLeast(0f) / 2
                            val oldY = (bitmap.height * crop * old.zoom - size.height).coerceAtLeast(0f) / 2
                            val newX = (bitmap.width * crop * nextZoom - size.width).coerceAtLeast(0f) / 2
                            val newY = (bitmap.height * crop * nextZoom - size.height).coerceAtLeast(0f) / 2
                            val ratio = nextZoom / old.zoom
                            val focus = centroid - Offset(size.width / 2f, size.height / 2f)
                            val tx = -old.positionX * oldX * ratio + focus.x * (1 - ratio) + pan.x
                            val ty = -old.positionY * oldY * ratio + focus.y * (1 - ratio) + pan.y
                            change(old.copy(zoom = nextZoom,
                                positionX = if (newX > 0) (-tx / newX).coerceIn(-1f, 1f) else 0f,
                                positionY = if (newY > 0) (-ty / newY).coerceIn(-1f, 1f) else 0f))
                        }
                    } else Modifier
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(30.dp))) {
                    GlassPageSurface {
                        Box(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().glassHazeSource(1f)) {
                            Row(Modifier.fillMaxWidth().height(statusHeight.coerceAtLeast(24.dp)).padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("9:41", color = tokens.textPrimary, fontSize = 11.sp)
                                Box(Modifier.size(8.dp).background(Color(0xFF15191E), RoundedCornerShape(4.dp)))
                                Text("5G  ▰", color = tokens.textPrimary, fontSize = 10.sp)
                            }
                            AppHeader(AppConnectionStatus.CONNECTED, {}, modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp))
                            Box(Modifier.weight(1f).padding(horizontal = 20.dp)) {
                                OperationsPage(DashboardSnapshot(26.8, "26.80", "演示账号", "301 宿舍热水器", "",
                                    "热水已关闭", null, false, isDeviceOnline = true), false, {}, {}, {}, {},
                                    previewAnimation = true)
                            }
                        }
                        BottomDock(listOf("用水", "账单", "设置"), 0, { _, _ -> }, {},
                            Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp).padding(bottom = navigationHeight + 12.dp))
                        // Preview controls never issue actions or steal the image-edit gesture.
                        Box(Modifier.fillMaxSize().then(imageGesture).pointerInput(editing, custom.image) {
                            if (!(editing && custom.image)) awaitPointerEventScope {
                                while (true) awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        })
                        }
                    }
                }
            }
        }
    }
}
