package cn.qzxy.hotwater.designsystem.theme

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class GlassIntensityPreferencesTest {
    @Test
    fun sanitizerClampsFiniteValuesAndUsesDefaultForNonFiniteValues() {
        assertEquals(0f, sanitizeGlassIntensity(-1f))
        assertEquals(0f, sanitizeGlassIntensity(0f))
        assertEquals(0.42f, sanitizeGlassIntensity(0.42f), 0f)
        assertEquals(1f, sanitizeGlassIntensity(2f))
        assertEquals(DEFAULT_GLASS_INTENSITY, sanitizeGlassIntensity(Float.NaN), 0f)
        assertEquals(DEFAULT_GLASS_INTENSITY, sanitizeGlassIntensity(Float.POSITIVE_INFINITY), 0f)
        assertEquals(DEFAULT_GLASS_INTENSITY, sanitizeGlassIntensity(Float.NEGATIVE_INFINITY), 0f)
    }

    @Test
    fun readFallsBackForCorruptPreferenceType() {
        val preferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, _ ->
            if (method.name == "getFloat") {
                throw ClassCastException("glass_intensity is not a float")
            }
            throw UnsupportedOperationException(method.name)
        } as SharedPreferences

        assertEquals(DEFAULT_GLASS_INTENSITY, GlassIntensityPreferences.read(preferences), 0f)
    }

    @Test
    fun readClampsStoredFloat() {
        val preferences = proxyPreferencesWithFloat(2f)
        assertEquals(1f, GlassIntensityPreferences.read(preferences), 0f)
    }

    @Test
    fun writeSanitizesValueBeforePersisting() {
        var writtenValue = Float.NaN
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { _, method, args ->
            when (method.name) {
                "putFloat" -> {
                    writtenValue = args!![1] as Float
                    editor
                }
                "apply" -> Unit
                "commit" -> true
                else -> throw UnsupportedOperationException(method.name)
            }
        } as SharedPreferences.Editor
        val preferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, _ ->
            if (method.name == "edit") editor else throw UnsupportedOperationException(method.name)
        } as SharedPreferences

        GlassIntensityPreferences.write(preferences, Float.POSITIVE_INFINITY)

        assertEquals(DEFAULT_GLASS_INTENSITY, writtenValue, 0f)
    }

    private fun proxyPreferencesWithFloat(value: Float): SharedPreferences = Proxy.newProxyInstance(
        SharedPreferences::class.java.classLoader,
        arrayOf(SharedPreferences::class.java),
    ) { _, method, _ ->
        if (method.name == "getFloat") value else throw UnsupportedOperationException(method.name)
    } as SharedPreferences
}
