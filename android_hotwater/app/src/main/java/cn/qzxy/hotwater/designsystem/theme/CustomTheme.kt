package cn.qzxy.hotwater.designsystem.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
    val blur: Float = 0f,
    val animation: String = "tide",
) {
    fun tokens(dark: Boolean): GlassTokens {
        val bg = if (dark) lerp(Color(background), Color.Black, .78f) else Color(background)
        val chosen = Color(accent)
        val ink = if (bg.luminance() < .35f) Color(0xFFF4F6FA) else Color(0xFF17212C)
        val panel = lerp(bg, ink, .045f)
        return ThemePalette.SKY.tokens(dark).copy(
            meshBase = bg, backgroundStart = bg, backgroundMiddle = bg, backgroundEnd = bg,
            panelSurface = panel.copy(alpha = if (image) .86f else .8f),
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
            blur = j.optDouble("blur", 0.0).toFloat().coerceIn(0f, 24f),
            animation = j.optString("animation", "tide").takeIf { it in listOf("sky", "tide", "ember", "mono") } ?: "tide",
        )
    }.getOrDefault(CustomThemeSettings())

    fun write(context: Context, s: CustomThemeSettings) {
        prefs(context).edit().putString(KEY, JSONObject().put("background", s.background)
            .put("accent", s.accent).put("corner", s.corner).put("image", s.image)
            .put("revision", s.imageRevision).put("x", s.positionX).put("y", s.positionY)
            .put("blur", s.blur).put("animation", s.animation).toString()).apply()
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
    return settings
}

@Composable
internal fun CustomThemeBackground(settings: CustomThemeSettings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, settings.imageRevision, settings.blur, settings.image) {
        if (settings.blur > 0) kotlinx.coroutines.delay(100)
        value = if (!settings.image) null else withContext(Dispatchers.IO) {
            runCatching {
                val original = BitmapFactory.decodeFile(CustomThemePreferences.imageFile(context).absolutePath,
                    BitmapFactory.Options().apply { inMutable = true })
                    ?: return@runCatching null
                // A software blur also works on Android 8–11. Decode and filter only off the UI thread.
                val scale = (720f / max(original.width, original.height)).coerceAtMost(1f)
                val small = Bitmap.createScaledBitmap(original, (original.width * scale).toInt().coerceAtLeast(1),
                    (original.height * scale).toInt().coerceAtLeast(1), true)
                if (small !== original) original.recycle()
                val radius = (settings.blur * scale).toInt()
                if (radius > 0) blurBitmap(small, radius)
                small.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(modifier.fillMaxSize()) {
        bitmap?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                alignment = androidx.compose.ui.BiasAlignment(settings.positionX, settings.positionY))
            Box(Modifier.fillMaxSize().background(LocalGlassTokens.current.meshBase.copy(alpha = .22f)))
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
