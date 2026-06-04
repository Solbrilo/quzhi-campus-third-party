package cn.qzxy.hotwater.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.designsystem.theme.GlassAccent
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.glassConcave

@Composable
fun GlassInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isFocused: Boolean = false,
) {
    val tokens = LocalGlassTokens.current

    Box(
        modifier = modifier
            .height(52.dp)
            .fillMaxWidth()
            .glassConcave(cornerRadius = 16.dp, tokens)
            .then(
                if (isFocused) Modifier.drawBehind {
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(GlassAccent.primary, GlassAccent.primary.copy(alpha = 0.4f))
                        ),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                } else Modifier
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(color = tokens.textPrimary, fontSize = 15.sp),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, color = tokens.textTertiary, fontSize = 15.sp)
                inner()
            }
        )
    }
}
