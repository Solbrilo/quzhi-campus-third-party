package cn.qzxy.hotwater.designsystem.theme

import android.app.Activity
import android.database.ContentObserver
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import cn.qzxy.hotwater.designsystem.motion.rememberMotionEnabled
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Stable
data class GlassEnvironment(
    val hazeState: HazeState,
    val blurAvailable: Boolean,
    val motionEnabled: Boolean,
    val preferOpaqueMaterials: Boolean,
)

private val PreviewGlassEnvironment = GlassEnvironment(
    hazeState = HazeState(initialBlurEnabled = false),
    blurAvailable = false,
    motionEnabled = true,
    preferOpaqueMaterials = false,
)

val LocalGlassEnvironment = staticCompositionLocalOf { PreviewGlassEnvironment }

@Composable
fun GlassTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.resolveDarkTheme(isSystemInDarkTheme())
    GlassThemeContent(
        darkTheme = darkTheme,
        content = content,
    )
}

/**
 * Compatibility entry point for callers that already resolved a boolean theme.
 * New screens should pass [ThemeMode] so the system choice remains explicit.
 */
@Composable
fun GlassTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    GlassThemeContent(darkTheme = darkTheme, content = content)
}

@Composable
private fun GlassThemeContent(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val tokens = if (darkTheme) DarkGlassTokens else LightGlassTokens
    val view = LocalView.current
    val preferOpaqueMaterials = rememberPreferOpaqueMaterials()
    val blurAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2 &&
        view.isHardwareAccelerated &&
        !preferOpaqueMaterials
    val hazeState = rememberHazeState(blurEnabled = blurAvailable)
    val motionEnabled = rememberMotionEnabled()
    val environment = remember(hazeState, blurAvailable, motionEnabled, preferOpaqueMaterials) {
        GlassEnvironment(
            hazeState = hazeState,
            blurAvailable = blurAvailable,
            motionEnabled = motionEnabled,
            preferOpaqueMaterials = preferOpaqueMaterials,
        )
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalGlassTokens provides tokens,
        LocalGlassEnvironment provides environment,
    ) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                background = tokens.meshBase,
                surface = tokens.meshBase,
                onBackground = tokens.textPrimary,
                onSurface = tokens.textPrimary,
                primary = tokens.accentPrimary,
            ),
            typography = GlassTypography,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                GlassMeshBackground(
                    tokens = tokens,
                    modifier = if (blurAvailable) {
                        Modifier.hazeSource(
                            state = hazeState,
                            zIndex = 0f,
                        )
                    } else {
                        Modifier
                    },
                )
                content()
            }
        }
    }
}

@Composable
fun Modifier.glassHazeSource(zIndex: Float): Modifier {
    val environment = LocalGlassEnvironment.current
    return if (environment.blurAvailable) {
        hazeSource(
            state = environment.hazeState,
            zIndex = zIndex,
        )
    } else {
        this
    }
}

@Composable
private fun rememberPreferOpaqueMaterials(): Boolean {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val highContrastUri = remember(resolver) {
        Settings.Secure.getUriFor(HighTextContrastSetting)
    }
    var enabled by remember(resolver) { mutableStateOf(resolver.isHighTextContrastEnabled()) }

    DisposableEffect(resolver, highContrastUri) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = resolver.isHighTextContrastEnabled()
            }
        }
        resolver.registerContentObserver(highContrastUri, false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

private const val HighTextContrastSetting = "high_text_contrast_enabled"

private fun android.content.ContentResolver.isHighTextContrastEnabled(): Boolean {
    return Settings.Secure.getInt(this, HighTextContrastSetting, 0) == 1
}
