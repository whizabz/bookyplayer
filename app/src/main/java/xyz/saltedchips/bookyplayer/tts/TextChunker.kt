package xyz.saltedchips.bookyplayer.tts

import java.text.BreakIterator
import java.util.Locale

/** One piece of text to speak. [endsParagraph] means a longer pause follows it. */
data class SpeechChunk(val text: String, val endsParagraph: Boolean)

/** Splits chapter text into pieces the speech model handles well (whole sentences, ~300 chars). */
object TextChunker {
    fun chunk(text: String, locale: Locale = Locale.ENGLISH, maxChars: Int = 300): List<SpeechChunk> {
        val chunks = mutableListOf<SpeechChunk>()
        for (paragraph in text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }) {
            val pieces = packSentences(sentences(paragraph, locale), maxChars)
            pieces.forEachIndexed { index, piece ->
                chunks += SpeechChunk(piece, endsParagraph = index == pieces.lastIndex)
            }
        }
        return chunks
    }

    private fun sentences(paragraph: String, locale: Locale): List<String> {
        val iterator = BreakIterator.getSentenceInstance(locale)
        iterator.setText(paragraph)
        val result = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            paragraph.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let(result::add)
            start = end
            end = iterator.next()
        }
        return result
    }

    private fun packSentences(sentences: List<String>, maxChars: Int): List<String> {
        val packed = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) packed += current.toString().trim()
            current.clear()
        }
        for (sentence in sentences) {
            for (part in splitLong(sentence, maxChars)) {
                if (current.isNotEmpty() && current.length + part.length + 1 > maxChars) flush()
                if (current.isNotEmpty()) current.append(' ')
                current.append(part)
            }
        }
        flush()
        return packed
    }

    /** Breaks a very long sentence at commas, semicolons or spaces so no piece exceeds [maxChars]. */
    private fun splitLong(sentence: String, maxChars: Int): List<String> {
        if (sentence.length <= maxChars) return listOf(sentence)
        val parts = mutableListOf<String>()
        var rest = sentence
        while (rest.length > maxChars) {
            val window = rest.substring(0, maxChars)
            val cut = listOf(window.lastIndexOf(", "), window.lastIndexOf("; "), window.lastIndexOf(' '))
                .firstOrNull { it > maxChars / 3 } ?: maxChars
            parts += rest.substring(0, cut + 1).trim()
            rest = rest.substring(cut + 1).trim()
        }
        if (rest.isNotEmpty()) parts += rest
        return parts
    }
}
