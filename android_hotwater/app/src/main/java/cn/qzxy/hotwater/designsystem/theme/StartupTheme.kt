package cn.qzxy.hotwater.designsystem.theme

import android.app.Activity
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.annotation.StyleRes
import androidx.compose.ui.graphics.toArgb
import cn.qzxy.hotwater.R

/** Stable resource names also let Android restore the selected palette before our process starts. */
internal object StartupTheme {
    @StyleRes
    fun resource(palette: ThemePalette, mode: ThemeMode): Int = when (palette) {
        ThemePalette.SKY -> when (mode) {
            ThemeMode.SYSTEM -> R.style.Theme_HotWater_Startup_Sky_System
            ThemeMode.LIGHT -> R.style.Theme_HotWater_Startup_Sky_Light
            ThemeMode.DARK -> R.style.Theme_HotWater_Startup_Sky_Dark
        }
        ThemePalette.JADE -> when (mode) {
            ThemeMode.SYSTEM -> R.style.Theme_HotWater_Startup_Jade_System
            ThemeMode.LIGHT -> R.style.Theme_HotWater_Startup_Jade_Light
            ThemeMode.DARK -> R.style.Theme_HotWater_Startup_Jade_Dark
        }
        ThemePalette.IRIS -> when (mode) {
            ThemeMode.SYSTEM -> R.style.Theme_HotWater_Startup_Iris_System
            ThemeMode.LIGHT -> R.style.Theme_HotWater_Startup_Iris_Light
            ThemeMode.DARK -> R.style.Theme_HotWater_Startup_Iris_Dark
        }
        ThemePalette.SAND -> when (mode) {
            ThemeMode.SYSTEM -> R.style.Theme_HotWater_Startup_Sand_System
            ThemeMode.LIGHT -> R.style.Theme_HotWater_Startup_Sand_Light
            ThemeMode.DARK -> R.style.Theme_HotWater_Startup_Sand_Dark
        }
        ThemePalette.TIDE -> when (mode) {
            ThemeMode.SYSTEM -> R.style.Theme_HotWater_Startup_Tide_System
            ThemeMode.LIGHT -> R.style.Theme_HotWater_Startup_Tide_Light
            ThemeMode.DARK -> R.style.Theme_HotWater_Startup_Tide_Dark
        }
        ThemePalette.EMBER -> when (mode) {
            ThemeMode.SYSTEM -> R.style.Theme_HotWater_Startup_Ember_System
            ThemeMode.LIGHT -> R.style.Theme_HotWater_Startup_Ember_Light
            ThemeMode.DARK -> R.style.Theme_HotWater_Startup_Ember_Dark
        }
        ThemePalette.MONO -> when (mode) {
            ThemeMode.SYSTEM -> R.style.Theme_HotWater_Startup_Mono_System
            ThemeMode.LIGHT -> R.style.Theme_HotWater_Startup_Mono_Light
            ThemeMode.DARK -> R.style.Theme_HotWater_Startup_Mono_Dark
        }
    }

    fun sync(activity: Activity, palette: ThemePalette, mode: ThemeMode) {
        val systemDark = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        activity.window.setBackgroundDrawable(ColorDrawable(palette.tokens(mode.resolveDarkTheme(systemDark)).meshBase.toArgb()))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity.splashScreen.setSplashScreenTheme(resource(palette, mode))
        }
    }
}
