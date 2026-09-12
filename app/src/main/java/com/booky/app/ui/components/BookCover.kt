package com.booky.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.booky.app.data.Audiobook
import com.booky.app.library.CoverLoader
import com.booky.app.settings.PlaceholderCoverStyle
import com.booky.app.theme.LocalPlaceholderCoverStyle
import com.booky.app.theme.LocalSystemColorScheme
import com.booky.app.theme.placeholderFontFamily
import com.booky.app.theme.placeholderPolygon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class CoverLoad(val ready: Boolean, val bitmap: Bitmap?)

@Composable
fun BookCover(
    book: Audiobook,
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
    square: Boolean = true,
    shape: Shape = RoundedCornerShape(corner),
) {
    val context = LocalContext.current
    val load by produceState(
        initialValue = CoverLoad(ready = false, bitmap = null),
        key1 = book.id,
        key2 = book.coverUri,
        key3 = "${book.artworkFileUri}:${book.coverRes}",
    ) {
        value = CoverLoad(ready = false, bitmap = null)
        value = CoverLoad(
            ready = true,
            bitmap = withContext(Dispatchers.IO) { CoverLoader.load(context, book) },
        )
    }
    Box(
        modifier = modifier
            .then(if (square) Modifier.aspectRatio(1f, matchHeightConstraintsFirst = false) else Modifier)
            .clip(shape)
            .background(LocalSystemColorScheme.current.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val image = load.bitmap
        when {
            image != null -> Image(
                bitmap = image.asImageBitmap(),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            load.ready -> EditorialCover(title = book.title, seed = book.id)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditorialCover(
    title: String,
    seed: String,
    modifier: Modifier = Modifier,
    style: PlaceholderCoverStyle = LocalPlaceholderCoverStyle.current,
    showTitle: Boolean = true,
) {
    val colors = LocalSystemColorScheme.current
    val polygon = placeholderPolygon(style.shapeId, seed)
    val family = remember(style.font, style.weight) {
        placeholderFontFamily(style)
    }
    BoxWithConstraints(modifier.fillMaxSize().background(colors.primaryContainer)) {
        val compact = maxWidth < 56.dp
        val blob = maxWidth * 0.82f
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(blob)
                .clip(polygon.toShape())
                .background(colors.primary),
        )
        if (showTitle) {
            Text(
                text = if (compact) editorialInitials(title) else title,
                color = colors.onPrimary,
                fontFamily = family,
                fontSize = if (compact) 16.sp else (maxWidth.value * 0.13f).coerceIn(14f, 34f).sp,
                lineHeight = if (compact) 18.sp else (maxWidth.value * 0.15f).coerceIn(16f, 38f).sp,
                letterSpacing = if (compact) 0.sp else (-0.6).sp,
                textAlign = TextAlign.Center,
                maxLines = if (compact) 1 else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = if (compact) 4.dp else 10.dp),
            )
        }
    }
}

private fun editorialInitials(title: String): String {
    val parts = title.split(Regex("[\\s—–-]+")).filter { it.isNotBlank() }
    if (parts.isEmpty()) return "?"
    return parts.take(2).joinToString("") { it.first().uppercase() }
}
