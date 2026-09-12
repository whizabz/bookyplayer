package com.booky.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.booky.app.library.LibraryViewModel
import com.booky.app.library.PersistableOpenDocumentTree
import com.booky.app.player.PlayerViewModel
import com.booky.app.settings.AppearanceViewModel
import com.booky.app.theme.BookyTheme
import com.booky.app.theme.expressiveFadeTransform
import com.booky.app.theme.expressiveStackTransform
import com.booky.app.data.Audiobook
import com.booky.app.ui.library.BookDetailScreen
import com.booky.app.ui.library.LibraryScreen
import com.booky.app.ui.player.NowPlayingScreen
import com.booky.app.ui.settings.PlaceholderCoverScreen
import com.booky.app.ui.settings.SettingsScreen
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BookyApp(
    playerViewModel: PlayerViewModel = viewModel(),
    appearanceViewModel: AppearanceViewModel = viewModel(),
    libraryViewModel: LibraryViewModel = viewModel(),
) {
    val appearance by appearanceViewModel.mode.collectAsStateWithLifecycle()
    val colorTheme by appearanceViewModel.colorTheme.collectAsStateWithLifecycle()
    val placeholderCover by appearanceViewModel.placeholderCover.collectAsStateWithLifecycle()
    val library by libraryViewModel.state.collectAsStateWithLifecycle()
    val player by playerViewModel.state.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(PersistableOpenDocumentTree()) { uri ->
        if (uri != null) libraryViewModel.setFolder(uri)
    }
    val notifyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(library.playQueue.map { it.id }) {
        playerViewModel.bindLibrary(library.playQueue)
    }
    LaunchedEffect(player.book?.id) {
        player.book?.id?.let(libraryViewModel::touchLastPlayed)
    }
    val listenByBook = remember { mutableMapOf<String, Long>() }
    player.book?.id?.let { listenByBook[it] = player.bookPositionMs }
    DisposableEffect(player.book?.id) {
        val bookId = player.book?.id
        onDispose {
            if (bookId != null) {
                listenByBook[bookId]?.let { libraryViewModel.setListened(bookId, it) }
            }
        }
    }
    LaunchedEffect(player.book?.id, player.isPlaying) {
        val bookId = player.book?.id ?: return@LaunchedEffect
        if (!player.isPlaying) {
            libraryViewModel.setListened(bookId, player.bookPositionMs)
            return@LaunchedEffect
        }
        while (true) {
            delay(5_000)
            val latest = playerViewModel.state.value
            if (latest.book?.id == bookId) {
                libraryViewModel.setListened(bookId, latest.bookPositionMs)
            }
        }
    }
    BookyTheme(
        appearance = appearance,
        colorTheme = colorTheme,
        book = player.book,
        placeholderCover = placeholderCover,
    ) {
        var showingSettings by rememberSaveable { mutableStateOf(false) }
        var showingPlaceholderCover by rememberSaveable { mutableStateOf(false) }
        var detailBookId by rememberSaveable { mutableStateOf<String?>(null) }
        var nowPlaying by rememberSaveable { mutableStateOf(false) }
        val canvas = MaterialTheme.colorScheme.background
        BackHandler(enabled = showingPlaceholderCover) { showingPlaceholderCover = false }
        BackHandler(enabled = showingSettings && !showingPlaceholderCover) {
            showingSettings = false
        }
        BackHandler(enabled = detailBookId != null && !showingSettings) { detailBookId = null }
        val miniPlayerClearance = if (player.book != null) 104.dp else 0.dp
        val playBook: (Audiobook) -> Unit = { book ->
            if (player.book?.id == book.id) {
                playerViewModel.togglePlay()
            } else {
                playerViewModel.selectBook(book, play = true)
            }
        }
        LaunchedEffect(detailBookId, library.books) {
            if (detailBookId != null && library.books.none { it.id == detailBookId }) {
                detailBookId = null
            }
        }

        Box(Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .background(canvas)
                    .imePadding(),
                containerColor = canvas,
            ) { padding ->
                val screenModifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                val detailBook = library.books.find { it.id == detailBookId }
                val route = when {
                    showingPlaceholderCover -> AppRoute.Placeholder
                    showingSettings -> AppRoute.Settings
                    detailBook != null -> AppRoute.Details
                    else -> AppRoute.Library
                }
                val motion = MaterialTheme.motionScheme
                AnimatedContent(
                    targetState = route,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        when {
                            targetState.rank > initialState.rank ->
                                motion.expressiveStackTransform(forward = true)
                            targetState.rank < initialState.rank ->
                                motion.expressiveStackTransform(forward = false)
                            else -> motion.expressiveFadeTransform()
                        }
                    },
                    contentKey = { pane ->
                        if (pane == AppRoute.Details) "details-${detailBookId.orEmpty()}" else pane.name
                    },
                    label = "main-pane",
                ) { pane ->
                    when (pane) {
                        AppRoute.Placeholder -> PlaceholderCoverScreen(
                            style = placeholderCover,
                            previewTitle = player.book?.title ?: "Pride and Prejudice",
                            onStyleChange = appearanceViewModel::setPlaceholderCover,
                            onBack = { showingPlaceholderCover = false },
                            bottomContentPadding = miniPlayerClearance,
                            modifier = Modifier.fillMaxSize(),
                        )
                        AppRoute.Settings -> SettingsScreen(
                            appearance = appearance,
                            onAppearanceChange = appearanceViewModel::setMode,
                            colorTheme = colorTheme,
                            onColorThemeChange = appearanceViewModel::setColorTheme,
                            skipBackSeconds = player.skipBackSeconds,
                            skipForwardSeconds = player.skipForwardSeconds,
                            onSkipBackSecondsChange = playerViewModel::setSkipBackSeconds,
                            onSkipForwardSecondsChange = playerViewModel::setSkipForwardSeconds,
                            smartResumeEnabled = player.smartResumeEnabled,
                            smartResumeSeconds = player.smartResumeSeconds,
                            onSmartResumeEnabledChange = playerViewModel::setSmartResumeEnabled,
                            onSmartResumeSecondsChange = playerViewModel::setSmartResumeSeconds,
                            folderPath = library.folderPath,
                            onChooseFolder = { pickFolder.launch(null) },
                            onOpenPlaceholderCover = { showingPlaceholderCover = true },
                            onBack = {
                                showingPlaceholderCover = false
                                showingSettings = false
                            },
                            bottomContentPadding = miniPlayerClearance,
                            modifier = screenModifier,
                        )
                        AppRoute.Details -> {
                            val book = detailBook
                            if (book == null) {
                                Box(Modifier.fillMaxSize())
                            } else {
                                BookDetailScreen(
                                    book = book,
                                    player = player,
                                    savedChapterProgress = playerViewModel.peekChapterProgress(book.id),
                                    completedChapters = playerViewModel.peekCompletedChapters(book),
                                    onPlay = { playBook(book) },
                                    onPlayChapter = { index ->
                                        playerViewModel.playFromChapter(book, index)
                                    },
                                    onSetChaptersPlayed = { indices, played ->
                                        playerViewModel.setChaptersPlayed(book.id, indices, played)
                                    },
                                    onSnapshotMarks = { playerViewModel.snapshotChapterMarks(book.id) },
                                    onRestoreMarks = { snapshot ->
                                        playerViewModel.restoreChapterMarks(book.id, snapshot)
                                    },
                                    onUpdateMetadata = { title, author, narrator, chapters, cover ->
                                        libraryViewModel.updateMetadata(
                                            book.id,
                                            title,
                                            author,
                                            narrator,
                                            chapters,
                                            cover,
                                        )
                                        libraryViewModel.state.value.books
                                            .find { it.id == book.id }
                                            ?.let(playerViewModel::patchDisplayedBook)
                                    },
                                    onDelete = {
                                        if (player.book?.id == book.id) {
                                            nowPlaying = false
                                            playerViewModel.closeBook()
                                        }
                                        libraryViewModel.delete(listOf(book.id))
                                        detailBookId = null
                                    },
                                    onMarkPlayed = {
                                        libraryViewModel.markPlayed(listOf(book.id))
                                        if (player.book?.id == book.id) {
                                            playerViewModel.markBookPlayed()
                                        }
                                    },
                                    onBack = { detailBookId = null },
                                    bottomContentPadding = 24.dp + miniPlayerClearance,
                                    modifier = screenModifier,
                                )
                            }
                        }
                        AppRoute.Library -> LibraryScreen(
                            books = library.books,
                            activeBookId = player.book?.id,
                            isPlaying = player.isPlaying,
                            playbackProgress = if (player.bookDurationMs == 0L) {
                                0f
                            } else {
                                (player.bookPositionMs.toFloat() / player.bookDurationMs)
                                    .coerceIn(0f, 1f)
                            },
                            hasFolder = library.folderUri != null,
                            folderName = library.folderName,
                            scanning = library.scanning,
                            sort = library.sort,
                            onSortChange = libraryViewModel::setSort,
                            onMarkPlayed = { books ->
                                libraryViewModel.markPlayed(books.map { it.id })
                                if (books.any { it.id == player.book?.id }) {
                                    playerViewModel.markBookPlayed()
                                }
                            },
                            onDelete = { books ->
                                val ids = books.map { it.id }
                                if (detailBookId in ids) detailBookId = null
                                if (player.book?.id in ids) {
                                    nowPlaying = false
                                    playerViewModel.closeBook()
                                }
                                libraryViewModel.delete(ids)
                            },
                            onUpdateMetadata = { id, title, author, narrator, chapters, cover ->
                                libraryViewModel.updateMetadata(
                                    id,
                                    title,
                                    author,
                                    narrator,
                                    chapters,
                                    cover,
                                )
                                libraryViewModel.state.value.books
                                    .find { it.id == id }
                                    ?.let(playerViewModel::patchDisplayedBook)
                            },
                            onChooseFolder = { pickFolder.launch(null) },
                            onOpenBook = { opened -> detailBookId = opened.id },
                            onPlayBook = playBook,
                            onOpenSettings = { showingSettings = true },
                            bottomContentPadding = 24.dp + miniPlayerClearance,
                            modifier = screenModifier,
                        )
                    }
                }
            }

            val book = player.book
            if (book != null) {
                NowPlayingScreen(
                    player = player,
                    expanded = nowPlaying,
                    onExpandedChange = { nowPlaying = it },
                    onTogglePlay = playerViewModel::togglePlay,
                    onSkip = playerViewModel::skip,
                    onSeek = playerViewModel::seek,
                    onSeekChapter = playerViewModel::seekToChapter,
                    onSetSpeed = playerViewModel::setSpeed,
                    onSetSleepTimer = playerViewModel::setSleepTimer,
                    onRestartChapter = playerViewModel::restartChapter,
                    onMarkChapterPlayed = playerViewModel::markChapterPlayed,
                    onMarkBookPlayed = playerViewModel::markBookPlayed,
                    onSetChaptersPlayed = { indices, played ->
                        player.book?.id?.let { id ->
                            playerViewModel.setChaptersPlayed(id, indices, played)
                        }
                    },
                    onSnapshotMarks = {
                        player.book?.id?.let(playerViewModel::snapshotChapterMarks)
                            ?: com.booky.app.player.ChapterMarksSnapshot(emptySet(), emptyMap())
                    },
                    onRestoreMarks = { snapshot ->
                        player.book?.id?.let { playerViewModel.restoreChapterMarks(it, snapshot) }
                    },
                    onToggleRepeat = playerViewModel::toggleRepeat,
                    onCloseBook = {
                        nowPlaying = false
                        playerViewModel.closeBook()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private enum class AppRoute(val rank: Int) {
    Library(0),
    Details(1),
    Settings(2),
    Placeholder(3),
}
