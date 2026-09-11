package com.booky.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import com.booky.app.ui.library.LibraryScreen
import com.booky.app.ui.player.NowPlayingScreen
import com.booky.app.ui.settings.SettingsScreen
import kotlinx.coroutines.delay

@Composable
fun BookyApp(
    playerViewModel: PlayerViewModel = viewModel(),
    appearanceViewModel: AppearanceViewModel = viewModel(),
    libraryViewModel: LibraryViewModel = viewModel(),
) {
    val appearance by appearanceViewModel.mode.collectAsStateWithLifecycle()
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
    BookyTheme(appearance = appearance, book = player.book) {
        var showingSettings by rememberSaveable { mutableStateOf(false) }
        var nowPlaying by rememberSaveable { mutableStateOf(false) }
        val canvas = MaterialTheme.colorScheme.background
        BackHandler(enabled = showingSettings) { showingSettings = false }
        val miniPlayerClearance = if (player.book != null) 104.dp else 0.dp

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
                if (showingSettings) {
                    SettingsScreen(
                        appearance = appearance,
                        onAppearanceChange = appearanceViewModel::setMode,
                        folderPath = library.folderPath,
                        onChooseFolder = { pickFolder.launch(null) },
                        onBack = { showingSettings = false },
                        bottomContentPadding = miniPlayerClearance,
                        modifier = screenModifier,
                    )
                } else {
                    LibraryScreen(
                        books = library.books,
                        activeBookId = player.book?.id,
                        isPlaying = player.isPlaying,
                        playbackProgress = if (player.bookDurationMs == 0L) {
                            0f
                        } else {
                            (player.bookPositionMs.toFloat() / player.bookDurationMs).coerceIn(0f, 1f)
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
                            if (player.book?.id in ids) {
                                nowPlaying = false
                                playerViewModel.closeBook()
                            }
                            libraryViewModel.delete(ids)
                        },
                        onUpdateMetadata = { id, title, author, narrator, chapters, cover ->
                            libraryViewModel.updateMetadata(id, title, author, narrator, chapters, cover)
                            libraryViewModel.state.value.books
                                .find { it.id == id }
                                ?.let(playerViewModel::patchDisplayedBook)
                        },
                        onChooseFolder = { pickFolder.launch(null) },
                        onOpenBook = { book ->
                            libraryViewModel.touchLastPlayed(book.id)
                            playerViewModel.selectBook(book)
                            nowPlaying = true
                        },
                        onOpenSettings = { showingSettings = true },
                        bottomContentPadding = 24.dp + miniPlayerClearance,
                        modifier = screenModifier,
                    )
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
