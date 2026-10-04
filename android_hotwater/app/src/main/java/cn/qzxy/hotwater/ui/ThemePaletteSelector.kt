package cn.qzxy.hotwater.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.designsystem.component.GlassPressable
import cn.qzxy.hotwater.designsystem.theme.*

@Composable
internal fun ThemePaletteSelector(selected: ThemePalette, onSelected: (ThemePalette) -> Unit) {
    val tokens = LocalGlassTokens.current
    val dark = LocalGlassEnvironment.current.darkTheme
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        GlassPressable(
            onClick = { expanded = true }, cornerRadius = 16.dp,
            modifier = Modifier.fillMaxWidth().testTag("theme_palette_selector"),
            contentPadding = PaddingValues(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("主题配色", color = tokens.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(selected.label, color = tokens.textSecondary, fontSize = 13.sp)
                }
                Box(Modifier.size(18.dp).background(tokens.accentPrimary, CircleShape))
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Outlined.KeyboardArrowDown, "选择主题", tint = tokens.textSecondary)
            }
        }
        DropdownMenu(
            expanded = expanded, onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(18.dp), containerColor = tokens.fallbackSurface,
            tonalElevation = 0.dp, shadowElevation = 8.dp,
            border = BorderStroke(1.dp, tokens.borderHi),
        ) {
            ThemePalette.entries.forEach { palette ->
                val active = selected == palette
                DropdownMenuItem(
                    modifier = Modifier.widthIn(min = 220.dp)
                        .background(if (active) tokens.accentPrimary.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                        .testTag("theme_palette_${palette.storageValue}"),
                    text = { Text(palette.label, color = if (active) tokens.accentPrimary else tokens.textPrimary,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = { Box(Modifier.size(18.dp).background(palette.tokens(dark).accentPrimary, CircleShape)) },
                    trailingIcon = { if (active) Icon(Icons.Outlined.Check, "已选中", tint = tokens.accentPrimary) },
                    onClick = { expanded = false; onSelected(palette) },
                )
            }
        }
    }
}
