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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.saltedchips.bookyplayer.data.Audiobook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.saltedchips.bookyplayer.library.GeneratedBookRepository
import xyz.saltedchips.bookyplayer.library.GeneratedBookStore
import xyz.saltedchips.bookyplayer.reader.EpubReaderViewModel
import xyz.saltedchips.bookyplayer.tts.Cue
import xyz.saltedchips.bookyplayer.tts.Paragraphs
import xyz.saltedchips.bookyplayer.tts.TimingFile
import xyz.saltedchips.bookyplayer.ui.components.BookyIcons

/** The generated audiobook of the open ePub, and where playback is, so the reader can read along. */
data class ReaderAudio(
    val audioBookId: String,
    val chapterDurationsMs: List<Long>,
    val loaded: Boolean,
    val chapterIndex: Int,
    val positionMs: Long,
    val isPlaying: Boolean,
    val onPlay: (chapterIndex: Int) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubReaderScreen(
    book: Audiobook,
    onBack: () -> Unit,
    onMakeAudiobook: () -> Unit,
    modifier: Modifier = Modifier,
    audio: ReaderAudio? = null,
) {
    val viewModel: EpubReaderViewModel = viewModel(key = "reader-${book.id}")
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(book.id) { viewModel.load(book.id) }
    var showChapters by remember { mutableStateOf(false) }
    var showTextMenu by remember { mutableStateOf(false) }
    var follow by remember { mutableStateOf(true) }
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    val cues by produceState<List<Cue>>(
        initialValue = emptyList(),
        state.chapterIndex,
        state.chapters.size,
        audio?.audioBookId,
        audio?.chapterDurationsMs?.size,
    ) {
        val chapter = state.chapters.getOrNull(state.chapterIndex)
        val audioInfo = audio
        value = if (chapter == null || audioInfo == null) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                val repo = GeneratedBookStore.of(context)
                val uuid = GeneratedBookRepository.uuidOf(audioInfo.audioBookId)
                TimingFile.read(repo.chapterTimingFile(uuid, state.chapterIndex))
                    ?: audioInfo.chapterDurationsMs.getOrNull(state.chapterIndex)
                        ?.let { TimingFile.estimate(chapter, it) }
                    ?: emptyList()
            }
        }
    }
    val activeCue = if (audio != null && audio.loaded && audio.chapterIndex == state.chapterIndex) {
        TimingFile.cueAt(cues, audio.positionMs)
    } else {
        null
    }
    // When the audio moves on to another chapter, the reader follows it.
    LaunchedEffect(audio?.chapterIndex, audio?.loaded) {
        val a = audio ?: return@LaunchedEffect
        if (follow && a.loaded && a.isPlaying && a.chapterIndex != state.chapterIndex) {
            viewModel.openChapter(a.chapterIndex)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (audio != null) {
                ReaderAudioBar(
                    audio = audio,
                    chapterIndex = state.chapterIndex,
                    follow = follow,
                    onFollowChange = { follow = it },
                )
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
                    IconButton(onClick = onMakeAudiobook, enabled = !state.loading && state.error == null) {
                        Icon(BookyIcons.sparkle, contentDescription = "Generate audiobook")
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
                    val paragraphs = remember(chapter) { Paragraphs.forChapter(chapter) }
                    key(state.chapterIndex) {
                        val listState = rememberLazyListState(state.scrollIndex, state.scrollOffset)
                        val chapterIndex by rememberUpdatedState(state.chapterIndex)
                        LaunchedEffect(listState) {
                            snapshotFlow {
                                Triple(chapterIndex, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                            }.collect { (c, i, o) -> if (!listState.isScrollInProgress) viewModel.savePosition(c, i, o) }
                        }
                        LaunchedEffect(activeCue?.paragraph, follow) {
                            val paragraph = activeCue?.paragraph ?: return@LaunchedEffect
                            if (!follow || listState.isScrollInProgress) return@LaunchedEffect
                            val index = paragraph + 1
                            if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                                listState.animateScrollToItem(index)
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
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
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
                                val highlight = activeCue?.takeIf { it.paragraph == paragraphIndex }
                                val highlightColor = colors.primaryContainer
                                Text(
                                    text = remember(paragraph, highlight?.start, highlight?.end, highlightColor) {
                                        buildAnnotatedString {
                                            append(paragraph)
                                            if (highlight != null) {
                                                addStyle(
                                                    SpanStyle(background = highlightColor),
                                                    highlight.start.coerceIn(0, paragraph.length),
                                                    highlight.end.coerceIn(0, paragraph.length),
                                                )
                                            }
                                        }
                                    },
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = (18f * state.textScale).sp,
                                        lineHeight = (30f * state.textScale).sp,
                                    ),
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

@Composable
private fun ReaderAudioBar(
    audio: ReaderAudio,
    chapterIndex: Int,
    follow: Boolean,
    onFollowChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    androidx.compose.material3.Surface(
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            androidx.compose.material3.FilledIconButton(onClick = { audio.onPlay(chapterIndex) }) {
                Icon(
                    if (audio.loaded && audio.isPlaying) BookyIcons.pause else BookyIcons.play,
                    contentDescription = if (audio.loaded && audio.isPlaying) "Pause" else "Listen",
                )
            }
            Text(
                if (audio.loaded) "Reading along" else "Listen from this chapter",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.FilterChip(
                selected = follow,
                onClick = { onFollowChange(!follow) },
                label = { Text("Follow") },
            )
        }
    }
}
