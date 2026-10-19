package xyz.saltedchips.bookyplayer.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Text with one range marked by a highlight whose corners are rounded, drawn one rounded box per
 * line of the range. (A plain background span would be a flat rectangle.)
 */
@Composable
fun RoundedHighlightText(
    text: String,
    style: TextStyle,
    highlightStart: Int,
    highlightEnd: Int,
    highlightColor: Color,
    modifier: Modifier = Modifier,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val hasHighlight = highlightEnd > highlightStart
    BasicText(
        text = text,
        style = style,
        onTextLayout = { layout = it },
        modifier = modifier.drawBehind {
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
    val padX = 4.dp.toPx()
    val padY = 1.dp.toPx()
    val round = CornerRadius(8.dp.toPx())
    val square = CornerRadius.Zero
    for (line in firstLine..lastLine) {
        val left = if (line == firstLine) result.getHorizontalPosition(start, usePrimaryDirection = true) else result.getLineLeft(line)
        val right = if (line == lastLine) result.getHorizontalPosition(end, usePrimaryDirection = true) else result.getLineRight(line)
        if (right <= left) continue
        // Only the very start (first line, left side) and the very end (last line, right side) are
        // rounded; where the highlight wraps from one line to the next, the edges stay straight.
        val isFirst = line == firstLine
        val isLast = line == lastLine
        val box = RoundRect(
            rect = Rect(
                left = if (isFirst) left - padX else left,
                top = result.getLineTop(line) - padY,
                right = if (isLast) right + padX else right,
                bottom = result.getLineBottom(line) + padY,
            ),
            topLeft = if (isFirst) round else square,
            bottomLeft = if (isFirst) round else square,
            topRight = if (isLast) round else square,
            bottomRight = if (isLast) round else square,
        )
        drawPath(Path().apply { addRoundRect(box) }, color)
    }
}
