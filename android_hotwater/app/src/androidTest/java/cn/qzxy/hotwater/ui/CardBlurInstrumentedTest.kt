package cn.qzxy.hotwater.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cn.qzxy.hotwater.designsystem.theme.*
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CardBlurInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun normalAndPinnedTitleCardsBothBlurTheirBackgroundAtZeroLiquidStrength() {
        val radius = mutableFloatStateOf(0f)
        rule.setContent {
            GlassTheme(ThemeMode.LIGHT, glassIntensity = 0f, palette = ThemePalette.CUSTOM) {
                val backdrop = rememberLayerBackdrop()
                CompositionLocalProvider(
                    LocalCustomTheme provides CustomThemeSettings(cardBlur = radius.floatValue),
                    LocalGlassEnvironment provides LocalGlassEnvironment.current.copy(backgroundBackdrop = backdrop),
                ) {
                    Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                        val stripe = 8.dp.toPx()
                        repeat((size.width / stripe).toInt() + 1) { x ->
                            drawRect(if (x % 2 == 0) Color.Black else Color.White,
                                Offset(x * stripe, 0f), Size(stripe, size.height))
                        }
                    }
                    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Box(Modifier.fillMaxWidth().height(160.dp).testTag("normal")
                            .glassMaterial(GlassMaterial.Panel, 24.dp))
                        CollapsingGlassCard(Modifier.testTag("pinned"), header = { Text("卡片标题") }) {
                            Spacer(Modifier.height(100.dp))
                        }
                    }
                }
            }
        }
        val clear = listOf("normal", "pinned").associateWith { stripeVariation(rule.onNodeWithTag(it).captureToImage()) }
        rule.runOnIdle { radius.floatValue = 32f }
        rule.waitForIdle()
        listOf("normal", "pinned").forEach { tag ->
            val blurred = stripeVariation(rule.onNodeWithTag(tag).captureToImage())
            assertTrue("$tag must blur stripes, clear=${clear.getValue(tag)}, blurred=$blurred",
                blurred < clear.getValue(tag) * .5f)
        }
    }

    // Sample blank interior, so text, borders and corner clipping cannot mask the result.
    private fun stripeVariation(image: ImageBitmap): Float {
        val pixels = image.toPixelMap()
        val values = (image.width / 4 until image.width * 3 / 4).map { pixels[it, image.height * 3 / 4].red }
        val average = values.average().toFloat()
        return values.sumOf { ((it - average) * (it - average)).toDouble() }.toFloat() / values.size
    }
}
