package cn.qzxy.hotwater.ui

import android.content.res.Configuration
import android.util.TypedValue
import androidx.compose.ui.graphics.toArgb
import androidx.test.platform.app.InstrumentationRegistry
import cn.qzxy.hotwater.designsystem.theme.StartupTheme
import cn.qzxy.hotwater.designsystem.theme.ThemeMode
import cn.qzxy.hotwater.designsystem.theme.ThemePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupThemeInstrumentedTest {
    @Test fun nativeStartingWindowMatchesEveryComposePaletteAndMode() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        listOf(false, true).forEach { systemDark ->
            val config = Configuration(base.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (systemDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val context = base.createConfigurationContext(config)
            ThemePalette.entries.forEach { palette ->
                ThemeMode.entries.forEach { mode ->
                    val theme = context.resources.newTheme().apply { applyStyle(StartupTheme.resource(palette, mode), true) }
                    val previewDisabled = TypedValue()
                    assertTrue(theme.resolveAttribute(android.R.attr.windowDisablePreview, previewDisabled, true))
                    assertTrue("The OS icon/background preview must stay disabled", previewDisabled.data != 0)
                    val expected = palette.tokens(mode.resolveDarkTheme(systemDark)).meshBase.toArgb()
                    listOf(android.R.attr.windowBackground, android.R.attr.windowSplashScreenBackground).forEach { attr ->
                        val value = TypedValue()
                        theme.resolveAttribute(attr, value, true)
                        assertEquals("$palette/$mode, systemDark=$systemDark", expected, value.data)
                    }
                }
            }
        }
    }
}
