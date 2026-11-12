package cn.qzxy.hotwater.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** A page owns its backdrop and hit area, including the space between controls. */
@Composable
internal fun GlassPageSurface(
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    solidBackground: Boolean = false,
    content: @Composable () -> Unit,
) {
    val parent = LocalGlassEnvironment.current
    val tokens = LocalGlassTokens.current
    val background = rememberLayerBackdrop()
    val foreground = rememberLayerBackdrop()
    val combined = rememberCombinedBackdrop(background, foreground)
    val haze = rememberHazeState(blurEnabled = parent.blurAvailable && !parent.liquidAvailable)
    val environment = parent.copy(hazeState = haze, backgroundBackdrop = background,
        contentBackdrop = foreground, floatingBackdrop = combined)
    CompositionLocalProvider(LocalGlassEnvironment provides environment) {
        Box(modifier.fillMaxSize().background(tokens.meshBase).then(if (!interactive) Modifier.pointerInput(Unit) {
            awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } }
        } else Modifier)) {
            // A sibling hit target catches only the gaps, without cancelling child scroll/transform gestures.
            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            })
            Box(Modifier.fillMaxSize().then(
                if (parent.liquidAvailable) Modifier.layerBackdrop(background)
                else if (parent.blurAvailable) Modifier.hazeSource(haze, zIndex = 0f) else Modifier)) {
                if (solidBackground) Box(Modifier.fillMaxSize().background(tokens.meshBase))
                else GlassMeshBackground(tokens)
                if (!solidBackground && LocalThemePalette.current == ThemePalette.CUSTOM && LocalCustomTheme.current.image) {
                    CustomThemeBackground(LocalCustomTheme.current)
                }
            }
            content()
        }
    }
}
