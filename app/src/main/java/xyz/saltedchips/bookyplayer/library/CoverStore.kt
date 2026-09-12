package xyz.saltedchips.bookyplayer.library

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.security.MessageDigest

object CoverStore {
    fun save(context: Context, bookId: String, bitmap: Bitmap): String {
        val file = fileFor(context, bookId)
        file.parentFile?.mkdirs()
        file.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        }
        return Uri.fromFile(file).toString()
    }

    fun delete(context: Context, bookId: String) {
        fileFor(context, bookId).delete()
    }

    private fun fileFor(context: Context, bookId: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(bookId.toByteArray())
        val name = digest.take(16).joinToString("") { byte -> "%02x".format(byte) }
        return File(File(context.filesDir, "covers"), "$name.jpg")
    }
}
