package xyz.saltedchips.bookyplayer.ui.library

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import xyz.saltedchips.bookyplayer.library.CoverImages
import kotlin.math.min

@Composable
fun SquareCoverCropDialog(
    bitmap: Bitmap,
    onConfirm: (Bitmap) -> Unit,
    onDismiss: () -> Unit,
) {
    var userScale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewSizePx by remember { mutableFloatStateOf(0f) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Text("Crop cover", style = MaterialTheme.typography.titleMedium)
                    TextButton(
                        onClick = {
                            val cropped = CoverImages.cropSquare(
                                source = bitmap,
                                userScale = userScale,
                                offsetX = offset.x,
                                offsetY = offset.y,
                                viewSize = viewSizePx,
                            )
                            onConfirm(cropped)
                        },
                        enabled = viewSizePx > 0f,
                    ) { Text("Done") }
                }
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val cropDp = min(constraints.maxWidth, constraints.maxHeight)
                        .let { with(LocalDensity.current) { it.toDp() } }
                        .coerceAtMost(360.dp)
                    SquareCropViewport(
                        bitmap = bitmap,
                        userScale = userScale,
                        offset = offset,
                        onTransform = { zoom, pan ->
                            val nextScale = (userScale * zoom).coerceIn(1f, 8f)
                            val nextOffset = offset + pan
                            val crop = viewSizePx.coerceAtLeast(1f)
                            userScale = nextScale
                            offset = clampedOffset(bitmap, nextScale, nextOffset, crop)
                        },
                        onSize = { viewSizePx = it },
                        modifier = Modifier.size(cropDp),
                    )
                }
                Text(
                    "Drag to reposition. Pinch to zoom. The cover is always square.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SquareCropViewport(
    bitmap: Bitmap,
    userScale: Float,
    offset: Offset,
    onTransform: (zoom: Float, pan: Offset) -> Unit,
    onSize: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val onTransformState = rememberUpdatedState(onTransform)
    var size by remember { mutableStateOf(IntSize.Zero) }
    val minDim = min(bitmap.width, bitmap.height).toFloat().coerceAtLeast(1f)
    val cropPx = size.width.toFloat().coerceAtLeast(1f)
    val baseScale = cropPx / minDim
    Box(
        modifier = modifier
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(16.dp))
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .onSizeChanged {
                size = it
                onSize(it.width.toFloat())
            }
            .pointerInput(bitmap) {
                detectTransformGestures { _, pan, zoom, _ ->
                    onTransformState.value(zoom, pan)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .size(
                    width = with(density) { (bitmap.width * baseScale).toDp() },
                    height = with(density) { (bitmap.height * baseScale).toDp() },
                )
                .graphicsLayer {
                    scaleX = userScale
                    scaleY = userScale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

private fun clampedOffset(
    bitmap: Bitmap,
    userScale: Float,
    offset: Offset,
    cropPx: Float,
): Offset {
    val minDim = min(bitmap.width, bitmap.height).toFloat().coerceAtLeast(1f)
    val drawScale = (cropPx / minDim) * userScale.coerceAtLeast(1f)
    val maxX = ((bitmap.width * drawScale - cropPx) / 2f).coerceAtLeast(0f)
    val maxY = ((bitmap.height * drawScale - cropPx) / 2f).coerceAtLeast(0f)
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}
