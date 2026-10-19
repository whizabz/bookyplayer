package xyz.saltedchips.bookyplayer.tts

import java.io.File
import java.util.Locale
import org.json.JSONArray
import xyz.saltedchips.bookyplayer.epub.EpubChapter

/**
 * A stretch of audio and the text it speaks: [start] to [end] is a range inside paragraph
 * [paragraph] of the chapter (see [Paragraphs]); a [paragraph] of -1 is the spoken chapter title.
 */
data class Cue(val paragraph: Int, val start: Int, val end: Int, val startMs: Long, val endMs: Long)

/** The read-along view switches to the next piece this long after it starts, so it never leads the voice. */
const val HIGHLIGHT_LAG_MS = 350L

/**
 * Playback positions are in the audio's own time, so at 2x the same wall-clock delay is twice as
 * much audio. Scale the lag with the speed so the highlight stays just behind the voice.
 */
fun highlightLagMs(speed: Float): Long = (HIGHLIGHT_LAG_MS * speed.coerceIn(0.5f, 3f)).toLong()

/** The silences the speech engine adds, shared so timing estimates match the real audio. */
object Pauses {
    const val TITLE_SECONDS = 0.9f
    const val PARAGRAPH_SECONDS = 0.5f
    const val SENTENCE_SECONDS = 0.15f
}

object TimingFile {
    fun write(file: File, cues: List<Cue>) {
        val json = JSONArray()
        cues.forEach { json.put(JSONArray(listOf(it.paragraph, it.start, it.end, it.startMs, it.endMs))) }
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.toString())
        temp.renameTo(file)
    }

    fun read(file: File): List<Cue>? = runCatching {
        if (!file.exists()) return null
        val json = JSONArray(file.readText())
        List(json.length()) { i ->
            val row = json.getJSONArray(i)
            Cue(row.getInt(0), row.getInt(1), row.getInt(2), row.getLong(3), row.getLong(4))
        }
    }.getOrNull()

    /**
     * Rough cues for audio generated before timing was recorded. The chapter's known pauses are
     * taken out of its length first, and the rest is shared between the title and the pieces of
     * text in proportion to their length. Can still drift a little against real speech.
     */
    fun estimate(chapter: EpubChapter, durationMs: Long, locale: Locale = Locale.ENGLISH): List<Cue> {
        val chunks = TextChunker.chunkParagraphs(Paragraphs.forChapter(chapter), locale, 200)
        if (chunks.isEmpty() || durationMs <= 0L) return emptyList()
        val titleChars = if (chapter.title.isNotBlank()) chapter.title.trim().trimEnd('.').length + 1 else 0
        val titlePauseMs = if (titleChars > 0) (Pauses.TITLE_SECONDS * 1000).toLong() else 0L
        val chunkPausesMs = chunks.sumOf { (pauseSeconds(it.endsParagraph) * 1000).toLong() }
        val speechMs = (durationMs - titlePauseMs - chunkPausesMs).coerceAtLeast(durationMs / 2)
        val totalChars = (chunks.sumOf { it.text.length } + titleChars).coerceAtLeast(1)
        val msPerChar = speechMs.toDouble() / totalChars
        var clock = titleChars * msPerChar + titlePauseMs
        return chunks.map { chunk ->
            val start = clock
            val end = start + chunk.text.length * msPerChar
            clock = end + pauseSeconds(chunk.endsParagraph) * 1000
            Cue(chunk.paragraph, chunk.start, chunk.end, start.toLong(), end.toLong())
        }
    }

    private fun pauseSeconds(endsParagraph: Boolean) =
        if (endsParagraph) Pauses.PARAGRAPH_SECONDS else Pauses.SENTENCE_SECONDS

    /** The cue being spoken at [positionMs], or null before the first spoken text. */
    fun cueAt(cues: List<Cue>, positionMs: Long): Cue? {
        if (cues.isEmpty()) return null
        var low = 0
        var high = cues.lastIndex
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (cues[mid].startMs <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        val cue = cues.getOrNull(found) ?: return null
        return cue.takeIf { it.paragraph >= 0 }
    }
}
