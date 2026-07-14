package cn.qzxy.hotwater.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ConfigTransferActionsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun narrowWidthStacksActions() {
        setActionsContent(width = 283.dp, fontScale = 1f)

        val exportBounds = actionBounds(ExportLabel)
        val importBounds = actionBounds(ImportLabel)

        assertTrue(importBounds.top > exportBounds.bottom)
        assertLabelsAreSingleLine()
    }

    @Test
    fun standardWidthKeepsActionsInOneRow() {
        setActionsContent(width = 320.dp, fontScale = 1f)

        val exportBounds = actionBounds(ExportLabel)
        val importBounds = actionBounds(ImportLabel)

        assertEquals(exportBounds.top, importBounds.top, 1f)
        assertTrue(importBounds.left > exportBounds.right)
        assertLabelsAreSingleLine()
    }

    @Test
    fun enlargedTextStacksActionsAtStandardWidth() {
        setActionsContent(width = 320.dp, fontScale = 1.3f)

        val exportBounds = actionBounds(ExportLabel)
        val importBounds = actionBounds(ImportLabel)

        assertTrue(importBounds.top > exportBounds.bottom)
        assertLabelsAreSingleLine()
    }

    @Test
    fun actionsInvokeTheirOwnCallbacks() {
        var exportClicks = 0
        var importClicks = 0
        setActionsContent(
            width = 320.dp,
            fontScale = 1f,
            onExport = { exportClicks++ },
            onImport = { importClicks++ },
        )

        composeRule.onNodeWithText(ExportLabel).performClick()
        composeRule.onNodeWithText(ImportLabel).performClick()

        composeRule.runOnIdle {
            assertEquals(1, exportClicks)
            assertEquals(1, importClicks)
        }
    }

    private fun setActionsContent(
        width: Dp,
        fontScale: Float,
        onExport: () -> Unit = {},
        onImport: () -> Unit = {},
    ) {
        composeRule.setContent {
            val deviceDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = deviceDensity.density,
                    fontScale = fontScale,
                ),
            ) {
                Box(modifier = Modifier.width(width)) {
                    ConfigTransferActions(
                        busy = false,
                        onExport = onExport,
                        onImport = onImport,
                    )
                }
            }
        }
    }

    private fun actionBounds(label: String) = composeRule
        .onNodeWithText(label)
        .fetchSemanticsNode()
        .boundsInRoot

    private fun assertLabelsAreSingleLine() {
        val maximumSingleLineHeight = with(composeRule.density) { 30.dp.toPx() }
        listOf(ExportLabel, ImportLabel).forEach { label ->
            val textBounds = composeRule
                .onNodeWithText(label, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
            assertTrue(
                "$label should remain on one line, but was ${textBounds.height}px high",
                textBounds.height <= maximumSingleLineHeight,
            )
        }
    }

    private companion object {
        const val ExportLabel = "导出配置"
        const val ImportLabel = "导入配置"
    }
}
