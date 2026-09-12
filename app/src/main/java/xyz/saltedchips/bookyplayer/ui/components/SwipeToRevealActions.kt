package xyz.saltedchips.bookyplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private enum class RevealValue { Closed, Open, Commit }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SwipeToRevealActions(
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    enabled: Boolean,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    actionWidth: Dp = 56.dp,
    actionCount: Int = 3,
    fromStart: Boolean = false,
    onCommit: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val gap = ButtonGroupDefaults.ConnectedSpaceBetween
    val revealPx = remember(density, actionWidth, actionCount, gap) {
        with(density) { (actionWidth * actionCount + gap * (actionCount - 1)).toPx() }
    }
    val revealedAt = if (fromStart) revealPx else -revealPx
    val commitAt = if (onCommit != null) {
        revealedAt * 2.6f
    } else {
        null
    }
    val state = remember {
        AnchoredDraggableState(
            initialValue = if (revealed) RevealValue.Open else RevealValue.Closed,
            DraggableAnchors {
                RevealValue.Closed at 0f
                RevealValue.Open at revealedAt
                if (commitAt != null) RevealValue.Commit at commitAt
            },
        )
    }
    LaunchedEffect(revealPx, fromStart, commitAt) {
        state.updateAnchors(
            DraggableAnchors {
                RevealValue.Closed at 0f
                RevealValue.Open at revealedAt
                if (commitAt != null) RevealValue.Commit at commitAt
            },
        )
    }
    LaunchedEffect(revealed) {
        val target = if (revealed) RevealValue.Open else RevealValue.Closed
        if (state.currentValue != target && state.currentValue != RevealValue.Commit) {
            state.animateTo(target)
        }
    }
    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            RevealValue.Closed -> if (revealed) onRevealedChange(false)
            RevealValue.Open -> if (!revealed) onRevealedChange(true)
            RevealValue.Commit -> {
                state.snapTo(RevealValue.Closed)
                onRevealedChange(false)
                onCommit?.invoke()
            }
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape),
    ) {
        Row(
            modifier = Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.spacedBy(
                gap,
                if (fromStart) Alignment.Start else Alignment.End,
            ),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
        Box(
            modifier = Modifier
                .offset {
                    val x = runCatching { state.requireOffset() }.getOrDefault(0f)
                    IntOffset(x.roundToInt(), 0)
                }
                .anchoredDraggable(
                    state = state,
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                )
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
            content = content,
        )
    }
}
