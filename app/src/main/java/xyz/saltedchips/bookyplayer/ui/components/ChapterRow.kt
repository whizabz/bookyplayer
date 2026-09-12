package xyz.saltedchips.bookyplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun ChapterRow(
    index: Int,
    title: String,
    durationMs: Long,
    progress: Float,
    selected: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    complete: Boolean = false,
    selecting: Boolean = false,
    checked: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val finished = complete
    val faded = finished && !selecting && !selected
    val showProgress = !finished && progress > 0.02f && progress < 0.98f
    Column(modifier) {
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp, end = 4.dp),
                color = colors.outlineVariant.copy(alpha = 0.6f),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    when {
                        checked -> colors.secondaryContainer
                        selected -> colors.surfaceContainerHigh
                        else -> colors.background
                    },
                )
                .graphicsLayer { alpha = if (faded) 0.45f else 1f }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onLongClickLabel = if (onLongClick != null) "Select" else null,
                )
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(28.dp),
                color = if (selected) colors.primary else colors.onSurfaceVariant,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showProgress) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        strokeCap = StrokeCap.Butt,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
            }
            when {
                selecting -> Checkbox(checked = checked, onCheckedChange = null)
                finished -> Icon(
                    BookyIcons.check,
                    contentDescription = "Completed",
                    tint = colors.primary,
                    modifier = Modifier.size(22.dp),
                )
                durationMs > 0L -> Text(
                    text = formatMinutes(durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
