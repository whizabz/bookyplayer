package xyz.saltedchips.bookyplayer.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Text with one range marked by a highlight whose corners are rounded, drawn one rounded box per
 * line of the range. (A plain background span would be a flat rectangle.)
 *
 * [onHighlightBounds] reports the highlight's top and bottom on screen (root coordinates), and
 * null when the highlight is gone or this text leaves the screen's composition.
 */
@Composable
fun RoundedHighlightText(
    text: String,
    style: TextStyle,
    highlightStart: Int,
    highlightEnd: Int,
    highlightColor: Color,
    modifier: Modifier = Modifier,
    onHighlightBounds: ((Pair<Float, Float>?) -> Unit)? = null,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val hasHighlight = highlightEnd > highlightStart
    if (onHighlightBounds != null) {
        DisposableEffect(Unit) {
            onDispose { onHighlightBounds(null) }
        }
    }
    BasicText(
        text = text,
        style = style,
        onTextLayout = { layout = it },
        modifier = modifier
            .onGloballyPositioned { positioned ->
                coordinates = positioned
                val result = layout
                if (onHighlightBounds != null && hasHighlight && result != null && positioned.isAttached) {
                    val first = result.getLineForOffset(highlightStart.coerceIn(0, text.length))
                    val last = result.getLineForOffset((highlightEnd - 1).coerceIn(0, text.length))
                    val top = positioned.localToRoot(Offset(0f, result.getLineTop(first))).y
                    val bottom = positioned.localToRoot(Offset(0f, result.getLineBottom(last))).y
                    onHighlightBounds(top to bottom)
                } else if (onHighlightBounds != null) {
                    onHighlightBounds(null)
                }
            }
            .drawBehind {
                val result = layout
                if (hasHighlight && result != null) {
                    drawRoundedRange(
                        result,
                        highlightStart.coerceIn(0, text.length),
                        highlightEnd.coerceIn(0, text.length),
                        highlightColor,
                    )
                }
            },
    )
}

private fun DrawScope.drawRoundedRange(result: TextLayoutResult, start: Int, end: Int, color: Color) {
    if (end <= start) return
    val firstLine = result.getLineForOffset(start)
    val lastLine = result.getLineForOffset((end - 1).coerceAtLeast(start))
    val padX = 5.dp.toPx()
    val padY = 1.dp.toPx()
    val radius = CornerRadius(12.dp.toPx())
    for (line in firstLine..lastLine) {
        val left = if (line == firstLine) result.getHorizontalPosition(start, usePrimaryDirection = true) else result.getLineLeft(line)
        val right = if (line == lastLine) result.getHorizontalPosition(end, usePrimaryDirection = true) else result.getLineRight(line)
        if (right <= left) continue
        drawRoundRect(
            color = color,
            topLeft = Offset(left - padX, result.getLineTop(line) - padY),
            size = Size(right - left + padX * 2, result.getLineBottom(line) - result.getLineTop(line) + padY * 2),
            cornerRadius = radius,
        )
    }
}
