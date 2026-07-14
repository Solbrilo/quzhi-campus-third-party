package cn.qzxy.hotwater.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import cn.qzxy.hotwater.designsystem.theme.DEFAULT_GLASS_INTENSITY
import cn.qzxy.hotwater.designsystem.theme.GlassTheme
import cn.qzxy.hotwater.designsystem.theme.ThemeMode
import cn.qzxy.hotwater.designsystem.theme.glassHazeSource
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

@SdkSuppress(minSdkVersion = 32)
class BottomDockGlassInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dockSamplesSiblingContentAndRespondsToIntensity() {
        val patterned = mutableStateOf(true)
        val intensity = mutableFloatStateOf(DEFAULT_GLASS_INTENSITY)

        composeRule.setContent {
            GlassTheme(
                themeMode = ThemeMode.LIGHT,
                glassIntensity = intensity.floatValue,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .glassHazeSource(zIndex = 1f),
                    ) {
                        if (patterned.value) {
                            val stripeWidth = 10.dp.toPx()
                            var left = 0f
                            var stripe = 0
                            while (left < size.width) {
                                drawRect(
                                    color = if (stripe % 2 == 0) StripeBlue else StripeCoral,
                                    topLeft = Offset(left, 0f),
                                    size = Size(stripeWidth, size.height),
                                )
                                left += stripeWidth
                                stripe += 1
                            }
                        } else {
                            drawRect(color = UniformSource)
                        }
                    }

                    BottomDock(
                        labels = listOf("操作", "记录", "配置"),
                        selectedIndex = 0,
                        onSelected = { _, _ -> },
                        onDragStateChanged = {},
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                    )
                }
            }
        }

        val patternedDefault = captureDockPixels()
        composeRule.runOnIdle { patterned.value = false }
        val uniformDefault = captureDockPixels()
        assertTrue(
            "The Dock must sample its sibling Haze source",
            changedPixelRatio(patternedDefault, uniformDefault) > 0.05,
        )

        composeRule.runOnIdle {
            patterned.value = true
            intensity.floatValue = 0f
        }
        val zeroIntensity = captureDockPixels()
        composeRule.runOnIdle { intensity.floatValue = 1f }
        val fullIntensity = captureDockPixels()

        assertTrue(
            "0% and 75% glass intensity must render differently",
            changedPixelRatio(zeroIntensity, patternedDefault) > 0.05,
        )
        assertTrue(
            "75% and 100% glass intensity must render differently",
            changedPixelRatio(patternedDefault, fullIntensity) > 0.01,
        )
    }

    private fun captureDockPixels(): IntArray {
        composeRule.waitForIdle()
        val image = composeRule.onNodeWithTag("bottom_dock").captureToImage()
        return image.toPixels()
    }

    private fun ImageBitmap.toPixels(): IntArray = IntArray(width * height).also { pixels ->
        readPixels(pixels)
    }

    private fun changedPixelRatio(first: IntArray, second: IntArray): Double {
        require(first.size == second.size)
        val changed = first.indices.count { index ->
            channelDistance(first[index], second[index]) >= MinimumChannelDistance
        }
        return changed.toDouble() / first.size
    }

    private fun channelDistance(first: Int, second: Int): Int {
        val red = abs((first shr 16 and 0xFF) - (second shr 16 and 0xFF))
        val green = abs((first shr 8 and 0xFF) - (second shr 8 and 0xFF))
        val blue = abs((first and 0xFF) - (second and 0xFF))
        return red + green + blue
    }

    private companion object {
        const val MinimumChannelDistance = 8
        val StripeBlue = Color(0xFF2E79C7)
        val StripeCoral = Color(0xFFE36F5B)
        val UniformSource = Color(0xFF8794A1)
    }
}
