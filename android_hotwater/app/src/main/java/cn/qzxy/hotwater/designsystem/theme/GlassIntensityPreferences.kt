package cn.qzxy.hotwater.designsystem.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * The default visual strength keeps the v1.4.1 material values unchanged.
 * Values are deliberately kept in the inclusive [0f, 1f] range.
 */
const val DEFAULT_GLASS_INTENSITY = 0.75f

/**
 * Sanitizes values coming from UI state, saved state, or an untrusted preference
 * store. Non-finite values cannot be meaningfully clamped, so they use the
 * stable default instead.
 */
fun sanitizeGlassIntensity(value: Float): Float = when {
    !value.isFinite() -> DEFAULT_GLASS_INTENSITY
    else -> value.coerceIn(0f, 1f)
}

/** Persists the Compose glass strength alongside the ordinary theme preference. */
object GlassIntensityPreferences {
    const val KEY_GLASS_INTENSITY = "glass_intensity"

    private fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(ThemeModePreferences.FILE_NAME, Context.MODE_PRIVATE)

    fun read(context: Context): Float = read(preferences(context))

    /** Reads a float and safely falls back for a bad type or non-finite value. */
    fun read(preferences: SharedPreferences): Float = runCatching {
        sanitizeGlassIntensity(preferences.getFloat(KEY_GLASS_INTENSITY, DEFAULT_GLASS_INTENSITY))
    }.getOrDefault(DEFAULT_GLASS_INTENSITY)

    fun write(context: Context, value: Float) = write(preferences(context), value)

    fun write(preferences: SharedPreferences, value: Float) {
        preferences.edit { putFloat(KEY_GLASS_INTENSITY, sanitizeGlassIntensity(value)) }
    }
}
