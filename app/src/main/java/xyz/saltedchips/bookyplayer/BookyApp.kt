package xyz.saltedchips.bookyplayer

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.saltedchips.bookyplayer.library.LibraryViewModel
import xyz.saltedchips.bookyplayer.library.PersistableOpenDocumentTree
import xyz.saltedchips.bookyplayer.player.PlayerViewModel
import xyz.saltedchips.bookyplayer.settings.AppearanceViewModel
import xyz.saltedchips.bookyplayer.theme.BookyTheme
import xyz.saltedchips.bookyplayer.theme.expressiveFadeTransform
import xyz.saltedchips.bookyplayer.theme.expressiveStackTransform
import xyz.saltedchips.bookyplayer.data.Audiobook
import xyz.saltedchips.bookyplayer.ui.library.BookDetailScreen
import xyz.saltedchips.bookyplayer.ui.library.LibraryScreen
import xyz.saltedchips.bookyplayer.ui.player.NowPlayingScreen
import xyz.saltedchips.bookyplayer.ui.settings.PlaceholderCoverScreen
import xyz.saltedchips.bookyplayer.ui.settings.SettingsScreen
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
    val notificationsPrompted by appearanceViewModel.notificationsPrompted.collectAsStateWithLifecycle()
    val library by libraryViewModel.state.collectAsStateWithLifecycle()
    val player by playerViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationsEnabled by remember {
        mutableStateOf(playbackNotificationsGranted(context))
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = playbackNotificationsGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var pendingPlay by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showNotificationExplainer by rememberSaveable { mutableStateOf(false) }
    val pickFolder = rememberLauncherForActivityResult(PersistableOpenDocumentTree()) { uri ->
        if (uri != null) libraryViewModel.setFolder(uri)
    }
    val notifyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        notificationsEnabled = playbackNotificationsGranted(context)
        pendingPlay?.invoke()
        pendingPlay = null
    }
    fun needsNotificationExplainer(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        if (notificationsPrompted) return false
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        return !granted
    }
    fun withPlaybackReady(action: () -> Unit) {
        if (needsNotificationExplainer()) {
            pendingPlay = action
            showNotificationExplainer = true
        } else {
            action()
        }
    }
    fun finishNotificationExplainer(requestPermission: Boolean) {
        appearanceViewModel.setNotificationsPrompted(true)
        showNotificationExplainer = false
        if (requestPermission && Build.VERSION.SDK_INT >= 33) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            pendingPlay?.invoke()
            pendingPlay = null
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
        val miniPlayerClearance =
            if (player.book != null && !showingSettings && !showingPlaceholderCover) {
                104.dp
            } else {
                0.dp
            }
        val playBook: (Audiobook) -> Unit = { book ->
            if (player.book?.id == book.id && player.isPlaying) {
                playerViewModel.togglePlay()
            } else {
                withPlaybackReady {
                    if (player.book?.id == book.id) {
                        playerViewModel.togglePlay()
                    } else {
                        playerViewModel.selectBook(book, play = true)
                    }
                }
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
                            playbackNotificationsEnabled = if (Build.VERSION.SDK_INT >= 33) {
                                notificationsEnabled
                            } else {
                                null
                            },
                            onPlaybackNotificationsClick = {
                                if (Build.VERSION.SDK_INT >= 33 &&
                                    !playbackNotificationsGranted(context)
                                ) {
                                    notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
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
                                        withPlaybackReady {
                                            playerViewModel.playFromChapter(book, index)
                                        }
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
                            scanDone = library.scanDone,
                            scanTotal = library.scanTotal,
                            scanLabel = library.scanLabel,
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
            if (book != null && !showingSettings && !showingPlaceholderCover) {
                NowPlayingScreen(
                    player = player,
                    expanded = nowPlaying,
                    onExpandedChange = { nowPlaying = it },
                    onTogglePlay = {
                        if (player.isPlaying) {
                            playerViewModel.togglePlay()
                        } else {
                            withPlaybackReady { playerViewModel.togglePlay() }
                        }
                    },
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
                            ?: xyz.saltedchips.bookyplayer.player.ChapterMarksSnapshot(emptySet(), emptyMap())
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
        if (showNotificationExplainer) {
            AlertDialog(
                onDismissRequest = { finishNotificationExplainer(requestPermission = false) },
                title = { Text("Stay in control while you listen") },
                text = {
                    Text(
                        "Booky Player shows a playback notification so you can pause, skip, " +
                            "and see the current book from the lock screen and notification shade " +
                            "when you leave the app. It is not used for ads or other alerts. " +
                            "You can still play books in the app if you skip this.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = { finishNotificationExplainer(requestPermission = true) }) {
                        Text("Continue")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { finishNotificationExplainer(requestPermission = false) }) {
                        Text("Not now")
                    }
                },
            )
        }
    }
}

private enum class AppRoute(val rank: Int) {
    Library(0),
    Details(1),
    Settings(2),
    Placeholder(3),
}

private fun playbackNotificationsGranted(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}
