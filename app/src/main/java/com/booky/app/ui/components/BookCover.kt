package com.booky.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.booky.app.data.Audiobook
import com.booky.app.library.CoverLoader
import com.booky.app.theme.BookyCoverOuter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BookCover(
    book: Audiobook,
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
    square: Boolean = true,
    shape: Shape = RoundedCornerShape(corner),
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = book.id,
        key2 = book.coverUri,
        key3 = "${book.artworkFileUri}:${book.coverRes}",
    ) {
        value = withContext(Dispatchers.IO) { CoverLoader.load(context, book) }
    }
    Box(
        modifier = modifier
            .then(if (square) Modifier.aspectRatio(1f, matchHeightConstraintsFirst = false) else Modifier)
            .clip(shape)
            .background(BookyCoverOuter),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = book.title.take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
