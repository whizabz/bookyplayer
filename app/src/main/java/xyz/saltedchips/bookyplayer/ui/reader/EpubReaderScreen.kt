package xyz.saltedchips.bookyplayer.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.saltedchips.bookyplayer.data.Audiobook
import xyz.saltedchips.bookyplayer.tts.TimingFile
import xyz.saltedchips.bookyplayer.tts.Cue
import xyz.saltedchips.bookyplayer.tts.highlightLagMs
import xyz.saltedchips.bookyplayer.ui.components.RoundedHighlightText
import xyz.saltedchips.bookyplayer.library.GeneratedBookStore
import xyz.saltedchips.bookyplayer.library.GeneratedBookRepository
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.produceState
import xyz.saltedchips.bookyplayer.reader.EpubReaderViewModel
import xyz.saltedchips.bookyplayer.ui.components.BookyIcons

/** Where the linked audiobook is, so the reader can offer to jump to the spoken text. */
data class ReaderSync(
    val audioBookId: String,
    val chapterDurationsMs: List<Long>,
    val loaded: Boolean,
    val chapterIndex: Int,
    val positionMs: Long,
    val speed: Float = 1f,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubReaderScreen(
    book: Audiobook,
    onBack: () -> Unit,
    onMakeAudiobook: () -> Unit,
    modifier: Modifier = Modifier,
    /** For a book that already has audio: regenerate every chapter (null) or one chapter. */
    onRegenerate: ((chapter: Int?) -> Unit)? = null,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    sync: ReaderSync? = null,
) {
    val viewModel: EpubReaderViewModel = viewModel(key = "reader-${book.id}")
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(book.id) { viewModel.load(book.id) }
    var showChapters by remember { mutableStateOf(false) }
    var showTextMenu by remember { mutableStateOf(false) }
    var showAudioMenu by remember { mutableStateOf(false) }
    var syncPending by remember { mutableStateOf(false) }
    var activeVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Cues for the chapter the audio is in, to find the paragraph being spoken.
    val syncCues by produceState<List<Cue>>(
        initialValue = emptyList(),
        sync?.audioBookId,
        sync?.chapterIndex,
        sync?.loaded,
        state.chapters.size,
    ) {
        val info = sync
        val chapter = info?.let { state.chapters.getOrNull(it.chapterIndex) }
        value = if (info == null || chapter == null || !info.loaded) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                val repo = GeneratedBookStore.of(context)
                val uuid = GeneratedBookRepository.uuidOf(info.audioBookId)
                TimingFile.read(repo.chapterTimingFile(uuid, info.chapterIndex))
                    ?: info.chapterDurationsMs.getOrNull(info.chapterIndex)
                        ?.let { TimingFile.estimate(chapter, it) }
                    ?: emptyList()
            }
        }
    }
    val activeCue: Cue? = if (sync != null && sync.loaded) {
        TimingFile.cueAt(syncCues, sync.positionMs - highlightLagMs(sync.speed))
    } else {
        null
    }
    val activeParagraph: Int? = activeCue?.paragraph
    val colors = MaterialTheme.colorScheme

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0),
        floatingActionButtonPosition = androidx.compose.material3.FabPosition.Center,
        floatingActionButton = {
            val offTarget = sync != null && sync.loaded && activeParagraph != null &&
                (state.chapterIndex != sync.chapterIndex || !activeVisible)
            if (offTarget) {
                androidx.compose.material3.SmallFloatingActionButton(
                    onClick = {
                        if (sync != null && state.chapterIndex != sync.chapterIndex) {
                            viewModel.openChapter(sync.chapterIndex)
                        }
                        syncPending = true
                    },
                    modifier = Modifier.padding(bottom = bottomPadding),
                ) {
                    Icon(BookyIcons.sync, contentDescription = "Sync to audio")
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(BookyIcons.back, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = {
                                if (onRegenerate != null) showAudioMenu = true else onMakeAudiobook()
                            },
                            enabled = !state.loading && state.error == null,
                        ) {
                            Icon(
                                BookyIcons.sparkle,
                                contentDescription = if (onRegenerate != null) "Audiobook options" else "Generate audiobook",
                            )
                        }
                        DropdownMenu(expanded = showAudioMenu, onDismissRequest = { showAudioMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Regenerate this chapter") },
                                onClick = {
                                    showAudioMenu = false
                                    onRegenerate?.invoke(state.chapterIndex)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Regenerate whole audiobook") },
                                onClick = {
                                    showAudioMenu = false
                                    onRegenerate?.invoke(null)
                                },
                            )
                        }
                    }
                    IconButton(onClick = { showChapters = true }, enabled = state.chapters.isNotEmpty()) {
                        Icon(BookyIcons.chapters, contentDescription = "Chapters")
                    }
                    Box {
                        IconButton(onClick = { showTextMenu = true }) {
                            Text("Aa", style = MaterialTheme.typography.titleMedium)
                        }
                        DropdownMenu(expanded = showTextMenu, onDismissRequest = { showTextMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Larger text") },
                                onClick = { viewModel.adjustTextScale(0.1f) },
                            )
                            DropdownMenuItem(
                                text = { Text("Smaller text") },
                                onClick = { viewModel.adjustTextScale(-0.1f) },
                            )
                        }
                    }
                },
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                state.loading -> CircularProgressIndicator()
                state.error != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text(state.error.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = { viewModel.load(book.id) }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Try again")
                    }
                }
                else -> {
                    val chapter = state.chapters[state.chapterIndex]
                    val paragraphs = remember(chapter) {
                        val parts = chapter.text.split("\n\n")
                        if (parts.firstOrNull()?.trim().equals(chapter.title.trim(), ignoreCase = true)) {
                            parts.drop(1)
                        } else {
                            parts
                        }
                    }
                    key(state.chapterIndex) {
                        val listState = rememberLazyListState(state.scrollIndex, state.scrollOffset)
                        val chapterIndex by rememberUpdatedState(state.chapterIndex)
                        LaunchedEffect(listState) {
                            snapshotFlow {
                                Triple(chapterIndex, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                            }.collect { (c, i, o) -> if (!listState.isScrollInProgress) viewModel.savePosition(c, i, o) }
                        }
                        LaunchedEffect(listState, activeParagraph, sync?.chapterIndex) {
                            snapshotFlow {
                                val target = activeParagraph?.plus(1)
                                sync?.chapterIndex == chapterIndex && target != null &&
                                    listState.layoutInfo.visibleItemsInfo.any { it.index == target }
                            }.collect { activeVisible = it }
                        }
                        LaunchedEffect(syncPending, activeParagraph, state.chapterIndex) {
                            val paragraph = activeParagraph
                            if (syncPending && sync?.chapterIndex == state.chapterIndex && paragraph != null) {
                                listState.animateScrollToItem(paragraph + 1)
                                syncPending = false
                            }
                        }
                        DisposableEffect(listState) {
                            onDispose {
                                viewModel.savePosition(
                                    chapterIndex,
                                    listState.firstVisibleItemIndex,
                                    listState.firstVisibleItemScrollOffset,
                                )
                            }
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 16.dp + bottomPadding),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            item("title") {
                                Text(
                                    chapter.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                            itemsIndexed(paragraphs) { paragraphIndex, paragraph ->
                                val highlight = activeCue?.takeIf {
                                    it.paragraph == paragraphIndex && sync?.chapterIndex == state.chapterIndex
                                }
                                RoundedHighlightText(
                                    text = paragraph,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = (18f * state.textScale).sp,
                                        lineHeight = (30f * state.textScale).sp,
                                        color = colors.onBackground,
                                    ),
                                    highlightStart = highlight?.start ?: 0,
                                    highlightEnd = highlight?.end ?: 0,
                                    highlightColor = colors.primaryContainer,
                                )
                            }
                            item("nav") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .padding(top = 16.dp, bottom = 32.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.openChapter(state.chapterIndex - 1) },
                                        enabled = state.chapterIndex > 0,
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Previous") }
                                    Button(
                                        onClick = { viewModel.openChapter(state.chapterIndex + 1) },
                                        enabled = state.chapterIndex < state.chapters.lastIndex,
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Next chapter") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showChapters) {
        ModalBottomSheet(onDismissRequest = { showChapters = false }) {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(state.chapters) { index, chapter ->
                    Text(
                        chapter.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (index == state.chapterIndex) colors.primary else colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.openChapter(index)
                                showChapters = false
                            }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}
