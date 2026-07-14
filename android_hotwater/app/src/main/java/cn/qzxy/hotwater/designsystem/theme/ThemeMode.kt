package cn.qzxy.hotwater.designsystem.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * The theme choice exposed to the user.
 *
 * The persisted values are deliberately stable and lowercase. Keep these values
 * independent from enum names so a future Kotlin rename does not invalidate
 * existing preferences.
 */
enum class ThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    /** Returns the next item in the system -> light -> dark cycle. */
    fun next(): ThemeMode = when (this) {
        SYSTEM -> LIGHT
        LIGHT -> DARK
        DARK -> SYSTEM
    }

    /** Resolves this choice against the current platform night-mode state. */
    fun resolveDarkTheme(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        /** Parses a stored value; unknown, missing, or malformed values use SYSTEM. */
        fun fromStorageValue(value: String?): ThemeMode = when (value) {
            "system" -> SYSTEM
            "light" -> LIGHT
            "dark" -> DARK
            else -> SYSTEM
        }

        // Kept as a short alias for callers that deal with preference strings.
        fun fromPreferenceValue(value: String?): ThemeMode = fromStorageValue(value)
    }
}

/**
 * Ordinary (unencrypted) preferences for the visual theme only.
 *
 * This file is intentionally separate from the encrypted hot-water account
 * configuration. Widgets do not read this store and therefore continue to use
 * the platform's system theme.
 */
object ThemeModePreferences {
    const val FILE_NAME = "hotwater_theme_preferences"
    const val KEY_MODE = "theme_mode"

    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun read(context: Context): ThemeMode = read(preferences(context))

    /** Reads the mode and safely falls back when the preference has a bad type/value. */
    fun read(preferences: SharedPreferences): ThemeMode = runCatching {
        ThemeMode.fromStorageValue(preferences.getString(KEY_MODE, null))
    }.getOrDefault(ThemeMode.SYSTEM)

    fun write(context: Context, mode: ThemeMode) = write(preferences(context), mode)

    fun write(preferences: SharedPreferences, mode: ThemeMode) {
        preferences.edit { putString(KEY_MODE, mode.storageValue) }
    }

    // Verb aliases make the read/write intent clear at call sites.
    fun get(context: Context): ThemeMode = read(context)

    fun set(context: Context, mode: ThemeMode) = write(context, mode)
}
