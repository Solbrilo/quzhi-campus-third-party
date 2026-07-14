package cn.qzxy.hotwater.designsystem.theme

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun missingAndMalformedValuesFallBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageValue(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageValue(""))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageValue("SYSTEM"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageValue("solarized"))
    }

    @Test
    fun preferenceWithCorruptValueTypeFallsBackToSystem() {
        val preferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, _ ->
            if (method.name == "getString") {
                throw ClassCastException("theme_mode is not a string")
            }
            throw UnsupportedOperationException(method.name)
        } as SharedPreferences

        assertEquals(ThemeMode.SYSTEM, ThemeModePreferences.read(preferences))
    }

    @Test
    fun stableValuesRoundTripToTheExpectedMode() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageValue("system"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStorageValue("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorageValue("dark"))
        assertEquals("system", ThemeMode.SYSTEM.storageValue)
        assertEquals("light", ThemeMode.LIGHT.storageValue)
        assertEquals("dark", ThemeMode.DARK.storageValue)
    }

    @Test
    fun nextCyclesSystemLightDarkAndBackToSystem() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.SYSTEM.next())
        assertEquals(ThemeMode.DARK, ThemeMode.LIGHT.next())
        assertEquals(ThemeMode.SYSTEM, ThemeMode.DARK.next())
    }

    @Test
    fun systemModeFollowsPlatformNightModeWhileExplicitModesDoNot() {
        assertFalse(ThemeMode.SYSTEM.resolveDarkTheme(systemIsDark = false))
        assertTrue(ThemeMode.SYSTEM.resolveDarkTheme(systemIsDark = true))
        assertFalse(ThemeMode.LIGHT.resolveDarkTheme(systemIsDark = false))
        assertFalse(ThemeMode.LIGHT.resolveDarkTheme(systemIsDark = true))
        assertTrue(ThemeMode.DARK.resolveDarkTheme(systemIsDark = false))
        assertTrue(ThemeMode.DARK.resolveDarkTheme(systemIsDark = true))
    }
}
