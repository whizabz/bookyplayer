package xyz.saltedchips.bookyplayer.tts

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import xyz.saltedchips.bookyplayer.epub.EpubChapter

class SynthesisCancelled : Exception("Conversion cancelled")

/**
 * Turns one ePub chapter into an `.m4a` file with Kokoro and returns its duration in ms.
 *
 * Speech is appended to a raw PCM file piece by piece, with a small checkpoint after each piece,
 * so a paused or interrupted chapter carries on where it stopped instead of starting over. The
 * PCM is encoded to AAC once the whole chapter is spoken.
 */
class ChapterSynthesizer(private val engine: KokoroEngine) {
    fun synthesize(
        chapter: EpubChapter,
        output: File,
        voiceSid: Int,
        speed: Float = 1f,
        locale: Locale = Locale.ENGLISH,
        speakTitle: Boolean = true,
        timingOutput: File? = null,
        isCancelled: () -> Boolean = { false },
        onProgress: (Float) -> Unit = {},
    ): Long {
        output.parentFile?.mkdirs()
        val pcm = pcmFile(output)
        val state = stateFile(output)
        val cuesFile = cuesFile(output)
        val items = buildItems(chapter, locale, speakTitle)
        val totalChars = items.sumOf { it.text.length }.coerceAtLeast(1)
        val rate = engine.sampleRate

        var startIndex = 0
        var samplesSoFar = 0L
        val saved = readState(state)
        val savedCues = cuesFile.takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() }.orEmpty()
        if (saved != null && saved.total == items.size && saved.index <= items.size &&
            pcm.exists() && pcm.length() >= saved.bytes && savedCues.size >= saved.index
        ) {
            RandomAccessFile(pcm, "rw").use { it.setLength(saved.bytes) }
            cuesFile.writeText(savedCues.take(saved.index).joinToString("\n", postfix = if (saved.index > 0) "\n" else ""))
            startIndex = saved.index
            samplesSoFar = saved.bytes / 2
        } else {
            pcm.delete()
            state.delete()
            cuesFile.delete()
        }

        var doneChars = items.take(startIndex).sumOf { it.text.length }
        onProgress(doneChars.toFloat() / totalChars)
        if (startIndex < items.size) {
            FileOutputStream(pcm, true).buffered(64 * 1024).use { out ->
                FileOutputStream(cuesFile, true).bufferedWriter().use { cues ->
                    for (i in startIndex until items.size) {
                        if (isCancelled()) throw SynthesisCancelled()
                        val item = items[i]
                        val samples = engine.generate(item.text, voiceSid, speed)
                        val pause = (item.pauseSeconds * rate).toInt()
                        out.write(toPcm16(samples, pause))
                        out.flush()
                        cues.write("${item.paragraph},${item.start},${item.end},$samplesSoFar,${samplesSoFar + samples.size}\n")
                        cues.flush()
                        samplesSoFar += samples.size + pause
                        writeState(state, i + 1, pcm.length(), items.size)
                        doneChars += item.text.length
                        onProgress(doneChars.toFloat() / totalChars)
                    }
                }
            }
        }

        val writer = AacWriter(output, rate)
        try {
            pcm.inputStream().buffered(64 * 1024).use { input ->
                val block = ByteArray(32 * 1024)
                while (true) {
                    val read = input.read(block)
                    if (read <= 0) break
                    val count = read / 2
                    val floats = FloatArray(count)
                    val view = ByteBuffer.wrap(block, 0, count * 2).order(ByteOrder.LITTLE_ENDIAN)
                    for (n in 0 until count) floats[n] = view.short / 32768f
                    writer.write(floats)
                }
            }
            val duration = writer.finish()
            timingOutput?.let { TimingFile.write(it, readCues(cuesFile, rate)) }
            pcm.delete()
            state.delete()
            cuesFile.delete()
            return duration
        } catch (t: Throwable) {
            writer.close()
            output.delete()
            throw t
        }
    }

    private class Item(
        val text: String,
        val pauseSeconds: Float,
        val paragraph: Int,
        val start: Int,
        val end: Int,
    )

    private class State(val index: Int, val bytes: Long, val total: Int)

    companion object {
        private const val TITLE_PAUSE_SECONDS = Pauses.TITLE_SECONDS
        private const val PARAGRAPH_PAUSE_SECONDS = Pauses.PARAGRAPH_SECONDS
        private const val SENTENCE_PAUSE_SECONDS = Pauses.SENTENCE_SECONDS
        private const val MAX_CHUNK_CHARS = 200

        private fun pcmFile(output: File) = File(output.parentFile, output.name + ".pcm")
        private fun stateFile(output: File) = File(output.parentFile, output.name + ".state")
        private fun cuesFile(output: File) = File(output.parentFile, output.name + ".cues")

        private fun readCues(file: File, rate: Int): List<Cue> = file.readLines()
            .filter { it.isNotBlank() }
            .map { line ->
                val p = line.split(',')
                Cue(
                    paragraph = p[0].toInt(),
                    start = p[1].toInt(),
                    end = p[2].toInt(),
                    startMs = p[3].toLong() * 1000L / rate,
                    endMs = p[4].toLong() * 1000L / rate,
                )
            }

        private fun buildItems(chapter: EpubChapter, locale: Locale, speakTitle: Boolean): List<Item> {
            val items = mutableListOf<Item>()
            if (speakTitle && chapter.title.isNotBlank()) {
                val title = TextNormalizer.normalize(chapter.title.trim().trimEnd('.')) + "."
                items += Item(title, TITLE_PAUSE_SECONDS, paragraph = -1, start = 0, end = 0)
            }
            TextChunker.chunkParagraphs(Paragraphs.forChapter(chapter), locale, MAX_CHUNK_CHARS).forEach { chunk ->
                items += Item(
                    text = TextNormalizer.normalize(chunk.text),
                    pauseSeconds = if (chunk.endsParagraph) PARAGRAPH_PAUSE_SECONDS else SENTENCE_PAUSE_SECONDS,
                    paragraph = chunk.paragraph,
                    start = chunk.start,
                    end = chunk.end,
                )
            }
            return items
        }

        /** How far a paused or interrupted chapter got (0..1), without needing the engine. */
        fun savedFraction(
            chapter: EpubChapter,
            output: File,
            locale: Locale = Locale.ENGLISH,
            speakTitle: Boolean = true,
        ): Float {
            val state = readState(stateFile(output)) ?: return 0f
            val pcm = pcmFile(output)
            val items = buildItems(chapter, locale, speakTitle)
            if (state.total != items.size || !pcm.exists() || pcm.length() < state.bytes) return 0f
            val total = items.sumOf { it.text.length }.coerceAtLeast(1)
            return items.take(state.index).sumOf { it.text.length }.toFloat() / total
        }

        private fun toPcm16(samples: FloatArray, silenceSamples: Int): ByteArray {
            val buffer = ByteBuffer.allocate((samples.size + silenceSamples) * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) buffer.putShort((s * 32767f).coerceIn(-32768f, 32767f).toInt().toShort())
            return buffer.array()
        }

        private fun readState(file: File): State? = runCatching {
            val parts = file.readText().trim().split(',')
            State(parts[0].toInt(), parts[1].toLong(), parts[2].toInt())
        }.getOrNull()

        private fun writeState(file: File, index: Int, bytes: Long, total: Int) {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText("$index,$bytes,$total")
            temp.renameTo(file)
        }
    }
}
