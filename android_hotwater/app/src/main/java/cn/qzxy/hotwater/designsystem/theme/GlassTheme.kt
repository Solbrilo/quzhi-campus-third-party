package cn.qzxy.hotwater.designsystem.theme

import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import cn.qzxy.hotwater.FeedbackPreferences
import cn.qzxy.hotwater.FeedbackOption
import android.app.Activity
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
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
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Stable
data class GlassEnvironment(
    val hazeState: HazeState,
    val blurAvailable: Boolean,
    val motionEnabled: Boolean,
    val preferOpaqueMaterials: Boolean,
    /** Shared material strength, normalized to the inclusive [0f, 1f] range. */
    val glassIntensity: Float = DEFAULT_GLASS_INTENSITY,
    val backgroundBackdrop: LayerBackdrop? = null,
    val contentBackdrop: LayerBackdrop? = null,
    val floatingBackdrop: Backdrop? = null,
    val liquidAvailable: Boolean = false,
    val darkTheme: Boolean = true,
)

private val PreviewGlassEnvironment = GlassEnvironment(
    hazeState = HazeState(initialBlurEnabled = false),
    blurAvailable = false,
    motionEnabled = true,
    preferOpaqueMaterials = false,
    glassIntensity = DEFAULT_GLASS_INTENSITY,
)

val LocalGlassEnvironment = staticCompositionLocalOf { PreviewGlassEnvironment }

@Composable
fun GlassTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    glassIntensity: Float = DEFAULT_GLASS_INTENSITY,
    palette: ThemePalette = ThemePalette.SKY,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.resolveDarkTheme(isSystemInDarkTheme())
    GlassThemeContent(
        darkTheme = darkTheme,
        glassIntensity = glassIntensity,
        palette = palette,
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
    glassIntensity: Float = DEFAULT_GLASS_INTENSITY,
    content: @Composable () -> Unit,
) {
    GlassThemeContent(
        darkTheme = darkTheme,
        glassIntensity = glassIntensity,
        content = content,
    )
}

@Composable
private fun GlassThemeContent(
    darkTheme: Boolean,
    glassIntensity: Float,
    palette: ThemePalette = ThemePalette.SKY,
    content: @Composable () -> Unit,
) {
    val motionEnabled = rememberMotionEnabled()
    val targetTokens = palette.tokens(darkTheme)
    val targetGeometry = palette.geometry
    var startGeometry by remember { mutableStateOf(targetGeometry) }
    var endGeometry by remember { mutableStateOf(targetGeometry) }
    var startTokens by remember { mutableStateOf(targetTokens) }
    var endTokens by remember { mutableStateOf(targetTokens) }
    val progress = remember { androidx.compose.animation.core.Animatable(1f) }
    val tokens = if (motionEnabled) lerpGlassTokens(startTokens, endTokens, progress.value) else targetTokens
    val geometry = if (motionEnabled) lerpThemeGeometry(startGeometry, endGeometry, progress.value) else targetGeometry
    LaunchedEffect(targetTokens, targetGeometry, motionEnabled) {
        startTokens = lerpGlassTokens(startTokens, endTokens, progress.value)
        startGeometry = lerpThemeGeometry(startGeometry, endGeometry, progress.value)
        endTokens = targetTokens
        endGeometry = targetGeometry
        progress.snapTo(if (motionEnabled) 0f else 1f)
        if (motionEnabled) progress.animateTo(1f, tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    val normalizedGlassIntensity = sanitizeGlassIntensity(glassIntensity)
    val view = LocalView.current
    val preferOpaqueMaterials = rememberPreferOpaqueMaterials()
    val blurAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2 &&
        view.isHardwareAccelerated &&
        !preferOpaqueMaterials &&
        normalizedGlassIntensity > 0f
    val liquidAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && blurAvailable
    // Use only one capture engine and disable both sources and consumers at zero.
    // An empty effect still records RenderNodes; it is not an off switch.
    val hazeAvailable = blurAvailable && !liquidAvailable
    val hazeState = rememberHazeState(blurEnabled = hazeAvailable)
    val backgroundBackdrop = rememberLayerBackdrop()
    val contentBackdrop = rememberLayerBackdrop()
    val floatingBackdrop = rememberCombinedBackdrop(backgroundBackdrop, contentBackdrop)
    val environment = remember(
        darkTheme,
        hazeState,
        blurAvailable,
        motionEnabled,
        preferOpaqueMaterials,
        normalizedGlassIntensity, backgroundBackdrop, contentBackdrop, floatingBackdrop, liquidAvailable,
    ) {
        GlassEnvironment(
            darkTheme = darkTheme,
            hazeState = hazeState,
            blurAvailable = blurAvailable,
            motionEnabled = motionEnabled,
            preferOpaqueMaterials = preferOpaqueMaterials,
            glassIntensity = normalizedGlassIntensity,
            backgroundBackdrop = backgroundBackdrop,
            contentBackdrop = contentBackdrop,
            floatingBackdrop = floatingBackdrop,
            liquidAvailable = liquidAvailable,
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

    val context = LocalContext.current
    val systemHaptic = LocalHapticFeedback.current
    val optionalHaptic = remember(context, systemHaptic) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (FeedbackPreferences.enabled(context, FeedbackOption.HAPTICS)) {
                    systemHaptic.performHapticFeedback(hapticFeedbackType)
                }
            }
        }
    }
    CompositionLocalProvider(
        LocalHapticFeedback provides optionalHaptic,
        LocalThemePalette provides palette,
        LocalThemeGeometry provides geometry,
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
                    modifier = (if (hazeAvailable) {
                        Modifier.hazeSource(
                            state = hazeState,
                            zIndex = 0f,
                        )
                    } else {
                        Modifier
                    }).then(if (liquidAvailable) Modifier.layerBackdrop(backgroundBackdrop) else Modifier),
                )
                content()
            }
        }
    }
}

@Composable
fun Modifier.glassHazeSource(zIndex: Float): Modifier {
    val environment = LocalGlassEnvironment.current
    // Panels sample a separate background layer; only the dock samples page content.
    return if (environment.liquidAvailable) {
        if (zIndex == 1f && environment.contentBackdrop != null) {
            layerBackdrop(environment.contentBackdrop)
        } else this
    } else if (environment.blurAvailable) {
        hazeSource(state = environment.hazeState, zIndex = zIndex)
    } else this
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
