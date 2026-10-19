package xyz.saltedchips.bookyplayer.tts

import java.text.BreakIterator
import java.util.Locale
import xyz.saltedchips.bookyplayer.epub.EpubChapter

/**
 * One piece of text to speak. [endsParagraph] means a longer pause follows it. [paragraph], [start]
 * and [end] say where the piece sits in the chapter's original text, so a reader can highlight it.
 */
data class SpeechChunk(
    val text: String,
    val endsParagraph: Boolean,
    val paragraph: Int = 0,
    val start: Int = 0,
    val end: Int = text.length,
)

/** How a chapter's text is divided into paragraphs, shared by the speech and the reader. */
object Paragraphs {
    private val blankLine = Regex("\\n\\s*\\n")

    fun split(text: String): List<String> =
        text.split(blankLine).map { it.trim() }.filter { it.isNotEmpty() }

    /** The paragraphs of a chapter, without a first paragraph that only repeats the chapter title. */
    fun forChapter(chapter: EpubChapter): List<String> {
        val parts = split(chapter.text)
        return if (parts.firstOrNull()?.trim().equals(chapter.title.trim(), ignoreCase = true)) {
            parts.drop(1)
        } else {
            parts
        }
    }
}

/** Splits chapter text into pieces the speech model handles well (whole sentences, ~200 chars). */
object TextChunker {
    private val abbreviations = setOf(
        "mr", "mrs", "ms", "dr", "prof", "capt", "lt", "gen", "sgt", "rev", "mt", "st", "sr", "jr",
        "vs", "e.g", "i.e", "no", "col", "cmdr", "gov", "sen", "rep",
    )

    fun chunk(text: String, locale: Locale = Locale.ENGLISH, maxChars: Int = 300): List<SpeechChunk> =
        chunkParagraphs(Paragraphs.split(text), locale, maxChars)

    fun chunkParagraphs(
        paragraphs: List<String>,
        locale: Locale = Locale.ENGLISH,
        maxChars: Int = 300,
    ): List<SpeechChunk> {
        val chunks = mutableListOf<SpeechChunk>()
        paragraphs.forEachIndexed { index, paragraph ->
            val pieces = packSentences(paragraph, sentences(paragraph, locale), maxChars)
            pieces.forEachIndexed { pieceIndex, range ->
                chunks += SpeechChunk(
                    text = paragraph.substring(range.first, range.last + 1),
                    endsParagraph = pieceIndex == pieces.lastIndex,
                    paragraph = index,
                    start = range.first,
                    end = range.last + 1,
                )
            }
        }
        return chunks
    }

    /** Sentence ranges (inclusive) within [paragraph], never splitting after an abbreviation. */
    private fun sentences(paragraph: String, locale: Locale): List<IntRange> {
        val iterator = BreakIterator.getSentenceInstance(locale)
        iterator.setText(paragraph)
        val raw = mutableListOf<IntRange>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            trimmed(paragraph, start, end)?.let(raw::add)
            start = end
            end = iterator.next()
        }
        val merged = mutableListOf<IntRange>()
        for (range in raw) {
            val previous = merged.lastOrNull()
            if (previous != null && endsWithAbbreviation(paragraph.substring(previous.first, previous.last + 1))) {
                merged[merged.lastIndex] = previous.first..range.last
            } else {
                merged += range
            }
        }
        return merged
    }

    private fun trimmed(text: String, start: Int, end: Int): IntRange? {
        var s = start
        var e = end
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        return if (s < e) s until e else null
    }

    private fun endsWithAbbreviation(sentence: String): Boolean {
        if (!sentence.endsWith(".")) return false
        val word = sentence.trimEnd('.').substringAfterLast(' ').trimStart('(', '"', '\u201C', '\u2018').lowercase()
        return word in abbreviations
    }

    private fun packSentences(paragraph: String, sentences: List<IntRange>, maxChars: Int): List<IntRange> {
        val packed = mutableListOf<IntRange>()
        var current: IntRange? = null
        for (sentence in sentences) {
            for (part in splitLong(paragraph, sentence, maxChars)) {
                val open = current
                if (open != null && part.last + 1 - open.first > maxChars) {
                    packed += open
                    current = part
                } else {
                    current = if (open == null) part else open.first..part.last
                }
            }
        }
        current?.let(packed::add)
        return packed
    }

    /** Breaks a very long sentence at commas, semicolons or spaces so no piece exceeds [maxChars]. */
    private fun splitLong(paragraph: String, sentence: IntRange, maxChars: Int): List<IntRange> {
        if (sentence.last + 1 - sentence.first <= maxChars) return listOf(sentence)
        val parts = mutableListOf<IntRange>()
        var start = sentence.first
        val end = sentence.last + 1
        while (end - start > maxChars) {
            val window = paragraph.substring(start, start + maxChars)
            val cut = listOf(window.lastIndexOf(", "), window.lastIndexOf("; "), window.lastIndexOf(' '))
                .firstOrNull { it > maxChars / 3 } ?: (maxChars - 1)
            val partEnd = start + cut + 1
            trimmed(paragraph, start, partEnd)?.let(parts::add)
            start = partEnd
            while (start < end && paragraph[start].isWhitespace()) start++
        }
        trimmed(paragraph, start, end)?.let(parts::add)
        return parts
    }
}
