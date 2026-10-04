package cn.qzxy.hotwater.designsystem.theme

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.core.content.edit

@Immutable
enum class ThemePalette(val storageValue: String, val label: String) {
    SKY("sky", "澄空"),
    JADE("jade", "青屿"),
    IRIS("iris", "暮紫"),
    SAND("sand", "暖砂");

    fun tokens(dark: Boolean): GlassTokens {
        val base = if (dark) DarkGlassTokens else LightGlassTokens
        if (this == SKY) return base
        val accent = when (this) {
            JADE -> if (dark) Color(0xFF8DD9C2) else Color(0xFF246C5C)
            IRIS -> if (dark) Color(0xFFCBB9F8) else Color(0xFF70529D)
            SAND -> if (dark) Color(0xFFE8C199) else Color(0xFF845B32)
            SKY -> base.accentPrimary
        }
        val background = when (this) {
            JADE -> if (dark) Color(0xFF111E1C) else Color(0xFFECF4EF)
            IRIS -> if (dark) Color(0xFF1A1726) else Color(0xFFF2EEFA)
            SAND -> if (dark) Color(0xFF221C17) else Color(0xFFF7F0E6)
            SKY -> base.meshBase
        }
        val panel = lerp(background, if (dark) Color.White else accent, if (dark) 0.085f else 0.035f)
        val soft = lerp(background, accent, if (dark) 0.15f else 0.12f)
        val ink = lerp(if (dark) Color.White else Color(0xFF171B24), accent, 0.08f)
        val secondary = lerp(ink, background, if (dark) 0.32f else 0.35f)
        return base.copy(
            meshBase = background, backgroundStart = background, backgroundMiddle = background,
            backgroundEnd = background, panelSurface = panel.copy(alpha = if (dark) 0.90f else 0.74f),
            floatingSurface = panel.copy(alpha = 0.8f), controlSurface = panel.copy(alpha = 0.8f),
            embeddedControlSurface = soft.copy(alpha = 0.9f), inputSurface = soft.copy(alpha = 0.55f),
            overlaySurface = panel.copy(alpha = 0.94f), pressedSurface = soft, fallbackSurface = panel,
            borderLo = accent.copy(alpha = 0.18f), textPrimary = ink, textSecondary = secondary,
            textTertiary = secondary, accentPrimary = accent, softButtonSurface = soft,
            softButtonText = accent, accentButtonText = if (dark) background else Color.White,
            statusSurface = soft, powerOuterSurface = soft.copy(alpha = 0.16f),
            powerInnerSurface = panel.copy(alpha = 0.2f),
        )
    }

    companion object {
        fun fromStorageValue(value: String?): ThemePalette = entries.firstOrNull { it.storageValue == value } ?: SKY
    }
}

object ThemePalettePreferences {
    private const val KEY_PALETTE = "theme_palette"
    fun read(context: Context): ThemePalette = runCatching {
        ThemePalette.fromStorageValue(ThemeModePreferences.preferences(context).getString(KEY_PALETTE, null))
    }.getOrDefault(ThemePalette.SKY)

    fun write(context: Context, palette: ThemePalette) {
        ThemeModePreferences.preferences(context).edit { putString(KEY_PALETTE, palette.storageValue) }
    }
}
