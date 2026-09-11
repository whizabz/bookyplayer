package com.booky.app.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object CoverImages {
    private const val MaxEditSide = 2048
    private const val OutputSide = 1024

    fun loadForCrop(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        val sample = Integer.highestOneBit((longest / MaxEditSide).coerceAtLeast(1))
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null
        val orientation = resolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
        return applyExif(decoded, orientation)
    }

    fun cropSquare(
        source: Bitmap,
        userScale: Float,
        offsetX: Float,
        offsetY: Float,
        viewSize: Float,
    ): Bitmap {
        val crop = viewSize.coerceAtLeast(1f)
        val minDim = min(source.width, source.height).toFloat().coerceAtLeast(1f)
        val totalScale = (crop / minDim) * userScale.coerceAtLeast(1f)
        val drawnW = source.width * totalScale
        val drawnH = source.height * totalScale
        val imageLeft = (crop - drawnW) / 2f + offsetX
        val imageTop = (crop - drawnH) / 2f + offsetY
        val srcX = ((0f - imageLeft) / totalScale).roundToInt().coerceIn(0, source.width - 1)
        val srcY = ((0f - imageTop) / totalScale).roundToInt().coerceIn(0, source.height - 1)
        val srcSize = (crop / totalScale).roundToInt()
            .coerceAtLeast(1)
            .coerceAtMost(min(source.width - srcX, source.height - srcY))
        val square = Bitmap.createBitmap(source, srcX, srcY, srcSize, srcSize)
        val output = srcSize.coerceAtMost(OutputSide)
        if (square.width == output) return square
        val scaled = Bitmap.createScaledBitmap(square, output, output, true)
        if (scaled != square) square.recycle()
        return scaled
    }

    private fun applyExif(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }
}
