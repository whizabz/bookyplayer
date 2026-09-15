package xyz.saltedchips.bookyplayer.ui.library

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.saltedchips.bookyplayer.data.Audiobook
import xyz.saltedchips.bookyplayer.data.formatDuration
import xyz.saltedchips.bookyplayer.data.sourceLabel
import xyz.saltedchips.bookyplayer.player.ChapterMarksSnapshot
import xyz.saltedchips.bookyplayer.player.PlayerUiState
import xyz.saltedchips.bookyplayer.ui.components.BookCover
import xyz.saltedchips.bookyplayer.ui.components.BookyIcons
import xyz.saltedchips.bookyplayer.ui.components.MarkPreviousChaptersDialog
import xyz.saltedchips.bookyplayer.ui.components.SwipeableChapterRow
import xyz.saltedchips.bookyplayer.ui.components.chapterListenProgress
import xyz.saltedchips.bookyplayer.ui.components.showBulkChapterSnackbar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BookDetailScreen(
    book: Audiobook,
    player: PlayerUiState,
    savedChapterProgress: Map<Int, Long>,
    completedChapters: Set<Int>,
    onPlay: () -> Unit,
    onPlayChapter: (Int) -> Unit,
    onSetChaptersPlayed: (Collection<Int>, Boolean) -> Unit,
    onSnapshotMarks: () -> ChapterMarksSnapshot,
    onRestoreMarks: (ChapterMarksSnapshot) -> Unit,
    onUpdateMetadata: (String, String, String, List<String>, Bitmap?) -> Unit,
    onDelete: () -> Unit,
    onMarkPlayed: () -> Unit,
    onResetProgress: () -> Unit,
    onBack: () -> Unit,
    bottomContentPadding: Dp = 16.dp,
    modifier: Modifier = Modifier,
    selectionBackEnabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val isActive = player.book?.id == book.id
    val playing = isActive && player.isPlaying
    val titles = book.chapterTitles.ifEmpty { listOf(book.title) }
    val durations = book.chapterDurationsMs.ifEmpty {
        if (book.durationMs > 0L) listOf(book.durationMs) else emptyList()
    }
    val bookPositionMs = if (isActive) player.bookPositionMs else book.listenedMs
    val positions = if (isActive) player.chapterPositionsMs else savedChapterProgress
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var confirmingReset by remember { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedIndices by rememberSaveable { mutableStateOf(setOf<Int>()) }
    var revealedIndex by remember { mutableStateOf<Int?>(null) }
    var pendingPlayed by remember { mutableStateOf<Set<Int>?>(null) }
    val selectedComplete = selectedIndices.isNotEmpty() &&
        selectedIndices.all { it in completedChapters }
    BackHandler(enabled = selecting && selectionBackEnabled) {
        selecting = false
        selectedIndices = emptySet()
        revealedIndex = null
    }

    fun applyPlayed(indices: Set<Int>, bulk: Boolean) {
        val snapshot = onSnapshotMarks()
        onSetChaptersPlayed(indices, true)
        if (bulk) {
            scope.showBulkChapterSnackbar(
                host = snackbarHostState,
                count = indices.size,
                played = true,
                snapshot = snapshot,
                restore = onRestoreMarks,
            )
        }
    }

    fun applyUnplayed(indices: Set<Int>, bulk: Boolean) {
        val snapshot = onSnapshotMarks()
        onSetChaptersPlayed(indices, false)
        if (bulk) {
            scope.showBulkChapterSnackbar(
                host = snackbarHostState,
                count = indices.size,
                played = false,
                snapshot = snapshot,
                restore = onRestoreMarks,
            )
        }
    }

    fun requestMarkPlayed(indices: Set<Int>, bulkIfNoPrompt: Boolean) {
        val lowest = indices.minOrNull() ?: return
        val previousIncomplete = (0 until lowest).any { it !in completedChapters }
        if (lowest > 0 && previousIncomplete) {
            pendingPlayed = indices
        } else {
            applyPlayed(indices, bulk = bulkIfNoPrompt)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = bottomContentPadding),
            )
        },
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        when {
                            !selecting -> book.title
                            selectedIndices.isEmpty() -> "Select chapters"
                            else -> "${selectedIndices.size} selected"
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selecting) {
                                selecting = false
                                selectedIndices = emptySet()
                            } else {
                                onBack()
                            }
                        },
                    ) {
                        Icon(
                            if (selecting) BookyIcons.close else BookyIcons.back,
                            contentDescription = if (selecting) "Cancel" else "Back",
                        )
                    }
                },
                actions = {
                    if (selecting) {
                        IconButton(
                            onClick = {
                                val indices = selectedIndices
                                if (indices.isEmpty()) return@IconButton
                                if (selectedComplete) {
                                    applyUnplayed(indices, bulk = true)
                                } else {
                                    requestMarkPlayed(indices, bulkIfNoPrompt = true)
                                }
                                selecting = false
                                selectedIndices = emptySet()
                            },
                            enabled = selectedIndices.isNotEmpty(),
                        ) {
                            Icon(
                                if (selectedComplete) BookyIcons.restartAlt else BookyIcons.doneAll,
                                contentDescription = if (selectedComplete) {
                                    "Mark as unplayed"
                                } else {
                                    "Mark as played"
                                },
                            )
                        }
                    } else {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(BookyIcons.more, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            shape = MenuDefaults.shape,
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { Icon(BookyIcons.edit, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    editing = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Mark as played") },
                                leadingIcon = { Icon(BookyIcons.doneAll, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onMarkPlayed()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Reset") },
                                leadingIcon = { Icon(BookyIcons.restartAlt, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    confirmingReset = true
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(BookyIcons.delete, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    confirmingDelete = true
                                },
                            )
                        }
                    }
                },
                windowInsets = WindowInsets(0),
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    scrolledContainerColor = colors.surface,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = bottomContentPadding,
            ),
        ) {
            item("header") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BookCover(
                        book = book,
                        modifier = Modifier.size(220.dp),
                        square = false,
                        shape = RoundedCornerShape(20.dp),
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    if (book.narrator.isNotBlank()) {
                        Text(
                            text = "Narrated by ${book.narrator}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Text(
                        text = book.sourceLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = formatDuration(book.durationMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Button(
                        onClick = onPlay,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp, bottom = 8.dp),
                    ) {
                        Icon(
                            if (playing) BookyIcons.pause else BookyIcons.play,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (playing) "Pause" else "Play")
                    }
                    Text(
                        text = "Chapters",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 8.dp),
                    )
                }
            }
            itemsIndexed(titles, key = { index, _ -> "chapter-$index" }) { index, title ->
                val current = isActive && index == player.currentChapterIndex
                val duration = durations.getOrNull(index) ?: 0L
                val complete = index in completedChapters
                val progress = if (complete) {
                    1f
                } else {
                    chapterListenProgress(
                        index = index,
                        durationMs = duration,
                        bookPositionMs = bookPositionMs,
                        chapterDurationsMs = durations,
                        savedPositionsMs = positions,
                    )
                }
                SwipeableChapterRow(
                    index = index,
                    title = title,
                    durationMs = duration,
                    progress = progress,
                    complete = complete,
                    selected = current && !selecting,
                    itemShapes = ListItemDefaults.segmentedShapes(
                        index = index,
                        count = titles.size,
                    ),
                    revealed = revealedIndex == index,
                    swipeEnabled = !selecting,
                    selecting = selecting,
                    checked = index in selectedIndices,
                    playing = current && playing,
                    modifier = Modifier.padding(
                        top = if (index == 0) 0.dp else ListItemDefaults.SegmentedGap,
                    ),
                    onClick = {
                        if (selecting) {
                            selectedIndices = if (index in selectedIndices) {
                                selectedIndices - index
                            } else {
                                selectedIndices + index
                            }
                        } else {
                            onPlayChapter(index)
                        }
                    },
                    onLongClick = {
                        selecting = true
                        selectedIndices = setOf(index)
                        revealedIndex = null
                    },
                    onRevealedChange = { open ->
                        revealedIndex = if (open) index else if (revealedIndex == index) null else revealedIndex
                    },
                    onMarkPlayed = { requestMarkPlayed(setOf(index), bulkIfNoPrompt = false) },
                    onMarkUnplayed = { applyUnplayed(setOf(index), bulk = false) },
                )
            }
        }
    }
    pendingPlayed?.let { indices ->
        val lowest = indices.minOrNull() ?: 0
        val previousCount = (0 until lowest).count { it !in completedChapters }
        MarkPreviousChaptersDialog(
            previousCount = previousCount,
            onMarkPrevious = {
                pendingPlayed = null
                applyPlayed((0 until lowest).toSet() + indices, bulk = true)
            },
            onOnlyThese = {
                pendingPlayed = null
                applyPlayed(indices, bulk = indices.size > 1)
            },
            onDismiss = { pendingPlayed = null },
        )
    }
    if (editing) {
        BookMetadataSheet(
            book = book,
            onSave = { title, author, narrator, chapters, cover ->
                onUpdateMetadata(title, author, narrator, chapters, cover)
            },
            onDismiss = { editing = false },
        )
    }
    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            title = { Text("Reset progress?") },
            text = { Text("Clear all listening progress for ${book.title}.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingReset = false
                        onResetProgress()
                    },
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingReset = false }) { Text("Cancel") }
            },
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete book?") },
            text = { Text("Remove ${book.title} from your library.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            },
        )
    }
}
