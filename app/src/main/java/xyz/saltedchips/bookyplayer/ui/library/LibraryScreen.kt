package xyz.saltedchips.bookyplayer.ui.library

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.saltedchips.bookyplayer.data.Audiobook
import xyz.saltedchips.bookyplayer.data.formatDuration
import xyz.saltedchips.bookyplayer.library.LibrarySort
import xyz.saltedchips.bookyplayer.settings.LibraryLayout
import xyz.saltedchips.bookyplayer.ui.components.BookCover
import xyz.saltedchips.bookyplayer.ui.components.BookyIcons
import xyz.saltedchips.bookyplayer.ui.components.RowProgressFill
import xyz.saltedchips.bookyplayer.ui.components.SwipeToRevealActions

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryScreen(
    books: List<Audiobook>,
    onOpenBook: (Audiobook) -> Unit,
    onPlayBook: (Audiobook) -> Unit,
    modifier: Modifier = Modifier,
    activeBookId: String? = null,
    isPlaying: Boolean = false,
    playbackProgress: Float? = null,
    hasFolder: Boolean = false,
    folderName: String? = null,
    scanning: Boolean = false,
    scanDone: Int = 0,
    scanTotal: Int = 0,
    scanLabel: String? = null,
    sort: LibrarySort = LibrarySort.Alphabetical,
    onSortChange: (LibrarySort) -> Unit = {},
    onMarkPlayed: (List<Audiobook>) -> Unit = {},
    onDelete: (List<Audiobook>) -> Unit = {},
    onUpdateMetadata: (String, String, String, String, List<String>, Bitmap?) -> Unit = { _, _, _, _, _, _ -> },
    onChooseFolder: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    bottomContentPadding: Dp = 24.dp,
    selectionBackEnabled: Boolean = true,
    layout: LibraryLayout = LibraryLayout.Rows,
) {
    var sortMenu by remember { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var revealedId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<List<Audiobook>?>(null) }
    var editingBook by remember { mutableStateOf<Audiobook?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.All) }
    val shown = books.filter { filter.matches(it) }
    val selectedBooks = books.filter { it.id in selectedIds }
    val toggleSelect: (Audiobook) -> Unit = { book ->
        selectedIds = if (book.id in selectedIds) selectedIds - book.id else selectedIds + book.id
    }
    val enterSelect: (Audiobook) -> Unit = { book ->
        selecting = true
        selectedIds = setOf(book.id)
        revealedId = null
    }
    BackHandler(enabled = selecting && selectionBackEnabled) {
        selecting = false
        selectedIds = emptySet()
        revealedId = null
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    val motion = MaterialTheme.motionScheme
                    val title = when {
                        !selecting -> "Library"
                        selectedIds.isEmpty() -> "Select books"
                        else -> "${selectedIds.size} selected"
                    }
                    AnimatedContent(
                        targetState = title,
                        transitionSpec = {
                            fadeIn(motion.defaultEffectsSpec()) togetherWith
                                fadeOut(motion.fastEffectsSpec()) using
                                SizeTransform { _, _ -> motion.fastSpatialSpec() }
                        },
                        label = "library-title",
                    ) { text ->
                        Text(
                            text,
                            style = MaterialTheme.typography.displaySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    val motion = MaterialTheme.motionScheme
                    val enterFromEnd = fadeIn(motion.defaultEffectsSpec()) +
                        expandHorizontally(
                            animationSpec = motion.fastSpatialSpec(),
                            expandFrom = Alignment.End,
                        ) +
                        scaleIn(
                            animationSpec = motion.fastSpatialSpec(),
                            initialScale = 0.86f,
                        )
                    val exitToEnd = fadeOut(motion.fastEffectsSpec()) +
                        shrinkHorizontally(
                            animationSpec = motion.fastSpatialSpec(),
                            shrinkTowards = Alignment.End,
                        ) +
                        scaleOut(
                            animationSpec = motion.fastSpatialSpec(),
                            targetScale = 0.86f,
                        )
                    AnimatedVisibility(
                        visible = selecting,
                        enter = enterFromEnd,
                        exit = exitToEnd,
                    ) {
                        Row {
                            IconButton(
                                onClick = {
                                    onMarkPlayed(selectedBooks)
                                    selecting = false
                                    selectedIds = emptySet()
                                },
                                enabled = selectedBooks.isNotEmpty(),
                            ) {
                                Icon(BookyIcons.doneAll, contentDescription = "Mark as played")
                            }
                            IconButton(
                                onClick = {
                                    editingBook = selectedBooks.first()
                                    selecting = false
                                    selectedIds = emptySet()
                                },
                                enabled = selectedBooks.size == 1,
                            ) {
                                Icon(BookyIcons.edit, contentDescription = "Edit")
                            }
                            IconButton(
                                onClick = { pendingDelete = selectedBooks },
                                enabled = selectedBooks.isNotEmpty(),
                            ) {
                                Icon(BookyIcons.delete, contentDescription = "Delete")
                            }
                            IconButton(
                                onClick = {
                                    selecting = false
                                    selectedIds = emptySet()
                                },
                            ) {
                                Icon(BookyIcons.close, contentDescription = "Cancel selection")
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = !selecting,
                        enter = enterFromEnd,
                        exit = exitToEnd,
                    ) {
                        Row {
                            Box {
                                IconButton(onClick = { sortMenu = true }) {
                                    Icon(BookyIcons.sort, contentDescription = "Sort")
                                }
                                DropdownMenu(
                                    expanded = sortMenu,
                                    onDismissRequest = { sortMenu = false },
                                    shape = MenuDefaults.shape,
                                ) {
                                    LibrarySort.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            onClick = {
                                                onSortChange(option)
                                                sortMenu = false
                                            },
                                            trailingIcon = if (sort == option) {
                                                {
                                                    Icon(
                                                        BookyIcons.check,
                                                        contentDescription = null,
                                                    )
                                                }
                                            } else {
                                                null
                                            },
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(BookyIcons.settings, contentDescription = "Settings")
                            }
                        }
                    }
                },
                expandedHeight = 88.dp,
                windowInsets = WindowInsets(0),
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
        if (books.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibraryFilter.entries.forEach { option ->
                    FilterChip(
                        modifier = Modifier.height(40.dp),
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option.label) },
                        shape = RoundedCornerShape(12.dp),
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        if (hasFolder && scanning && books.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                LibraryScanProgress(
                    scanDone = scanDone,
                    scanTotal = scanTotal,
                )
            }
        } else {
            val showEmptyState = !hasFolder || (!scanning && shown.isEmpty())
            val emptyState: @Composable () -> Unit = {
                if (!hasFolder) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Choose your audiobook folder", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Pick the folder that holds your books. Files in that folder, and folders of files, become titles in your library.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                            )
                            Button(onClick = onChooseFolder) { Text("Choose folder") }
                        }
                    }
                } else if (books.isNotEmpty()) {
                    Text(
                        text = "No ${filter.label.lowercase()} books",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    Text(
                        text = if (folderName != null) {
                            "No audiobooks found in $folderName"
                        } else {
                            "No audiobooks found in this folder"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            fun progressOf(book: Audiobook) =
                bookProgress(book, if (book.id == activeBookId) playbackProgress else null)
            when (layout) {
                LibraryLayout.Rows -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = bottomContentPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showEmptyState) item("empty-state") { emptyState() }
                    itemsIndexed(shown, key = { _, book -> book.id }) { index, book ->
                        LibraryBookRow(
                            book = book,
                            index = index,
                            count = shown.size,
                            progressOverride = if (book.id == activeBookId) playbackProgress else null,
                            showWavyProgress = book.id == activeBookId && isPlaying,
                            active = book.id == activeBookId,
                            selecting = selecting,
                            selected = book.id in selectedIds,
                            revealed = revealedId == book.id,
                            onRevealedChange = { open ->
                                revealedId = if (open) book.id else if (revealedId == book.id) null else revealedId
                            },
                            onOpen = { onOpenBook(book) },
                            onPlay = { onPlayBook(book) },
                            onToggleSelect = { toggleSelect(book) },
                            onEnterSelect = { enterSelect(book) },
                            onEdit = { editingBook = book },
                            onDelete = { pendingDelete = listOf(book) },
                        )
                    }
                }

                LibraryLayout.Hero -> {
                    val heroBook = shown.firstOrNull { it.id == activeBookId }
                        ?: shown
                            .filter { it.listenedMs > 0L && it.listenedMs < it.durationMs }
                            .maxByOrNull { it.lastPlayedMs }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = bottomContentPadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (showEmptyState) item("empty-state") { emptyState() }
                        if (heroBook != null && !selecting) {
                            item("hero") {
                                LibraryHeroCard(
                                    book = heroBook,
                                    progress = progressOf(heroBook),
                                    playing = heroBook.id == activeBookId && isPlaying,
                                    onOpen = { onOpenBook(heroBook) },
                                    onPlay = { onPlayBook(heroBook) },
                                )
                            }
                        }
                        if (shown.isNotEmpty()) {
                            item("all-books-label") {
                                Text(
                                    "All books",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp),
                                )
                            }
                        }
                        items(
                            shown.filter { selecting || it.id != heroBook?.id },
                            key = { it.id },
                        ) { book ->
                            LibraryCompactRow(
                                book = book,
                                progress = progressOf(book),
                                playing = book.id == activeBookId && isPlaying,
                                selecting = selecting,
                                selected = book.id in selectedIds,
                                onClick = if (selecting) {
                                    { toggleSelect(book) }
                                } else {
                                    { onOpenBook(book) }
                                },
                                onLongClick = if (selecting) {
                                    { toggleSelect(book) }
                                } else {
                                    { enterSelect(book) }
                                },
                                onPlay = { onPlayBook(book) },
                            )
                        }
                    }
                }

                LibraryLayout.Grid -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = bottomContentPadding,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (showEmptyState) {
                        item("empty-state", span = { GridItemSpan(maxLineSpan) }) { emptyState() }
                    }
                    items(shown, key = { it.id }) { book ->
                        LibraryGridItem(
                            book = book,
                            progress = progressOf(book),
                            playing = book.id == activeBookId && isPlaying,
                            active = book.id == activeBookId,
                            selecting = selecting,
                            selected = book.id in selectedIds,
                            onClick = if (selecting) {
                                { toggleSelect(book) }
                            } else {
                                { onOpenBook(book) }
                            },
                            onLongClick = if (selecting) {
                                { toggleSelect(book) }
                            } else {
                                { enterSelect(book) }
                            },
                            onPlay = { onPlayBook(book) },
                        )
                    }
                }
            }
        }
        }
        }
    }

    editingBook?.let { book ->
        BookMetadataSheet(
            book = book,
            onSave = { title, author, narrator, chapters, cover ->
                onUpdateMetadata(book.id, title, author, narrator, chapters, cover)
            },
            onDismiss = { editingBook = null },
        )
    }

    pendingDelete?.let { targets ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(if (targets.size == 1) "Delete book?" else "Delete ${targets.size} books?") },
            text = {
                Text(
                    if (targets.size == 1) {
                        "Remove ${targets.first().title} from your library."
                    } else {
                        "Remove the selected books from your library."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(targets)
                        pendingDelete = null
                        selecting = false
                        selectedIds = emptySet()
                        revealedId = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryBookRow(
    book: Audiobook,
    index: Int,
    count: Int,
    progressOverride: Float?,
    showWavyProgress: Boolean,
    active: Boolean,
    selecting: Boolean,
    selected: Boolean,
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onToggleSelect: () -> Unit,
    onEnterSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val rowShape = RoundedCornerShape(28.dp)
    val actionWidth = 56.dp
    SwipeToRevealActions(
        revealed = revealed,
        onRevealedChange = onRevealedChange,
        enabled = !selecting,
        shape = rowShape,
        actionWidth = actionWidth,
        actionCount = 2,
        actionGap = 8.dp,
        rowGap = 8.dp,
        contentBackground = androidx.compose.ui.graphics.Color.Transparent,
        actions = { _ ->
            FilledTonalIconButton(
                onClick = {
                    onRevealedChange(false)
                    onEdit()
                },
                shapes = IconButtonShapes(
                    shape = rowShape,
                    pressedShape = RoundedCornerShape(20.dp),
                ),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer,
                ),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(actionWidth),
            ) {
                Icon(BookyIcons.edit, contentDescription = "Edit")
            }
            FilledTonalIconButton(
                onClick = {
                    onRevealedChange(false)
                    onDelete()
                },
                shapes = IconButtonShapes(
                    shape = rowShape,
                    pressedShape = RoundedCornerShape(20.dp),
                ),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = colors.errorContainer,
                    contentColor = colors.onErrorContainer,
                ),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(actionWidth),
            ) {
                Icon(BookyIcons.delete, contentDescription = "Delete")
            }
        },
    ) {
        LibraryRow(
            book = book,
            shape = rowShape,
            progressOverride = progressOverride,
            showWavyProgress = showWavyProgress,
            selecting = selecting,
            selected = selected,
            onClick = if (selecting) onToggleSelect else onOpen,
            onLongClick = if (selecting) onToggleSelect else onEnterSelect,
            onPlay = onPlay,
            playing = showWavyProgress,
            active = active,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryRow(
    book: Audiobook,
    shape: Shape,
    progressOverride: Float?,
    showWavyProgress: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlay: () -> Unit,
    playing: Boolean,
    active: Boolean,
) {
    val stored = if (book.durationMs == 0L) 0f else (book.listenedMs.toFloat() / book.durationMs).coerceIn(0f, 1f)
    val progress = (progressOverride ?: stored).coerceIn(0f, 1f)
    val remaining = (book.durationMs - (progress * book.durationMs).toLong()).coerceAtLeast(0)
    val remainingLabel = when {
        progress <= 0f -> formatDuration(book.durationMs)
        progress >= 1f -> "Finished"
        else -> "${formatDuration(remaining)} left"
    }
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        targetValue = if (selected) scheme.secondaryContainer else scheme.surfaceContainerLow,
        animationSpec = motion.defaultEffectsSpec(),
        label = "library-row-container",
    )
    val showProgress = progress > 0f
    Surface(
        shape = shape,
        color = container,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
        modifier = Modifier.clip(shape),
    ) {
        Box {
            if (showProgress) {
                RowProgressFill(progress, scheme.primary.copy(alpha = if (active) 0.2f else 0.12f))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                        onLongClickLabel = "Select",
                    )
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookCover(book, Modifier.size(64.dp), corner = 18.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp, end = 12.dp),
                ) {
                    Text(
                        book.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${book.author} · ${book.narrator}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        remainingLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier.height(64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selecting) {
                        Checkbox(checked = selected, onCheckedChange = null)
                    } else {
                        FilledIconButton(
                            onClick = onPlay,
                            shapes = IconButtonDefaults.shapes(
                                shape = if (active) RoundedCornerShape(16.dp) else IconButtonDefaults.smallRoundShape,
                                pressedShape = IconButtonDefaults.smallPressedShape,
                            ),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (active) scheme.primary else scheme.secondaryContainer,
                                contentColor = if (active) scheme.onPrimary else scheme.onSecondaryContainer,
                            ),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                if (playing) BookyIcons.pause else BookyIcons.play,
                                contentDescription = if (playing) "Pause" else "Play",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryScanProgress(
    scanDone: Int,
    scanTotal: Int,
    modifier: Modifier = Modifier,
) {
    val fraction = if (scanTotal <= 0) {
        0f
    } else {
        (scanDone.toFloat() / scanTotal.toFloat()).coerceIn(0f, 1f)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Text(
            text = "Scanning for new books",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

private enum class LibraryFilter(val label: String) {
    All("All"),
    InProgress("In progress"),
    Finished("Finished"),
    ;

    fun matches(book: Audiobook): Boolean = when (this) {
        All -> true
        InProgress -> book.listenedMs > 0L && book.listenedMs < book.durationMs
        Finished -> book.durationMs > 0L && book.listenedMs >= book.durationMs
    }
}
