package cn.qzxy.hotwater.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.qzxy.hotwater.designsystem.theme.LocalGlassTokens
import cn.qzxy.hotwater.designsystem.theme.glassConcave

@Composable
fun GlassInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    errorMessage: String? = null,
    focusRequester: FocusRequester? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val tokens = LocalGlassTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val outlineColor = when {
        errorMessage != null -> tokens.accentError
        isFocused -> tokens.accentPrimary
        else -> null
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                color = if (errorMessage == null) tokens.textSecondary else tokens.accentError,
                fontSize = 12.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        Box(
            modifier = Modifier
                .heightIn(min = 52.dp)
                .fillMaxWidth()
                .glassConcave(cornerRadius = 16.dp, tokens)
                .then(
                    if (outlineColor != null) Modifier.drawBehind {
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                listOf(outlineColor, outlineColor.copy(alpha = 0.38f))
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
                textStyle = LocalTextStyle.current.copy(color = tokens.textPrimary, fontSize = 15.sp),
                singleLine = true,
                interactionSource = interactionSource,
                cursorBrush = SolidColor(tokens.accentPrimary),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(placeholder, color = tokens.textTertiary, fontSize = 15.sp)
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .then(
                        if (focusRequester != null) Modifier.focusRequester(focusRequester)
                        else Modifier
                    )
                    .semantics {
                        if (label.isNotBlank()) contentDescription = label
                        errorMessage?.let { error(it) }
                    },
            )
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = errorMessage,
                color = tokens.accentError,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
