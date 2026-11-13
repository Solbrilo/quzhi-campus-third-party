package cn.qzxy.hotwater.designsystem.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import android.os.Build
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.max

data class CustomThemeSettings(
    val background: Long = 0xFFF0F4F8,
    val accent: Long = 0xFF3674AE,
    val corner: Float = 24f,
    val image: Boolean = false,
    val imageRevision: Long = 0,
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val zoom: Float = 1f,
    val imageColorRevision: Long = -1,
    val blur: Float = 0f,
    val cardBlur: Float = 16f,
    val animation: String = "tide",
) {
    fun tokens(dark: Boolean): GlassTokens {
        // Keep the image's hue, while the selected appearance controls luminance and contrast.
        val bg = if (dark) lerp(Color(background), Color(0xFF11161C), .84f)
            else if (image || Color(background).luminance() < .45f) lerp(Color(background), Color(0xFFF9F8F5), .90f) else Color(background)
        val chosen = when {
            dark && Color(accent).luminance() < .38f -> lerp(Color(accent), Color.White, .48f)
            !dark && Color(accent).luminance() > .45f -> lerp(Color(accent), Color(0xFF17212C), .50f)
            else -> Color(accent)
        }
        val ink = if (dark) Color(0xFFF4F6FA) else Color(0xFF17212C)
        val panel = lerp(bg, ink, .045f)
        return ThemePalette.SKY.tokens(dark).copy(
            meshBase = bg, backgroundStart = bg, backgroundMiddle = bg, backgroundEnd = bg,
            panelSurface = panel.copy(alpha = .62f),
            fallbackSurface = panel, floatingSurface = panel.copy(alpha = .94f),
            controlSurface = panel.copy(alpha = .9f), overlaySurface = panel.copy(alpha = .96f),
            inputSurface = lerp(bg, chosen, .1f), embeddedControlSurface = lerp(bg, chosen, .12f),
            pressedSurface = lerp(bg, chosen, .18f), softButtonSurface = lerp(bg, chosen, .12f),
            textPrimary = ink, textSecondary = lerp(ink, bg, .28f), textTertiary = lerp(ink, bg, .38f),
            accentPrimary = chosen, softButtonText = chosen,
            accentButtonText = if (chosen.luminance() > .45f) Color(0xFF14202A) else Color.White,
            borderLo = ink.copy(alpha = .12f), statusSurface = lerp(bg, chosen, .12f),
            powerOuterSurface = chosen.copy(alpha = .07f), powerInnerSurface = panel.copy(alpha = .45f),
        )
    }
}

val LocalCustomTheme = staticCompositionLocalOf { CustomThemeSettings() }

object CustomThemePreferences {
    private fun prefs(context: Context) = ThemeModePreferences.preferences(context)
    private const val KEY = "custom_theme_v1"
    fun read(context: Context): CustomThemeSettings = runCatching {
        val j = JSONObject(prefs(context).getString(KEY, "{}").orEmpty())
        CustomThemeSettings(
            background = j.optLong("background", 0xFFF0F4F8), accent = j.optLong("accent", 0xFF3674AE),
            corner = j.optDouble("corner", 24.0).toFloat().coerceIn(0f, 36f),
            image = j.optBoolean("image"), imageRevision = j.optLong("revision"),
            positionX = j.optDouble("x", 0.0).toFloat().coerceIn(-1f, 1f),
            positionY = j.optDouble("y", 0.0).toFloat().coerceIn(-1f, 1f),
            zoom = j.optDouble("zoom", 1.0).toFloat().coerceIn(1f, 4f),
            imageColorRevision = j.optLong("colorRevision", -1),
            blur = j.optDouble("blur", 0.0).toFloat().coerceIn(0f, 24f),
            cardBlur = j.optDouble("cardBlur", 16.0).toFloat().takeIf { it.isFinite() }?.coerceIn(0f, 32f) ?: 16f,
            animation = j.optString("animation", "tide").takeIf { it in listOf("sky", "tide", "ember", "mono", "brass") } ?: "tide",
        )
    }.getOrDefault(CustomThemeSettings())

    fun write(context: Context, s: CustomThemeSettings) {
        prefs(context).edit().putString(KEY, JSONObject().put("background", s.background)
            .put("accent", s.accent).put("corner", s.corner).put("image", s.image)
            .put("revision", s.imageRevision).put("x", s.positionX).put("y", s.positionY)
            .put("zoom", s.zoom).put("colorRevision", s.imageColorRevision)
            .put("blur", s.blur).put("cardBlur", s.cardBlur).put("animation", s.animation).toString()).apply()
    }
    fun imageFile(context: Context) = File(context.noBackupFilesDir, "custom_theme_background.jpg")

    suspend fun importImage(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "请选择有效的图片" }
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (max(bounds.outWidth, bounds.outHeight) / inSampleSize > 1800) inSampleSize *= 2
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("无法读取图片")
        val temp = File(context.noBackupFilesDir, "custom_theme_background.tmp")
        try {
            temp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
            check(temp.renameTo(imageFile(context))) { "保存图片失败" }
            extractImageBackground(bitmap)
        } finally { bitmap.recycle(); temp.delete() }
    }
}

@Composable
fun rememberCustomThemeSettings(): CustomThemeSettings {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(CustomThemePreferences.read(context)) }
    DisposableEffect(context) {
        val preferences = ThemeModePreferences.preferences(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            settings = CustomThemePreferences.read(context)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(settings.image, settings.imageRevision) {
        if (settings.image && settings.imageColorRevision != settings.imageRevision) {
            val revision = settings.imageRevision
            val background = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(CustomThemePreferences.imageFile(context).absolutePath)?.let {
                    try { extractImageBackground(it) } finally { it.recycle() }
                }
            }
            val latest = CustomThemePreferences.read(context)
            if (background != null && latest.image && latest.imageRevision == revision && latest.imageColorRevision != revision) {
                CustomThemePreferences.write(context, latest.copy(background = background, imageColorRevision = revision))
            }
        }
    }
    return settings
}

