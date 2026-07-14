package cn.qzxy.hotwater.designsystem.theme

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp

private const val GrainTextureSize = 64
private const val GrainSeed = 0x4A6F7921

@Composable
fun GlassMeshBackground(tokens: GlassTokens, modifier: Modifier = Modifier) {
    val grainTexture = remember(tokens.grainColor) {
        createGrainTexture(tokens.grainColor)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val direction = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to tokens.backgroundStart,
                        0.52f to tokens.backgroundMiddle,
                        1f to tokens.backgroundEnd,
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height * 1.08f),
                )
                val vignette = Brush.radialGradient(
                    colors = listOf(Color.Transparent, tokens.backgroundVignette),
                    center = Offset(size.width * 0.48f, size.height * 0.42f),
                    radius = size.maxDimension * 0.78f,
                )
                val grain = ShaderBrush(
                    ImageShader(
                        image = grainTexture,
                        tileModeX = TileMode.Repeated,
                        tileModeY = TileMode.Repeated,
                    )
                )

                onDrawBehind {
                    drawRect(brush = direction)
                    drawRect(brush = vignette)
                    drawRect(brush = grain)
                }
            },
    )
}

private fun createGrainTexture(color: Color): ImageBitmap {
    val bitmap = Bitmap.createBitmap(
        GrainTextureSize,
        GrainTextureSize,
        Bitmap.Config.ARGB_8888,
    )
    val pixels = IntArray(GrainTextureSize * GrainTextureSize)
    var state = GrainSeed
    for (index in pixels.indices) {
        state = state * 1_664_525 + 1_013_904_223
        val sample = (state ushr 24) / 255f
        pixels[index] = color.copy(alpha = color.alpha * (0.18f + sample * 0.82f)).toArgb()
    }
    bitmap.setPixels(
        pixels,
        0,
        GrainTextureSize,
        0,
        0,
        GrainTextureSize,
        GrainTextureSize,
    )
    return bitmap.asImageBitmap()
}

@Composable
fun Modifier.glassConvex(
    cornerRadius: Dp,
    tokens: GlassTokens = LocalGlassTokens.current,
): Modifier = glassMaterial(
    material = GlassMaterial.Panel,
    cornerRadius = cornerRadius,
    tokens = tokens,
)

@Composable
fun Modifier.glassConcave(
    cornerRadius: Dp,
    tokens: GlassTokens = LocalGlassTokens.current,
): Modifier = glassMaterial(
    material = GlassMaterial.Input,
    cornerRadius = cornerRadius,
    tokens = tokens,
)

@Composable
fun Modifier.glassConvexOverlay(
    cornerRadius: Dp,
    tokens: GlassTokens = LocalGlassTokens.current,
): Modifier = glassMaterial(
    material = GlassMaterial.Overlay,
    cornerRadius = cornerRadius,
    tokens = tokens,
)

@Composable
fun Modifier.glassConcaveOverlay(
    cornerRadius: Dp,
    tokens: GlassTokens = LocalGlassTokens.current,
): Modifier = glassMaterial(
    material = GlassMaterial.Overlay,
    cornerRadius = cornerRadius,
    pressed = true,
    tokens = tokens,
)
