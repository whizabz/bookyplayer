package xyz.saltedchips.bookyplayer.library

import java.nio.charset.Charset
import xyz.saltedchips.bookyplayer.data.Audiobook

object MetadataText {
    fun repair(value: String): String {
        val source = value.trim()
        if (source.isEmpty() || !mightBeMojibake(source)) return source
        val original = garbleScore(source)
        val best = sequenceOf(
            recode(source, "windows-1252"),
            recode(source, "ISO-8859-1"),
        ).filterNotNull().minByOrNull(::garbleScore)
        return if (best != null && garbleScore(best) < original) best else source
    }

    fun looksGarbled(value: String): Boolean {
        if (value.isBlank()) return false
        if ('\uFFFD' in value) return true
        if (MOJI_MARK.containsMatchIn(value)) return true
        return garbleScore(value) >= 4
    }

    fun isWeakName(value: String): Boolean {
        val stem = value.trim()
        if (stem.isEmpty()) return true
        if (looksGarbled(stem)) return true
        return WEAK_NAME.matches(stem)
    }

    fun preferredLabel(display: String, tagged: String?): String {
        val fromDisplay = repair(display)
        val fromTag = tagged?.let(::repair)?.takeIf { it.isNotBlank() }
        if (!isWeakName(fromDisplay)) return fromDisplay
        if (fromTag != null && !isWeakName(fromTag)) return fromTag
        if (fromTag != null && garbleScore(fromTag) < garbleScore(fromDisplay)) return fromTag
        return fromDisplay.ifBlank { fromTag ?: display.trim() }
    }

    private fun recode(source: String, charsetName: String): String? {
        return try {
            val bytes = source.toByteArray(Charset.forName(charsetName))
            val out = String(bytes, Charsets.UTF_8).trim()
            if (out.isEmpty() || '\uFFFD' in out) null else out
        } catch (_: Exception) {
            null
        }
    }

    private fun mightBeMojibake(value: String): Boolean {
        return value.any { it == 'â' || it == 'Ã' || it == 'Â' || it == '\uFFFD' || it.code in 0x80..0x9F }
    }

    private fun garbleScore(value: String): Int {
        var score = 0
        for (char in value) {
            score += when {
                char == '\uFFFD' -> 6
                char.code in 0x80..0x9F -> 5
                char == 'â' || char == 'Ã' || char == 'Â' -> 3
                else -> 0
            }
        }
        if (MOJI_MARK.containsMatchIn(value)) score += 4
        return score
    }

    private val MOJI_MARK = Regex("â[€™˜œ]|Ã.|Â[\\s\\S]")
    private val WEAK_NAME = Regex(
        """^(audiobook|untitled|unknown|title|track\s*\d+|disc\s*\d+|cd\s*\d+|chapter\s*\d+|\d{1,3})$""",
        RegexOption.IGNORE_CASE,
    )
}

fun Audiobook.withRepairedText(): Audiobook {
    val titles = chapterTitles.map(MetadataText::repair)
    val chapterIndex = (currentChapter - 1).coerceAtLeast(0)
    return copy(
        title = MetadataText.repair(title),
        author = MetadataText.repair(author),
        narrator = MetadataText.repair(narrator),
        currentChapterTitle = titles.getOrNull(chapterIndex)
            ?: MetadataText.repair(currentChapterTitle),
        fileName = MetadataText.repair(fileName),
        chapterTitles = titles,
    )
}
