package com.booky.app.ui.library

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.booky.app.data.Audiobook
import com.booky.app.data.MockLibrary
import com.booky.app.data.formatDuration
import com.booky.app.library.LibrarySort
import com.booky.app.ui.components.BookCover
import com.booky.app.ui.components.BookyIcons
import com.booky.app.ui.components.SwipeToRevealActions
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryScreen(
    books: List<Audiobook>,
    archivedBooks: List<Audiobook>,
    onOpenBook: (Audiobook) -> Unit,
    modifier: Modifier = Modifier,
    activeBookId: String? = null,
    isPlaying: Boolean = false,
    playbackProgress: Float? = null,
    hasFolder: Boolean = false,
    folderName: String? = null,
    scanning: Boolean = false,
    showArchived: Boolean = false,
    sort: LibrarySort = LibrarySort.Manual,
    onShowArchivedChange: (Boolean) -> Unit = {},
    onSortChange: (LibrarySort) -> Unit = {},
    onMarkPlayed: (List<Audiobook>) -> Unit = {},
    onArchive: (List<Audiobook>, Boolean) -> Unit = { _, _ -> },
    onDelete: (List<Audiobook>) -> Unit = {},
    onUpdateMetadata: (String, String, String, String, List<String>, Bitmap?) -> Unit = { _, _, _, _, _, _ -> },
    onChooseFolder: () -> Unit = {},
) {
    var addMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var revealedId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<List<Audiobook>?>(null) }
    var editingBook by remember { mutableStateOf<Audiobook?>(null) }
    val searchBarState = rememberSearchBarState()
    val searchQuery = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    val query = searchQuery.text.toString()
    val searchMatches = remember(books, archivedBooks, query) {
        val pool = books + archivedBooks
        if (query.isBlank()) {
            pool
        } else {
            pool.filter { book ->
                book.title.contains(query, ignoreCase = true) ||
                    book.author.contains(query, ignoreCase = true) ||
                    book.narrator.contains(query, ignoreCase = true)
            }
        }
    }
    val recents = if (hasFolder) {
        emptyList()
    } else {
        MockLibrary.recentSearches.filter {
            query.isBlank() || it.title.contains(query, ignoreCase = true)
        }
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val selectedBooks = (books + archivedBooks).filter { it.id in selectedIds }
    BackHandler(enabled = selecting) {
        selecting = false
        selectedIds = emptySet()
        revealedId = null
    }
    val searchInput = @Composable {
        SearchBarDefaults.InputField(
            searchBarState = searchBarState,
            textFieldState = searchQuery,
            onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
            placeholder = { Text("Search library") },
            leadingIcon = {
                IconButton(onClick = { scope.launch { searchBarState.animateToCollapsed() } }) {
                    Icon(BookyIcons.back, contentDescription = "Back")
                }
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { searchQuery.clearText() }) {
                        Icon(BookyIcons.close, contentDescription = "Clear")
                    }
                }
            },
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeFlexibleTopAppBar(
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
                        Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                                    onArchive(selectedBooks, true)
                                    selecting = false
                                    selectedIds = emptySet()
                                },
                                enabled = selectedBooks.isNotEmpty(),
                            ) {
                                Icon(BookyIcons.archive, contentDescription = "Archive")
                            }
                            IconButton(
                                onClick = { pendingDelete = selectedBooks },
                                enabled = selectedBooks.isNotEmpty(),
                            ) {
                                Icon(BookyIcons.delete, contentDescription = "Delete")
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = !selecting,
                        enter = enterFromEnd,
                        exit = exitToEnd,
                    ) {
                        IconButton(onClick = { scope.launch { searchBarState.animateToExpanded() } }) {
                            Icon(BookyIcons.search, contentDescription = "Search")
                        }
                    }
                    Box {
                        AnimatedContent(
                            targetState = selecting,
                            transitionSpec = {
                                fadeIn(motion.defaultEffectsSpec()) +
                                    scaleIn(motion.fastSpatialSpec(), initialScale = 0.86f) togetherWith
                                    fadeOut(motion.fastEffectsSpec()) +
                                    scaleOut(motion.fastSpatialSpec(), targetScale = 0.86f)
                            },
                            label = "library-end-action",
                        ) { inSelect ->
                            if (inSelect) {
                                IconButton(
                                    onClick = {
                                        selecting = false
                                        selectedIds = emptySet()
                                    },
                                ) {
                                    Icon(BookyIcons.close, contentDescription = "Cancel selection")
                                }
                            } else {
                                IconButton(onClick = { addMenu = true }) {
                                    Icon(BookyIcons.more, contentDescription = "More")
                                }
                            }
                        }
                        DropdownMenu(expanded = addMenu, onDismissRequest = {
                            addMenu = false
                            sortMenu = false
                        }) {
                            if (sortMenu) {
                                DropdownMenuItem(
                                    text = { Text("Sort by") },
                                    onClick = { sortMenu = false },
                                    leadingIcon = { Icon(BookyIcons.back, contentDescription = null) },
                                )
                                HorizontalDivider()
                                LibrarySort.entries.forEachIndexed { index, option ->
                                    DropdownMenuItem(
                                        selected = sort == option,
                                        onClick = {
                                            onSortChange(option)
                                            addMenu = false
                                            sortMenu = false
                                        },
                                        text = { Text(option.label) },
                                        shapes = MenuDefaults.itemShape(index, LibrarySort.entries.size),
                                        selectedLeadingIcon = {
                                            Icon(BookyIcons.check, contentDescription = null)
                                        },
                                    )
                                }
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Show archived") },
                                    onClick = { onShowArchivedChange(!showArchived) },
                                    trailingIcon = {
                                        Switch(
                                            checked = showArchived,
                                            onCheckedChange = onShowArchivedChange,
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Select") },
                                    onClick = {
                                        addMenu = false
                                        selecting = true
                                        revealedId = null
                                    },
                                    leadingIcon = { Icon(BookyIcons.select, contentDescription = null) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort by") },
                                    onClick = { sortMenu = true },
                                    leadingIcon = { Icon(BookyIcons.viewOptions, contentDescription = null) },
                                    trailingIcon = {
                                        Text(sort.label, color = MaterialTheme.colorScheme.primary)
                                    },
                                )
                            }
                        }
                    }
                },
                windowInsets = WindowInsets(0),
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        if (hasFolder && scanning && books.isEmpty() && archivedBooks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LoadingIndicator()
                    Text(
                        text = if (folderName != null) {
                            "Scanning $folderName"
                        } else {
                            "Scanning library"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                if (scanning && (books.isNotEmpty() || archivedBooks.isNotEmpty())) {
                    item("scanning") {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        )
                    }
                }
                if (!hasFolder) {
                    item("choose-folder") {
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
                    }
                } else if (!scanning && books.isEmpty() && (!showArchived || archivedBooks.isEmpty())) {
                    item("empty") {
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
                itemsIndexed(books, key = { _, book -> book.id }) { index, book ->
                    LibraryBookRow(
                        book = book,
                        index = index,
                        count = books.size,
                        progressOverride = if (book.id == activeBookId) playbackProgress else null,
                        showWavyProgress = book.id == activeBookId && isPlaying,
                        selecting = selecting,
                        selected = book.id in selectedIds,
                        revealed = revealedId == book.id,
                        onRevealedChange = { open ->
                            revealedId = if (open) book.id else if (revealedId == book.id) null else revealedId
                        },
                        onOpen = { onOpenBook(book) },
                        onToggleSelect = {
                            selectedIds = if (book.id in selectedIds) {
                                selectedIds - book.id
                            } else {
                                selectedIds + book.id
                            }
                        },
                        onEnterSelect = {
                            selecting = true
                            selectedIds = setOf(book.id)
                            revealedId = null
                        },
                        onEdit = { editingBook = book },
                        onArchive = { onArchive(listOf(book), !book.archived) },
                        onDelete = { pendingDelete = listOf(book) },
                    )
                }
                if (showArchived && archivedBooks.isNotEmpty()) {
                    item("archived-header") {
                        Text(
                            text = "Archived",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                    itemsIndexed(archivedBooks, key = { _, book -> "archived-${book.id}" }) { index, book ->
                        LibraryBookRow(
                            book = book,
                            index = index,
                            count = archivedBooks.size,
                            progressOverride = if (book.id == activeBookId) playbackProgress else null,
                            showWavyProgress = book.id == activeBookId && isPlaying,
                            selecting = selecting,
                            selected = book.id in selectedIds,
                            revealed = revealedId == book.id,
                            onRevealedChange = { open ->
                                revealedId = if (open) book.id else if (revealedId == book.id) null else revealedId
                            },
                            onOpen = { onOpenBook(book) },
                            onToggleSelect = {
                                selectedIds = if (book.id in selectedIds) {
                                    selectedIds - book.id
                                } else {
                                    selectedIds + book.id
                                }
                            },
                            onEnterSelect = {
                                selecting = true
                                selectedIds = setOf(book.id)
                                revealedId = null
                            },
                            onEdit = { editingBook = book },
                            onArchive = { onArchive(listOf(book), false) },
                            onDelete = { pendingDelete = listOf(book) },
                        )
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

    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = searchInput,
    ) {
        if (recents.isNotEmpty()) {
            Text(
                text = "Recent",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            recents.forEach { item ->
                val book = MockLibrary.bookById(item.bookId) ?: books.firstOrNull() ?: return@forEach
                ListItem(
                    headlineContent = { Text(item.title) },
                    supportingContent = { Text("${item.subtitle} · ${item.durationLabel}") },
                    leadingContent = { BookCover(book, Modifier.size(48.dp), corner = 10.dp) },
                    trailingContent = { Icon(BookyIcons.play, contentDescription = "Play") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { searchBarState.animateToCollapsed() }
                            onOpenBook(book)
                        },
                )
            }
        }
        if (searchMatches.isNotEmpty()) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            searchMatches.forEach { book ->
                ListItem(
                    headlineContent = { Text(book.title) },
                    supportingContent = { Text("${book.author} · ${book.narrator}") },
                    leadingContent = { BookCover(book, Modifier.size(48.dp), corner = 10.dp) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { searchBarState.animateToCollapsed() }
                            onOpenBook(book)
                        },
                )
            }
        } else {
            Text(
                "No matching titles",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }
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
    selecting: Boolean,
    selected: Boolean,
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onEnterSelect: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val itemShapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    val actionWidth = 56.dp
    SwipeToRevealActions(
        revealed = revealed,
        onRevealedChange = onRevealedChange,
        enabled = !selecting,
        shape = itemShapes.shape,
        actionWidth = actionWidth,
        actionCount = 3,
        actions = {
            FilledTonalIconButton(
                onClick = {
                    onRevealedChange(false)
                    onEdit()
                },
                shapes = IconButtonShapes(
                    shape = ButtonGroupDefaults.connectedLeadingButtonShape,
                    pressedShape = ButtonGroupDefaults.connectedLeadingButtonPressShape,
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
                    onArchive()
                },
                shapes = IconButtonShapes(
                    shape = ButtonGroupDefaults.connectedMiddleButtonShapes().shape,
                    pressedShape = ButtonGroupDefaults.connectedMiddleButtonPressShape,
                ),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer,
                ),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(actionWidth),
            ) {
                Icon(BookyIcons.archive, contentDescription = if (book.archived) "Unarchive" else "Archive")
            }
            FilledTonalIconButton(
                onClick = {
                    onRevealedChange(false)
                    onDelete()
                },
                shapes = IconButtonShapes(
                    shape = ButtonGroupDefaults.connectedTrailingButtonShape,
                    pressedShape = ButtonGroupDefaults.connectedTrailingButtonPressShape,
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
            itemShapes = itemShapes,
            progressOverride = progressOverride,
            showWavyProgress = showWavyProgress,
            selecting = selecting,
            selected = selected,
            onClick = if (selecting) onToggleSelect else onOpen,
            onLongClick = if (selecting) onToggleSelect else onEnterSelect,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryRow(
    book: Audiobook,
    itemShapes: ListItemShapes,
    progressOverride: Float?,
    showWavyProgress: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
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
        targetValue = if (selected) scheme.secondaryContainer else scheme.surfaceContainer,
        animationSpec = motion.defaultEffectsSpec(),
        label = "library-row-container",
    )
    val colors = ListItemDefaults.colors(containerColor = container)
    val leading = @Composable {
        BookCover(book, Modifier.size(72.dp), corner = 8.dp)
    }
    val supporting = @Composable {
        Column {
            Text(
                "${book.author} · ${book.narrator}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                remainingLabel,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            if (showWavyProgress) {
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    SegmentedListItem(
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = "Select",
        shapes = itemShapes,
        colors = colors,
        leadingContent = leading,
        trailingContent = {
            AnimatedVisibility(
                visible = selecting,
                enter = fadeIn(motion.defaultEffectsSpec()) +
                    scaleIn(motion.fastSpatialSpec(), initialScale = 0.86f),
                exit = fadeOut(motion.fastEffectsSpec()) +
                    scaleOut(motion.fastSpatialSpec(), targetScale = 0.86f),
            ) {
                Checkbox(checked = selected, onCheckedChange = null)
            }
        },
        supportingContent = supporting,
    ) {
        Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
