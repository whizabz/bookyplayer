package xyz.saltedchips.bookyplayer.tts

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A saved voice sample: mono float samples and their sample rate. */
class VoiceSample(val samples: FloatArray, val sampleRate: Int)

/**
 * Short spoken samples of each voice, generated once on this phone and kept on disk so tapping
 * preview plays instantly. The model package ships no audio samples, so they are made locally.
 */
object VoicePreviews {
    const val TEXT = "Hello, this is how I will read your book."

    private fun dir(context: Context) = File(context.filesDir, "tts/previews")
    private fun file(context: Context, sid: Int) = File(dir(context), "$sid.pcm")

    fun hasAll(context: Context): Boolean = KokoroVoices.english.all { file(context, it.sid).exists() }

    fun load(context: Context, sid: Int): VoiceSample? {
        val source = file(context, sid)
        if (!source.exists()) return null
        return runCatching {
            val buffer = ByteBuffer.wrap(source.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            val rate = buffer.int
            val samples = FloatArray((buffer.remaining()) / 2) { buffer.short / 32768f }
            VoiceSample(samples, rate)
        }.getOrNull()
    }

    fun save(context: Context, sid: Int, sample: VoiceSample) {
        val buffer = ByteBuffer.allocate(4 + sample.samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(sample.sampleRate)
        sample.samples.forEach { buffer.putShort((it * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()) }
        val target = file(context, sid)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "$sid.pcm.tmp")
        temp.writeBytes(buffer.array())
        temp.renameTo(target)
    }

    /** Makes any missing samples, one voice at a time. [generate] should serialise engine access. */
    fun generateMissing(
        context: Context,
        generate: (sid: Int) -> VoiceSample,
        isCancelled: () -> Boolean = { false },
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        val voices = KokoroVoices.english
        voices.forEachIndexed { index, voice ->
            if (isCancelled()) return
            if (!file(context, voice.sid).exists()) {
                save(context, voice.sid, generate(voice.sid))
            }
            onProgress(index + 1, voices.size)
        }
    }

    fun deleteAll(context: Context) {
        dir(context).deleteRecursively()
    }
}
