package xyz.saltedchips.bookyplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

@Composable
fun BoxScope.RowProgressFill(progress: Float, color: Color) {
    Canvas(Modifier.matchParentSize()) {
        drawRect(
            color = color,
            size = Size(size.width * progress.coerceIn(0f, 1f) + 2f, size.height),
        )
    }
}
