package cn.qzxy.hotwater.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens

/** Shared menu treatment for bill periods and message sources. */
@Composable
internal fun <T> FilterMenu(expanded: Boolean, onDismiss: () -> Unit, options: List<T>,
    label: (T) -> String, onSelected: (T) -> Unit) {
    val tokens = LocalGlassTokens.current
    DropdownMenu(expanded, onDismiss, shape = RoundedCornerShape(16.dp),
        containerColor = tokens.fallbackSurface, tonalElevation = 0.dp, shadowElevation = 8.dp,
        border = BorderStroke(1.dp, tokens.borderHi.copy(alpha = .55f))) {
        options.forEach { option ->
            DropdownMenuItem(text = { Text(label(option), color = tokens.textPrimary,
                fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                onClick = { onDismiss(); onSelected(option) },
                colors = MenuDefaults.itemColors(textColor = tokens.textPrimary,
                    leadingIconColor = tokens.textSecondary, trailingIconColor = tokens.textSecondary,
                    disabledTextColor = tokens.textTertiary, disabledLeadingIconColor = tokens.textTertiary,
                    disabledTrailingIconColor = tokens.textTertiary))
        }
    }
}
