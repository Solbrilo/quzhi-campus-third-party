package cn.qzxy.hotwater.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import cn.qzxy.hotwater.designsystem.theme.DEFAULT_GLASS_INTENSITY
import cn.qzxy.hotwater.designsystem.theme.GlassTheme
import cn.qzxy.hotwater.designsystem.theme.ThemeMode
import cn.qzxy.hotwater.designsystem.theme.glassHazeSource
import cn.qzxy.hotwater.designsystem.theme.GlassMaterial
import cn.qzxy.hotwater.designsystem.theme.glassMaterial
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

@SdkSuppress(minSdkVersion = 32)
class BottomDockGlassInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pressingEnlargesLensAndDraggingSelectsThenRestoresSize() {
        val selected = androidx.compose.runtime.mutableIntStateOf(0)
        composeRule.setContent {
            GlassTheme(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.fillMaxSize()) {
                    BottomDock(listOf("用水", "账单", "设置"), selected.intValue,
                        onSelected = { index, _ -> selected.intValue = index }, onDragStateChanged = {},
                        modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp))
                }
            }
        }
        val indicator = composeRule.onNodeWithTag("bottom_dock_indicator")
        (0..2).forEach { composeRule.onNodeWithTag("bottom_dock_icon_$it", useUnmergedTree = true).assertIsDisplayed() }
        val dockResting = composeRule.onNodeWithTag("bottom_dock").fetchSemanticsNode().boundsInRoot.width
        val resting = indicator.fetchSemanticsNode().boundsInRoot.height
        composeRule.onNodeWithTag("bottom_dock").performTouchInput { down(Offset(width / 6f, height / 2f)) }
        composeRule.waitForIdle()
        assertTrue(indicator.fetchSemanticsNode().boundsInRoot.height > resting * 1.15f)
        assertTrue(composeRule.onNodeWithTag("bottom_dock").fetchSemanticsNode().boundsInRoot.width > dockResting * 1.01f)
        saveReviewImage("dock-pressed", composeRule.onRoot().captureToImage())
        composeRule.onNodeWithTag("bottom_dock").performTouchInput {
            moveTo(Offset(width * 5f / 6f, height / 2f), delayMillis = 320)
            up()
        }
        composeRule.onNodeWithTag("bottom_dock_tab_2").assertIsSelected()
        composeRule.waitForIdle()
        assertTrue(abs(indicator.fetchSemanticsNode().boundsInRoot.height - resting) < 1f)
    }

    @Test
    @SdkSuppress(minSdkVersion = 33)
    fun lensSamplesDockLabelsAbovePageContent() {
        val labels = mutableStateOf(listOf("用水", "账单", "设置"))
        composeRule.setContent {
            GlassTheme(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.fillMaxSize()) {
                    BottomDock(labels.value, 0, { _, _ -> }, {},
                        Modifier.align(Alignment.BottomCenter).padding(20.dp))
                }
            }
        }
        composeRule.onNodeWithTag("bottom_dock").performTouchInput { down(Offset(width / 6f, height / 2f)) }
        val original = composeRule.onNodeWithTag("bottom_dock_indicator").captureToImage().toPixels()
        composeRule.runOnIdle { labels.value = listOf("测试文字", "账单", "设置") }
        val changed = composeRule.onNodeWithTag("bottom_dock_indicator").captureToImage().toPixels()
        assertTrue("The lens must include the dock's own text, not only the page", changedPixelRatio(original, changed) > 0.005)
        composeRule.onNodeWithTag("bottom_dock").performTouchInput { up() }
    }

    private fun saveReviewImage(name: String, image: ImageBitmap) {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = android.graphics.Bitmap.createBitmap(image.toPixels(), image.width, image.height, android.graphics.Bitmap.Config.ARGB_8888)
        java.io.File(context.cacheDir, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test
    fun recordedPageAndDockSurviveRepeatedZeroIntensityTransitions() {
        val intensity = mutableFloatStateOf(0f)
        val theme = mutableStateOf(ThemeMode.DARK)
        composeRule.setContent {
            GlassTheme(themeMode = theme.value, glassIntensity = intensity.floatValue) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().glassHazeSource(1f)) {
                        Box(Modifier.size(240.dp).glassMaterial(GlassMaterial.Panel, 24.dp))
                    }
                    BottomDock(
                        labels = listOf("用水", "账单", "设置"), selectedIndex = 0,
                        onSelected = { _, _ -> }, onDragStateChanged = {},
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        listOf(ThemeMode.DARK, ThemeMode.LIGHT).forEach { mode ->
            composeRule.runOnIdle { theme.value = mode }
            listOf(0f, 0.75f, 0.01f, 0f, 1f, 0f, 0.75f).forEach { value ->
                android.util.Log.i("GlassRegression", "Dock theme=$mode intensity=$value")
                composeRule.runOnIdle { intensity.floatValue = value }
                assertTrue(captureDockPixels().isNotEmpty())
            }
        }
    }

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
