package xyz.saltedchips.bookyplayer.ui.player

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.onSizeChanged
import xyz.saltedchips.bookyplayer.ui.components.BookyIcons
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.saltedchips.bookyplayer.data.Audiobook
import xyz.saltedchips.bookyplayer.epub.EpubChapter
import xyz.saltedchips.bookyplayer.library.GeneratedBookRepository
import xyz.saltedchips.bookyplayer.library.GeneratedBookStore
import xyz.saltedchips.bookyplayer.tts.Cue
import xyz.saltedchips.bookyplayer.tts.highlightLagMs
import xyz.saltedchips.bookyplayer.tts.Paragraphs
import xyz.saltedchips.bookyplayer.tts.TimingFile

/** The spoken pieces of one chapter: the text of each, and when its audio starts. */
class ReadAlongData(val lines: List<String>, val startsMs: List<Long>) {
    /** Index of the piece being spoken at [positionMs], or -1 before the first one. */
    fun activeIndex(positionMs: Long): Int {
        var low = 0
        var high = startsMs.lastIndex
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (startsMs[mid] <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }

    companion object {
        val Empty = ReadAlongData(emptyList(), emptyList())
    }
}

/** Loads the chapter text and its timing for a generated audiobook; empty for any other book. */
@Composable
fun rememberReadAlongData(
    context: Context,
    book: Audiobook,
    chapterIndex: Int,
    enabled: Boolean,
): ReadAlongData {
    val durationMs = book.chapterDurationsMs.getOrNull(chapterIndex)
    val data by produceState(ReadAlongData.Empty, book.id, chapterIndex, enabled, durationMs) {
        value = if (!enabled || !GeneratedBookRepository.isGenerated(book.id)) {
            ReadAlongData.Empty
        } else {
            withContext(Dispatchers.IO) { loadReadAlong(context, book, chapterIndex, durationMs) }
        }
    }
    return data
}

private fun loadReadAlong(context: Context, book: Audiobook, chapterIndex: Int, durationMs: Long?): ReadAlongData {
    val repo = GeneratedBookStore.of(context)
    val uuid = GeneratedBookRepository.uuidOf(book.id)
    val textFile = repo.chapterTextFile(uuid, chapterIndex)
    if (!textFile.exists()) return ReadAlongData.Empty
    val chapter = EpubChapter(book.chapterTitles.getOrNull(chapterIndex).orEmpty(), textFile.readText())
    val paragraphs = Paragraphs.forChapter(chapter)
    val cues: List<Cue> = TimingFile.read(repo.chapterTimingFile(uuid, chapterIndex))
        ?: durationMs?.let { TimingFile.estimate(chapter, it) }
        ?: emptyList()
    val spoken = cues.filter { it.paragraph in paragraphs.indices }
    return ReadAlongData(
        lines = spoken.map { cue ->
            val text = paragraphs[cue.paragraph]
            text.substring(cue.start.coerceIn(0, text.length), cue.end.coerceIn(0, text.length))
        },
        startsMs = spoken.map { it.startMs },
    )
}

/**
 * Lyrics-style reading view: the piece being spoken sits in the middle at full strength, and the
 * text above and below it fades away towards the top and bottom edges. Scrolling away pauses the
 * following; a small button brings you back to the spoken text.
 */
@Composable
fun ReadAlongView(
    data: ReadAlongData,
    positionMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    speed: Float = 1f,
) {
    // Switch a moment after the next piece begins, so the text never runs ahead of the voice.
    val active = data.activeIndex(positionMs - highlightLagMs(speed)).coerceAtLeast(0)
    val listState = rememberLazyListState()
    var following by remember { mutableStateOf(true) }
    var programmatic by remember { mutableStateOf(false) }
    var viewportHeightPx by remember { mutableStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Where the spoken line rests while following (the list's top padding), and how far off is too far.
    val restPx = viewportHeightPx * 0.3f
    val tolerancePx = with(density) { 48.dp.toPx() }
    val activeInPlace by remember(active, restPx) {
        derivedStateOf {
            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == active }
            item != null && abs(item.offset - restPx) <= tolerancePx
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && !programmatic) following = false
        }
    }
    LaunchedEffect(data) { following = true }
    LaunchedEffect(active, following, data.lines.size) {
        if (following && data.lines.isNotEmpty()) {
            programmatic = true
            try {
                listState.animateScrollToItem(active)
            } finally {
                programmatic = false
            }
        }
    }
    Box(modifier = modifier.onSizeChanged { viewportHeightPx = it.height }) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.2f to Color.Black,
                            0.7f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            val topInset = maxHeight * 0.3f
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(top = topInset, bottom = maxHeight * 0.6f, start = 8.dp, end = 8.dp),
            ) {
                itemsIndexed(data.lines) { index, line ->
                    val distance = abs(index - active)
                    val target = when (distance) {
                        0 -> 1f
                        1 -> 0.4f
                        2 -> 0.22f
                        else -> 0.12f
                    }
                    val alpha by animateFloatAsState(target, label = "read-along-alpha")
                    Text(
                        text = line,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = MaterialTheme.typography.headlineSmall.lineHeight * 1.1f,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .graphicsLayer { this.alpha = alpha }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                following = true
                                onSeekTo(data.startsMs[index])
                            }
                            .padding(vertical = 10.dp),
                    )
                }
            }
        }
        // Shown as soon as the spoken line is out of view or has drifted from its place, in
        // whichever direction you scrolled.
        if (!following && !activeInPlace && data.lines.isNotEmpty()) {
            androidx.compose.material3.SmallExtendedFloatingActionButton(
                onClick = { following = true },
                icon = { Icon(BookyIcons.sync, contentDescription = null) },
                text = { Text("Sync to audio") },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
            )
        }
    }
}
