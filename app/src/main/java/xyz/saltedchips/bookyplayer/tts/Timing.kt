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
     * Rough cues for audio generated before timing was recorded: each piece of text gets a share
     * of the chapter's audio in proportion to its length. Can drift a little against real speech.
     */
    fun estimate(chapter: EpubChapter, durationMs: Long, locale: Locale = Locale.ENGLISH): List<Cue> {
        val chunks = TextChunker.chunkParagraphs(Paragraphs.forChapter(chapter), locale, 200)
        if (chunks.isEmpty() || durationMs <= 0L) return emptyList()
        val total = chunks.sumOf { it.text.length }.coerceAtLeast(1)
        var done = 0
        return chunks.map { chunk ->
            val startMs = durationMs * done / total
            done += chunk.text.length
            Cue(chunk.paragraph, chunk.start, chunk.end, startMs, durationMs * done / total)
        }
    }

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
