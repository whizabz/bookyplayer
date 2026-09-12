package com.booky.app.ui.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.booky.app.player.ChapterMarksSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SwipeableChapterRow(
    index: Int,
    title: String,
    durationMs: Long,
    progress: Float,
    complete: Boolean,
    selected: Boolean,
    showDivider: Boolean,
    revealed: Boolean,
    swipeEnabled: Boolean,
    selecting: Boolean = false,
    checked: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onRevealedChange: (Boolean) -> Unit,
    onMarkPlayed: () -> Unit,
    onMarkUnplayed: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val actionWidth = 56.dp
    SwipeToRevealActions(
        revealed = revealed,
        onRevealedChange = onRevealedChange,
        enabled = swipeEnabled && !selecting,
        fromStart = true,
        shape = RoundedCornerShape(16.dp),
        actionWidth = actionWidth,
        actionCount = 1,
        onCommit = {
            if (complete) onMarkUnplayed() else onMarkPlayed()
        },
        actions = {
            FilledTonalIconButton(
                onClick = {
                    onRevealedChange(false)
                    if (complete) onMarkUnplayed() else onMarkPlayed()
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (complete) {
                        colors.secondaryContainer
                    } else {
                        colors.primaryContainer
                    },
                    contentColor = if (complete) {
                        colors.onSecondaryContainer
                    } else {
                        colors.onPrimaryContainer
                    },
                ),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(actionWidth),
            ) {
                Icon(
                    if (complete) BookyIcons.restartAlt else BookyIcons.check,
                    contentDescription = if (complete) "Mark as unplayed" else "Mark as played",
                )
            }
        },
    ) {
        ChapterRow(
            index = index,
            title = title,
            durationMs = durationMs,
            progress = progress,
            complete = complete,
            selected = selected,
            showDivider = showDivider,
            selecting = selecting,
            checked = checked,
            onClick = {
                if (revealed) onRevealedChange(false) else onClick()
            },
            onLongClick = onLongClick,
        )
    }
}

@Composable
fun MarkPreviousChaptersDialog(
    previousCount: Int,
    onMarkPrevious: () -> Unit,
    onOnlyThese: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mark previous chapters?") },
        text = {
            Text(
                if (previousCount == 1) {
                    "Also mark the previous chapter as played?"
                } else {
                    "Also mark the previous $previousCount chapters as played?"
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onMarkPrevious) { Text("Mark previous too") }
        },
        dismissButton = {
            TextButton(onClick = onOnlyThese) { Text("Only this chapter") }
        },
    )
}

fun CoroutineScope.showBulkChapterSnackbar(
    host: SnackbarHostState,
    count: Int,
    played: Boolean,
    snapshot: ChapterMarksSnapshot,
    restore: (ChapterMarksSnapshot) -> Unit,
) {
    launch {
        val message = if (played) {
            if (count == 1) "1 chapter marked as played" else "$count chapters marked as played"
        } else {
            if (count == 1) "1 chapter marked as unplayed" else "$count chapters marked as unplayed"
        }
        val dismiss = launch {
            delay(5_000)
            host.currentSnackbarData?.dismiss()
        }
        val result = host.showSnackbar(
            message = message,
            actionLabel = "Undo",
            withDismissAction = true,
            duration = SnackbarDuration.Indefinite,
        )
        dismiss.cancel()
        if (result == SnackbarResult.ActionPerformed) {
            restore(snapshot)
        }
    }
}
