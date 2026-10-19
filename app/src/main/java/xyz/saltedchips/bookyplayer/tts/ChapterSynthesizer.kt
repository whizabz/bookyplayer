package xyz.saltedchips.bookyplayer.tts

import java.io.File
import java.util.Locale
import xyz.saltedchips.bookyplayer.epub.EpubChapter

class SynthesisCancelled : Exception("Conversion cancelled")

/** Turns one ePub chapter into an `.m4a` file with Kokoro and returns its duration in ms. */
class ChapterSynthesizer(private val engine: KokoroEngine) {
    fun synthesize(
        chapter: EpubChapter,
        output: File,
        voiceSid: Int,
        speed: Float = 1f,
        locale: Locale = Locale.ENGLISH,
        speakTitle: Boolean = true,
        isCancelled: () -> Boolean = { false },
        onProgress: (Float) -> Unit = {},
    ): Long {
        val chunks = TextChunker.chunk(chapter.text, locale)
        val totalChars = chunks.sumOf { it.text.length }.coerceAtLeast(1)
        output.parentFile?.mkdirs()
        val writer = AacWriter(output, engine.sampleRate)
        try {
            if (speakTitle && chapter.title.isNotBlank()) {
                val title = engine.generate(chapter.title.trim().trimEnd('.') + ".", voiceSid, speed, isCancelled)
                if (isCancelled()) throw SynthesisCancelled()
                writer.write(title)
                writer.writeSilence(TITLE_PAUSE_SECONDS)
            }
            var doneChars = 0
            for (chunk in chunks) {
                if (isCancelled()) throw SynthesisCancelled()
                val audio = engine.generate(chunk.text, voiceSid, speed, isCancelled)
                if (isCancelled()) throw SynthesisCancelled()
                writer.write(audio)
                writer.writeSilence(if (chunk.endsParagraph) PARAGRAPH_PAUSE_SECONDS else SENTENCE_PAUSE_SECONDS)
                doneChars += chunk.text.length
                onProgress(doneChars.toFloat() / totalChars)
            }
            return writer.finish()
        } catch (t: Throwable) {
            writer.close()
            output.delete()
            throw t
        }
    }

    private companion object {
        const val TITLE_PAUSE_SECONDS = 0.9f
        const val PARAGRAPH_PAUSE_SECONDS = 0.5f
        const val SENTENCE_PAUSE_SECONDS = 0.15f
    }
}
