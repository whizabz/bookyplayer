package xyz.saltedchips.bookyplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.saltedchips.bookyplayer.data.Audiobook
import xyz.saltedchips.bookyplayer.data.formatMinutes

fun chapterListenProgress(
    index: Int,
    durationMs: Long,
    bookPositionMs: Long,
    chapterStartMs: List<Long>,
    chapterDurationsMs: List<Long>,
    savedPositionsMs: Map<Int, Long>,
): Float {
    if (durationMs <= 0L) return 0f
    val start = chapterStartMs.getOrNull(index)
        ?: chapterDurationsMs.take(index).sumOf { it.coerceAtLeast(0L) }
    val end = start + durationMs
    val fromBook = when {
        bookPositionMs >= end - 2_000L -> durationMs
        bookPositionMs > start -> (bookPositionMs - start).coerceAtMost(durationMs)
        else -> 0L
    }
    val fromSaved = savedPositionsMs[index] ?: 0L
    return (maxOf(fromBook, fromSaved).toFloat() / durationMs).coerceIn(0f, 1f)
}

fun Audiobook.chapterListenProgress(
    index: Int,
    bookPositionMs: Long,
    savedPositionsMs: Map<Int, Long>,
): Float {
    val duration = chapterDurationsMs.getOrNull(index) ?: 0L
    return chapterListenProgress(
        index = index,
        durationMs = duration,
        bookPositionMs = bookPositionMs,
        chapterStartMs = chapterStartMs,
        chapterDurationsMs = chapterDurationsMs,
        savedPositionsMs = savedPositionsMs,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChapterRow(
    index: Int,
    title: String,
    durationMs: Long,
    progress: Float,
    selected: Boolean,
    itemShapes: ListItemShapes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    complete: Boolean = false,
    playing: Boolean = false,
    selecting: Boolean = false,
    checked: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val finished = complete
    val faded = finished && !selecting && !selected
    val highlight = selected || checked
    val container by animateColorAsState(
        targetValue = if (highlight) scheme.secondaryContainer else scheme.surfaceContainer,
        animationSpec = motion.defaultEffectsSpec(),
        label = "chapter-row-container",
    )
    val remaining = (durationMs - (progress * durationMs).toLong()).coerceAtLeast(0)
    val remainingLabel = when {
        finished || progress >= 1f -> "Finished"
        progress <= 0f -> if (durationMs > 0L) formatMinutes(durationMs) else ""
        else -> "${formatMinutes(remaining)} left"
    }
    val showProgress = !finished && progress > 0.02f && progress < 0.98f
    Surface(
        shape = itemShapes.shape,
        color = container,
        contentColor = if (highlight) scheme.onSecondaryContainer else scheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .clip(itemShapes.shape)
            .graphicsLayer { alpha = if (faded) 0.6f else 1f },
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                        onLongClickLabel = if (onLongClick != null) "Select" else null,
                    )
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(40.dp),
                    color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, end = 12.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (remainingLabel.isNotEmpty()) {
                        Text(
                            text = remainingLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                when {
                    selecting -> Checkbox(checked = checked, onCheckedChange = null)
                    finished -> Icon(
                        BookyIcons.check,
                        contentDescription = "Completed",
                        tint = scheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            if (showProgress) {
                val barModifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp)
                if (playing) {
                    LinearWavyProgressIndicator(
                        progress = { progress },
                        modifier = barModifier,
                        gapSize = 0.dp,
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = barModifier,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
}
