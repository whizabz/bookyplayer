package xyz.saltedchips.bookyplayer.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import xyz.saltedchips.bookyplayer.data.Audiobook
import java.io.ByteArrayOutputStream

object CoverLoader {
    private val sessionArt = object : LruCache<String, ByteArray>(4) {
        override fun sizeOf(key: String, value: ByteArray): Int = 1
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

    fun sessionArtwork(context: Context, book: Audiobook): ByteArray? {
        val key = "${book.id}|${book.coverUri.orEmpty()}|${book.artworkFileUri.orEmpty()}|${book.coverRes}"
        sessionArt.get(key)?.let { return it }
        val bitmap = load(context, book, sampleSize = 2) ?: return null
        val bytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            stream.toByteArray()
        }
        sessionArt.put(key, bytes)
        return bytes
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
}
