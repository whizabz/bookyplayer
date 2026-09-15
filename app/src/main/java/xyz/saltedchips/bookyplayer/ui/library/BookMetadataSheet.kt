package xyz.saltedchips.bookyplayer.ui.library

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import xyz.saltedchips.bookyplayer.data.Audiobook
import xyz.saltedchips.bookyplayer.data.formatDuration
import xyz.saltedchips.bookyplayer.data.sourceLabel
import xyz.saltedchips.bookyplayer.library.CoverImages
import xyz.saltedchips.bookyplayer.ui.components.BookCover
import xyz.saltedchips.bookyplayer.ui.components.SheetHeader
import xyz.saltedchips.bookyplayer.ui.components.SheetHeaderToContentPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookMetadataSheet(
    book: Audiobook,
    onSave: (title: String, author: String, narrator: String, chapterTitles: List<String>, cover: Bitmap?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var title by remember(book.id) { mutableStateOf(book.title) }
    var author by remember(book.id) { mutableStateOf(book.author) }
    var narrator by remember(book.id) { mutableStateOf(book.narrator) }
    var pendingCover by remember(book.id) { mutableStateOf<Bitmap?>(null) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        cropUri = uri
    }
    LaunchedEffect(cropUri) {
        val uri = cropUri
        if (uri == null) {
            cropBitmap = null
            return@LaunchedEffect
        }
        cropBitmap = withContext(Dispatchers.IO) { CoverImages.loadForCrop(context, uri) }
        if (cropBitmap == null) cropUri = null
    }
    cropBitmap?.let { source ->
        SquareCoverCropDialog(
            bitmap = source,
            onConfirm = { cropped ->
                pendingCover = cropped
                cropBitmap = null
                cropUri = null
            },
            onDismiss = {
                cropBitmap = null
                cropUri = null
            },
        )
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(
                title = "Edit book",
                action = {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                },
            )
            Row(
                modifier = Modifier.padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = SheetHeaderToContentPadding,
                    bottom = 8.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val openPicker = {
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }
                val coverModifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = openPicker)
                val preview = pendingCover
                if (preview != null) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = "Book cover",
                        contentScale = ContentScale.Crop,
                        modifier = coverModifier,
                    )
                } else {
                    BookCover(book, coverModifier, corner = 12.dp)
                }
                Column(Modifier.padding(start = 16.dp)) {
                    Text(
                        book.sourceLabel(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatDuration(book.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    TextButton(
                        onClick = openPicker,
                        modifier = Modifier.padding(start = 0.dp),
                    ) {
                        Text("Change cover")
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text("Author") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = narrator,
                onValueChange = { narrator = it },
                label = { Text("Narrator") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = {
                    onSave(title, author, narrator, book.chapterTitles, pendingCover)
                    onDismiss()
                },
                enabled = title.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text("Save")
            }
            }
        }
    }
}
