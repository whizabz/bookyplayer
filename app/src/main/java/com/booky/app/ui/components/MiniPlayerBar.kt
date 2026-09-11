package com.booky.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.booky.app.data.Audiobook

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiniPlayerBar(
    book: Audiobook,
    isPlaying: Boolean,
    progress: Float,
    onOpenPlayer: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkipBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FloatingToolbarDefaults.standardFloatingToolbarColors()
    Surface(
        modifier = modifier
            .wrapContentHeight()
            .heightIn(min = FloatingToolbarDefaults.ContainerSize, max = 80.dp),
        shape = FloatingToolbarDefaults.ContainerShape,
        color = colors.toolbarContainerColor,
        contentColor = colors.toolbarContentColor,
        shadowElevation = 3.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.clickable(onClick = onOpenPlayer),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookCover(book = book, modifier = Modifier.size(40.dp), shape = CircleShape)
                Column(
                    modifier = Modifier
                        .widthIn(max = 180.dp)
                        .padding(start = 12.dp, end = 4.dp),
                ) {
                    Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = book.currentChapterTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .widthIn(min = 72.dp)
                            .height(4.dp),
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
                IconButton(onClick = onSkipBack) {
                    Icon(BookyIcons.skipBack, contentDescription = "Back 10 seconds")
                }
            }
            FilledIconButton(
                onClick = onTogglePlay,
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    imageVector = if (isPlaying) BookyIcons.pause else BookyIcons.play,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                )
            }
        }
    }
}
