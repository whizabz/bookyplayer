package xyz.saltedchips.bookyplayer.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.saltedchips.bookyplayer.data.Audiobook
import xyz.saltedchips.bookyplayer.data.BookKind
import xyz.saltedchips.bookyplayer.data.formatDuration
import xyz.saltedchips.bookyplayer.ui.components.BookCover
import xyz.saltedchips.bookyplayer.ui.components.BookyIcons

internal fun bookProgress(book: Audiobook, override: Float?): Float {
    val stored = if (book.durationMs == 0L) 0f else book.listenedMs.toFloat() / book.durationMs
    return (override ?: stored).coerceIn(0f, 1f)
}

internal fun bookRemainingLabel(book: Audiobook, progress: Float): String {
    val remaining = (book.durationMs - (progress * book.durationMs).toLong()).coerceAtLeast(0)
    return when {
        progress <= 0f -> formatDuration(book.durationMs)
        progress >= 1f -> "Finished"
        else -> "${formatDuration(remaining)} left"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LibraryHeroCard(
    book: Audiobook,
    progress: Float,
    playing: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp)) {
            BookCover(book, Modifier.size(132.dp), corner = 24.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(132.dp)
                    .padding(start = 16.dp),
            ) {
                Text(
                    "Continue listening",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.primary,
                )
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    "${bookRemainingLabel(book, progress)} · ${book.currentChapterTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSecondaryContainer.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    color = scheme.primary,
                    trackColor = scheme.surfaceContainerHighest,
                    amplitude = { if (playing) 1f else 0f },
                    waveSpeed = WavyProgressIndicatorDefaults.LinearDeterminateWavelength / 3f,
                )
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onPlay,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                ) {
                    Icon(
                        if (playing) BookyIcons.pause else BookyIcons.play,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(if (playing) "Pause" else "Resume", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
internal fun BookProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 32.dp,
) {
    if (progress <= 0f) return
    val scheme = MaterialTheme.colorScheme
    CircularProgressIndicator(
        progress = { progress },
        modifier = modifier.size(size),
        color = scheme.primary,
        trackColor = scheme.surfaceContainerHighest,
        strokeWidth = 4.dp,
        strokeCap = StrokeCap.Round,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibraryCompactRow(
    book: Audiobook,
    progress: Float,
    playing: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Surface(
        shape = shape,
        color = if (selected) scheme.secondaryContainer else scheme.background,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onLongClickLabel = "Select",
                )
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(book, Modifier.size(56.dp), corner = 16.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.kind == BookKind.Epub) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EpubBadge()
                        Text(
                            "${book.author} · ${book.chapterCount} chapters",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(start = 8.dp),
                        )
                    }
                } else {
                    Text(
                        "${book.author} · ${bookRemainingLabel(book, progress)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = null)
            } else {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    BookProgressRing(progress)
                    FilledIconButton(
                        onClick = onPlay,
                        shapes = IconButtonDefaults.shapes(
                            shape = IconButtonDefaults.smallRoundShape,
                            pressedShape = IconButtonDefaults.smallPressedShape,
                        ),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (playing) scheme.primary else scheme.surfaceContainerHigh,
                            contentColor = if (playing) scheme.onPrimary else scheme.onSurface,
                        ),
                        modifier = Modifier.size(if (progress > 0f) 24.dp else 36.dp),
                    ) {
                        if (book.kind == BookKind.Epub) {
                            Icon(
                                BookyIcons.menuBook,
                                contentDescription = "Read",
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Icon(
                                if (playing) BookyIcons.pause else BookyIcons.play,
                                contentDescription = if (playing) "Pause" else "Play",
                                modifier = Modifier.size(if (progress > 0f) 14.dp else 20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibraryGridItem(
    book: Audiobook,
    progress: Float,
    playing: Boolean,
    active: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val coverShape = RoundedCornerShape(28.dp)
    Column(
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = "Select",
            ),
    ) {
        Box {
            BookCover(
                book,
                Modifier.fillMaxWidth(),
                shape = coverShape,
            )
            if (active || selected) {
                Surface(
                    modifier = Modifier.matchParentSize(),
                    shape = coverShape,
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    border = BorderStroke(3.dp, scheme.primary),
                ) {}
            }
            if (selecting) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = null,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (progress > 0f && !active) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = scheme.background.copy(alpha = 0.88f),
                            modifier = Modifier.size(40.dp),
                        ) {}
                        BookProgressRing(progress, size = 30.dp)
                    }
                    FilledIconButton(
                        onClick = onPlay,
                        shapes = IconButtonDefaults.shapes(
                            shape = IconButtonDefaults.smallRoundShape,
                            pressedShape = IconButtonDefaults.smallPressedShape,
                        ),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (active) scheme.primary else scheme.background.copy(alpha = 0.88f),
                            contentColor = if (active) scheme.onPrimary else scheme.onSurface,
                        ),
                        modifier = Modifier.size(if (progress > 0f && !active) 22.dp else 40.dp),
                    ) {
                        if (book.kind == BookKind.Epub) {
                            Icon(
                                BookyIcons.menuBook,
                                contentDescription = "Read",
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Icon(
                                if (playing) BookyIcons.pause else BookyIcons.play,
                                contentDescription = if (playing) "Pause" else "Play",
                                modifier = Modifier.size(if (progress > 0f && !active) 14.dp else 20.dp),
                            )
                        }
                    }
                }
            }
        }
        Text(
            book.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
        )
        Text(
            if (book.kind == BookKind.Epub) "ePub · ${book.chapterCount} chapters" else bookRemainingLabel(book, progress),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversionCard(
    book: xyz.saltedchips.bookyplayer.library.GeneratedBook,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRemove: () -> Unit,
    onPlay: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val total = book.chapters.size.coerceAtLeast(1)
    val done = book.doneChapters
    val converting = book.status == xyz.saltedchips.bookyplayer.library.ConversionStatus.Converting
    val failed = book.status == xyz.saltedchips.bookyplayer.library.ConversionStatus.Failed
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (failed) scheme.errorContainer else scheme.secondaryContainer,
        contentColor = if (failed) scheme.onErrorContainer else scheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    failed -> book.error ?: "Conversion failed"
                    converting -> "Making audiobook · $done of $total chapters"
                    else -> "Stopped · $done of $total chapters"
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (!failed) {
                LinearWavyProgressIndicator(
                    progress = { done.toFloat() / total },
                    amplitude = { if (converting) 1f else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (converting) {
                    androidx.compose.material3.TextButton(onClick = onStop) { Text("Stop") }
                } else {
                    androidx.compose.material3.TextButton(onClick = onRemove) { Text("Remove") }
                    androidx.compose.material3.TextButton(onClick = onResume) {
                        Text(if (failed) "Try again" else "Resume")
                    }
                }
                if (onPlay != null && done > 0) {
                    androidx.compose.material3.Button(onClick = onPlay) {
                        Text(if (converting) "Listen now" else "Listen")
                    }
                }
            }
        }
    }
}

@Composable
internal fun EpubBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier,
    ) {
        Text(
            "ePub",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** The top-bar badge: a spinner while audiobooks are being made, otherwise their overall progress. */
@Composable
internal fun ConversionIndicator(conversions: List<xyz.saltedchips.bookyplayer.library.GeneratedBook>) {
    val converting = conversions.any {
        it.status == xyz.saltedchips.bookyplayer.library.ConversionStatus.Converting
    }
    val failed = conversions.any {
        it.status == xyz.saltedchips.bookyplayer.library.ConversionStatus.Failed
    }
    when {
        converting -> androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 3.dp,
        )
        failed -> Icon(BookyIcons.warning, contentDescription = "Audiobook conversion needs attention")
        else -> {
            val total = conversions.sumOf { it.chapters.size }.coerceAtLeast(1)
            val done = conversions.sumOf { it.doneChapters }
            androidx.compose.material3.CircularProgressIndicator(
                progress = { done.toFloat() / total },
                modifier = Modifier.size(24.dp),
                strokeWidth = 3.dp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversionsSheet(
    conversions: List<xyz.saltedchips.bookyplayer.library.GeneratedBook>,
    onResume: (String) -> Unit,
    onStop: (String) -> Unit,
    onRemove: (String) -> Unit,
    onPlay: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Making audiobooks", style = MaterialTheme.typography.headlineSmall)
            conversions.forEach { conversion ->
                ConversionCard(
                    book = conversion,
                    onResume = { onResume(conversion.id) },
                    onStop = { onStop(conversion.id) },
                    onRemove = { onRemove(conversion.id) },
                    onPlay = { onPlay(conversion.id) },
                )
            }
        }
    }
}
