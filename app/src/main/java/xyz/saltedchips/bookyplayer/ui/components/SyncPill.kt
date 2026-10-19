package xyz.saltedchips.bookyplayer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * A compact floating "Sync to audio" pill (about 32dp tall). With a [hazeState] it is frosted
 * glass with the mini player's blur, tint, border and shadow; without one it is a flat tonal pill.
 */
@Composable
fun SyncPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (colors.background.luminance() < 0.5f) {
        colors.surfaceContainerLowest
    } else {
        colors.surfaceContainerHighest
    }
    val glass = hazeState != null
    Row(
        modifier = modifier
            .dropShadow(CircleShape) {
                radius = 28.dp.toPx()
                color = Color.Black
                alpha = 0.16f
                offset = Offset(0f, 6.dp.toPx())
            }
            .clip(CircleShape)
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(hazeState) {
                        blurRadius = 36.dp
                        tints = listOf(HazeTint(container.copy(alpha = 0.5f)))
                        noiseFactor = 0.03f
                    }
                } else {
                    Modifier
                },
            )
            .background(container.copy(alpha = if (glass) 0.88f else 0.95f))
            .border(
                BorderStroke(0.5.dp, colors.secondaryContainer.copy(alpha = 0.45f)),
                CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(BookyIcons.sync, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.onSurface)
        Spacer(Modifier.width(6.dp))
        Text("Sync to audio", style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
    }
}
