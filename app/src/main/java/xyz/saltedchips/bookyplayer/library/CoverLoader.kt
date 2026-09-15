package xyz.saltedchips.bookyplayer.library

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.core.content.FileProvider
import xyz.saltedchips.bookyplayer.data.Audiobook
import java.io.ByteArrayOutputStream
import java.io.File

data class SessionArtwork(
    val bytes: ByteArray,
    val contentUri: Uri,
)

object CoverLoader {
    private val sessionArt = object : LruCache<String, SessionArtwork>(4) {
        override fun sizeOf(key: String, value: SessionArtwork): Int = 1
    }

    fun load(context: Context, book: Audiobook, sampleSize: Int = 2): Bitmap? {
        decodeUri(context, book.coverUri, sampleSize)?.let { return it }
        embedded(context, book.artworkFileUri, sampleSize)?.let { return it }
        if (book.coverRes != 0) {
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            return BitmapFactory.decodeResource(context.resources, book.coverRes, options)
        }
        return null
    }

    fun sessionArtwork(context: Context, book: Audiobook): SessionArtwork? {
        val key = "${book.id}|${book.coverUri.orEmpty()}|${book.artworkFileUri.orEmpty()}|${book.coverRes}"
        sessionArt.get(key)?.let { cached ->
            grantArtworkRead(context, cached.contentUri)
            return cached
        }
        val bitmap = load(context, book, sampleSize = 2) ?: return null
        val scaled = downscale(bitmap, SESSION_ART_MAX_EDGE)
        if (scaled != bitmap) bitmap.recycle()
        val bytes = ByteArrayOutputStream().use { stream ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            stream.toByteArray()
        }
        scaled.recycle()
        val file = artworkFile(context, book.id)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.artwork",
            file,
        )
        grantArtworkRead(context, uri)
        val art = SessionArtwork(bytes, uri)
        sessionArt.put(key, art)
        return art
    }

    private fun artworkFile(context: Context, bookId: String): File {
        val dir = File(context.cacheDir, ARTWORK_DIR)
        val safe = bookId.hashCode().toString()
        return File(dir, "$safe.jpg")
    }

    private fun grantArtworkRead(context: Context, uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        ARTWORK_PACKAGES.forEach { pkg ->
            runCatching { context.grantUriPermission(pkg, uri, flags) }
        }
    }

    private fun downscale(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val edge = maxOf(bitmap.width, bitmap.height)
        if (edge <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / edge
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun decodeUri(context: Context, uriString: String?, sampleSize: Int): Bitmap? {
        val uri = uriString?.let(Uri::parse) ?: return null
        return try {
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            if (uri.scheme == "file") {
                BitmapFactory.decodeFile(uri.path, options)
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun embedded(context: Context, uriString: String?, sampleSize: Int): Bitmap? {
        val uri = uriString?.let(Uri::parse) ?: return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture ?: return null
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (_: RuntimeException) {
            null
        } finally {
            retriever.release()
        }
    }

    private const val SESSION_ART_MAX_EDGE = 512
    private const val ARTWORK_DIR = "session_artwork"
    private val ARTWORK_PACKAGES = listOf(
        "com.google.android.projection.gearhead",
        "com.google.android.gms",
        "com.android.bluetooth",
        "com.android.systemui",
    )
}
