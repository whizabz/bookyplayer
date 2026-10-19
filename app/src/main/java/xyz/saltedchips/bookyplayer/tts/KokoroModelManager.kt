package xyz.saltedchips.bookyplayer.tts

import android.content.Context
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

/**
 * Downloads and unpacks the Kokoro voice model (Apache-2.0, from the sherpa-onnx releases) into
 * app storage. The archive is streamed straight through bzip2 and tar, so it is never stored.
 */
class KokoroModelManager(context: Context) {
    private val root = File(context.filesDir, "tts")
    val modelDir = File(root, MODEL_NAME)
    private val partialDir = File(root, "$MODEL_NAME.partial")

    fun isInstalled(): Boolean = File(modelDir, COMPLETE_MARKER).exists() &&
        File(modelDir, "model.onnx").exists() &&
        File(modelDir, "voices.bin").exists() &&
        File(modelDir, "tokens.txt").exists() &&
        File(modelDir, "espeak-ng-data").isDirectory

    fun installedBytes(): Long = modelDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun delete() {
        modelDir.deleteRecursively()
        partialDir.deleteRecursively()
    }

    /** [onProgress] gets 0f..1f. Throws on network failure or cancellation. */
    suspend fun download(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        if (isInstalled()) return@withContext
        partialDir.deleteRecursively()
        partialDir.mkdirs()
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            check(connection.responseCode in 200..299) { "Download failed (HTTP ${connection.responseCode})" }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: APPROX_BYTES
            val counted = CountingInputStream(connection.inputStream.buffered(BUFFER))
            BZip2CompressorInputStream(counted).use { bz ->
                TarArchiveInputStream(bz).use { tar ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        coroutineContext.ensureActive()
                        val entry = tar.nextEntry ?: break
                        val relative = entry.name.substringAfter('/', "")
                        if (relative.isEmpty()) continue
                        val target = File(partialDir, relative)
                        require(target.canonicalPath.startsWith(partialDir.canonicalPath + File.separator)) {
                            "Unsafe path in archive"
                        }
                        if (entry.isDirectory) {
                            target.mkdirs()
                            continue
                        }
                        target.parentFile?.mkdirs()
                        target.outputStream().use { out ->
                            while (true) {
                                val read = tar.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                coroutineContext.ensureActive()
                                onProgress((counted.count.toFloat() / total).coerceIn(0f, 0.99f))
                            }
                        }
                    }
                }
            }
            File(partialDir, COMPLETE_MARKER).writeText("ok")
            modelDir.deleteRecursively()
            check(partialDir.renameTo(modelDir)) { "Could not finish installing the voice model" }
            onProgress(1f)
        } catch (t: Throwable) {
            partialDir.deleteRecursively()
            throw t
        } finally {
            connection.disconnect()
        }
    }

    private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
        var count = 0L
            private set

        override fun read(): Int = super.read().also { if (it >= 0) count++ }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) count += it }
    }

    companion object {
        const val MODEL_NAME = "kokoro-multi-lang-v1_0"
        const val MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$MODEL_NAME.tar.bz2"
        const val APPROX_BYTES = 350_000_000L
        private const val COMPLETE_MARKER = ".complete"
        private const val BUFFER = 64 * 1024
    }
}
