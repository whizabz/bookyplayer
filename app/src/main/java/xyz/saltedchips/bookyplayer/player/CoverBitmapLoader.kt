package xyz.saltedchips.bookyplayer.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.io.IOException
import java.util.concurrent.Executors

@UnstableApi
internal class CoverBitmapLoader(context: Context) : BitmapLoader {
    private val appContext = context.applicationContext
    private val executor: ListeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String): Boolean {
        return mimeType.startsWith("image/")
    }

    override fun decodeBitmap(data: ByteArray) = executor.submit<Bitmap> {
        BitmapFactory.decodeByteArray(data, 0, data.size)
            ?: throw IOException("Could not decode artwork")
    }

    override fun loadBitmap(uri: Uri) = executor.submit<Bitmap> {
        appContext.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: throw IOException("Could not load artwork from $uri")
    }
}