/** A small, deterministic sample avoids a network dependency or large palette allocation. */
internal fun extractImageBackground(bitmap: Bitmap): Long {
    val swatches = mutableMapOf<Int, DoubleArray>()
    for (y in 0 until bitmap.height step max(1, bitmap.height / 48)) {
        for (x in 0 until bitmap.width step max(1, bitmap.width / 48)) {
            val c = bitmap.getPixel(x, y)
            val r = c shr 16 and 255; val g = c shr 8 and 255; val b = c and 255
            val key = (r / 32 shl 6) or (g / 32 shl 3) or (b / 32)
            val bin = swatches.getOrPut(key) { DoubleArray(4) }
            bin[0]++; bin[1] += r; bin[2] += g; bin[3] += b
        }
    }
    val dominant = swatches.values.maxByOrNull { it[0] } ?: return 0xFF18232D
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((dominant[1] / dominant[0]).toInt(),
        (dominant[2] / dominant[0]).toInt(), (dominant[3] / dominant[0]).toInt(), hsv)
    hsv[1] = hsv[1].coerceAtMost(.42f)
    hsv[2] = hsv[2].coerceIn(.10f, .92f)
    return android.graphics.Color.HSVToColor(hsv).toLong() and 0xFFFFFFFF
}

@Composable
internal fun rememberBackgroundBitmap(settings: CustomThemeSettings): androidx.compose.ui.graphics.ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, settings.imageRevision, settings.image) {
        value = if (!settings.image) null else withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeFile(CustomThemePreferences.imageFile(context).absolutePath)?.asImageBitmap()
            }.getOrNull()
        }
    }
    return bitmap
}

@Composable
internal fun CustomThemeBackground(settings: CustomThemeSettings, modifier: Modifier = Modifier) {
    val dark = LocalGlassEnvironment.current.darkTheme
    val base = settings.tokens(dark).meshBase
    val bitmap = rememberBackgroundBitmap(settings)
    // Android 12+ changes RenderEffect every frame; older devices interpolate cached blur levels.
    val levels by produceState<List<androidx.compose.ui.graphics.ImageBitmap>>(emptyList(), bitmap) {
        value = emptyList()
        if (Build.VERSION.SDK_INT < 31 && bitmap != null) withContext(Dispatchers.Default) {
            val source = bitmap.asAndroidBitmap()
            val scale = (420f / max(source.width, source.height)).coerceAtMost(1f)
            val base = Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1), true)
            val result = (1..12).map { level ->
                base.copy(Bitmap.Config.ARGB_8888, true).also { blurBitmap(it, (level * 2 * scale).toInt().coerceAtLeast(1)) }.asImageBitmap()
            }
            if (base !== source) base.recycle()
            value = listOf(bitmap) + result
        }
    }
    Box(modifier.fillMaxSize().clipToBounds()) {
        bitmap?.let { original ->
            val effect = Modifier.fillMaxSize().graphicsLayer {
                if (Build.VERSION.SDK_INT >= 31 && settings.blur > 0f) {
                    renderEffect = android.graphics.RenderEffect.createBlurEffect(
                        settings.blur * density, settings.blur * density, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
                }
            }
            val amount = settings.blur / 2
            val low = amount.toInt().coerceIn(0, 12)
            androidx.compose.foundation.Canvas(effect) {
                val crop = max(size.width / original.width, size.height / original.height) * settings.zoom
                val width = original.width * crop
                val height = original.height * crop
                val left = (size.width - width) / 2 * (1 + settings.positionX)
                val top = (size.height - height) / 2 * (1 + settings.positionY)
                fun image(layer: androidx.compose.ui.graphics.ImageBitmap, alpha: Float = 1f) {
                    drawImage(layer, dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1)), alpha = alpha)
                }
                if (Build.VERSION.SDK_INT >= 31 || levels.isEmpty()) image(original)
                else {
                    image(levels[low])
                    if (low < 12) image(levels[low + 1], amount - low)
                }
                // A hue-matched veil and soft edge shading preserve the picture without a white wash.
                drawRect(base.copy(alpha = if (dark) .34f else .09f))
                drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(
                    base.copy(alpha = if (dark) .58f else .38f), Color.Transparent,
                    base.copy(alpha = if (dark) .64f else .40f))))
            }
        }
    }
}

private fun blurBitmap(bitmap: Bitmap, radius: Int) {
    val w = bitmap.width; val h = bitmap.height
    val pixels = IntArray(w * h); val scratch = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    fun pass(src: IntArray, dst: IntArray, horizontal: Boolean) {
        val outer = if (horizontal) h else w; val inner = if (horizontal) w else h
        val count = radius * 2 + 1
        for (line in 0 until outer) {
            fun index(p: Int) = if (horizontal) line * w + p.coerceIn(0, w - 1) else p.coerceIn(0, h - 1) * w + line
            var r = 0; var g = 0; var b = 0
            for (p in -radius..radius) { val c = src[index(p)]; r += c shr 16 and 255; g += c shr 8 and 255; b += c and 255 }
            for (p in 0 until inner) {
                dst[index(p)] = (255 shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
                val a = src[index(p - radius)]; val z = src[index(p + radius + 1)]
                r += (z shr 16 and 255) - (a shr 16 and 255)
                g += (z shr 8 and 255) - (a shr 8 and 255); b += (z and 255) - (a and 255)
            }
        }
    }
    repeat(3) { pass(pixels, scratch, true); pass(scratch, pixels, false) }
    bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
}
