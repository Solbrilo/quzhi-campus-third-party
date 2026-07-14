package cn.qzxy.hotwater.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.designsystem.theme.GlassMaterial
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    height: Dp = 52.dp,
    cornerRadius: Dp = 22.dp,
    icon: (@Composable () -> Unit)? = null,
    material: GlassMaterial = GlassMaterial.Control,
    hapticFeedback: (() -> HapticFeedbackType?)? = {
        HapticFeedbackType.SegmentTick
    },
) {
    val tokens = LocalGlassTokens.current
    val interactionEnabled = enabled && !isLoading

    GlassPressable(
        onClick = onClick,
        enabled = interactionEnabled,
        material = material,
        cornerRadius = cornerRadius,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        disabledAlpha = if (isLoading && enabled) 1f else 0.52f,
        hapticFeedback = hapticFeedback,
        modifier = modifier
            .heightIn(min = height)
            .defaultMinSize(minWidth = 120.dp)
            .semantics {
                if (isLoading) stateDescription = "正在处理：$text"
            },
    ) {
        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = tokens.accentPrimary,
                strokeWidth = 2.dp,
            )

            icon != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.width(8.dp))
                Text(
                    text = text,
                    color = tokens.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            else -> Text(
                text = text,
                color = tokens.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
