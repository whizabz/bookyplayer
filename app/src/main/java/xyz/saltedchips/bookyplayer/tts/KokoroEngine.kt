package xyz.saltedchips.bookyplayer.tts

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.Closeable
import java.io.File

/** On-device Kokoro speech synthesis through sherpa-onnx. Not thread-safe; use from one worker. */
class KokoroEngine(private val modelDir: File, numThreads: Int = 3) : Closeable {
    private fun path(name: String) = File(modelDir, name).absolutePath

    private val tts = OfflineTts(
        config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = path("model.onnx"),
                    voices = path("voices.bin"),
                    tokens = path("tokens.txt"),
                    dataDir = path("espeak-ng-data"),
                    lexicon = "${path("lexicon-us-en.txt")},${path("lexicon-zh.txt")}",
                    lang = "en-us",
                    dictDir = path("dict"),
                ),
                numThreads = numThreads,
                debug = false,
                provider = "cpu",
            ),
            ruleFsts = listOf("date-zh.fst", "number-zh.fst", "phone-zh.fst")
                .joinToString(",") { path(it) },
        ),
    )

    val sampleRate: Int get() = tts.sampleRate()

    /** Speaks [text] and returns mono float samples at [sampleRate]. [speed] 1.0 is normal. */
    fun generate(
        text: String,
        sid: Int,
        speed: Float = 1f,
        isCancelled: () -> Boolean = { false },
    ): FloatArray {
        if (isCancelled()) return FloatArray(0)
        return tts.generate(text = text, sid = sid, speed = speed).samples
    }

    override fun close() {
        tts.release()
    }
}
