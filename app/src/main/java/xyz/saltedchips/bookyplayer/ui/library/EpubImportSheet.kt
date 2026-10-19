package xyz.saltedchips.bookyplayer.ui.library

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import xyz.saltedchips.bookyplayer.tts.VoiceGender
import xyz.saltedchips.bookyplayer.tts.VoiceAccent
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import xyz.saltedchips.bookyplayer.tts.EpubImportUiState
import xyz.saltedchips.bookyplayer.tts.KokoroVoices
import xyz.saltedchips.bookyplayer.ui.components.BookyIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubImportSheet(
    uri: Uri,
    state: EpubImportUiState,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onWarmUp: () -> Unit,
    onSelectVoice: (Int) -> Unit,
    onPreviewVoice: (Int) -> Unit,
    onConvert: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(state.modelInstalled) {
        if (state.modelInstalled) onWarmUp()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
        ) {
            Text("Generate an audiobook", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Your ePub is read aloud on this phone. Nothing is uploaded.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            if (!state.modelInstalled) {
                VoiceModelStep(state, onDownload, onCancelDownload)
            } else {
                VoicePicker(state, onSelectVoice, onPreviewVoice)
                Text(
                    "Conversion runs in the background and can take a few hours for a long book. " +
                        "You can start listening once the first chapter is ready.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            state.importError?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = { onConvert(uri) },
                    enabled = state.modelInstalled && !state.importing,
                    modifier = Modifier.weight(1f),
                ) { Text(if (state.importing) "Reading…" else "Convert") }
            }
        }
    }
}

@Composable
private fun VoiceModelStep(
    state: EpubImportUiState,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    Text("Download the speech model first", style = MaterialTheme.typography.titleSmall)
    Text(
        "A one-time download of about 350 MB (Kokoro, Apache-2.0). Wi-Fi is recommended. " +
            "It runs in the background, so you can close this and keep reading. " +
            "We'll notify you when it's ready.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
    )
    val progress = state.downloadProgress
    if (progress == null) {
        Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.downloadError != null) "Try again" else "Download in background")
        }
        state.downloadError?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    } else {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancelDownload) { Text("Cancel download") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoicePicker(
    state: EpubImportUiState,
    onSelectVoice: (Int) -> Unit,
    onPreviewVoice: (Int) -> Unit,
) {
    val selected = KokoroVoices.bySid(state.voiceSid)
    var accent by remember { mutableStateOf(selected.accent) }
    var gender by remember { mutableStateOf(selected.gender) }
    val scheme = MaterialTheme.colorScheme
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = scheme.secondaryContainer,
        labelColor = scheme.onSecondaryContainer,
        selectedContainerColor = scheme.primary,
        selectedLabelColor = scheme.onPrimary,
    )

    Text("Voice", style = MaterialTheme.typography.titleSmall)
    Text(
        "Selected: ${selected.label}",
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VoiceAccent.entries.forEach { option ->
            FilterChip(
                modifier = Modifier.height(40.dp),
                selected = accent == option,
                onClick = { accent = option },
                label = { Text(option.label) },
                shape = RoundedCornerShape(12.dp),
                border = null,
                colors = chipColors,
            )
        }
        Spacer(Modifier.width(8.dp))
        VoiceGender.entries.forEach { option ->
            FilterChip(
                modifier = Modifier.height(40.dp),
                selected = gender == option,
                onClick = { gender = option },
                label = { Text(option.label) },
                shape = RoundedCornerShape(12.dp),
                border = null,
                colors = chipColors,
            )
        }
    }
    val voices = KokoroVoices.english.filter { it.accent == accent && it.gender == gender }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .padding(top = 8.dp),
    ) {
        items(voices, key = { it.sid }) { voice ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = state.voiceSid == voice.sid,
                        onClick = { onSelectVoice(voice.sid) },
                        role = Role.RadioButton,
                    )
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = state.voiceSid == voice.sid, onClick = null)
                Text(
                    voice.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
                IconButton(
                    onClick = { onPreviewVoice(voice.sid) },
                    enabled = state.previewingSid == null,
                ) {
                    if (state.previewingSid == voice.sid) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            BookyIcons.play,
                            contentDescription = "Preview ${voice.label}",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}
