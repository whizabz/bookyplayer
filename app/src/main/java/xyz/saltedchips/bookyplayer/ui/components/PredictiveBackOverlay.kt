package xyz.saltedchips.bookyplayer.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PredictiveBackOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    interceptBack: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var held by remember { mutableStateOf(visible) }
    if (visible) held = true
    if (!held) return

    val transitionState = remember { SeekableTransitionState(false) }
    var seeking by remember { mutableStateOf(false) }
    val transition = rememberTransition(transitionState, label = "predictive-overlay")
    val motion = MaterialTheme.motionScheme

    LaunchedEffect(visible) {
        if (seeking) return@LaunchedEffect
        if (visible) {
            if (transitionState.currentState != true) {
                transitionState.animateTo(true)
            }
        } else {
            if (transitionState.currentState != false) {
                transitionState.animateTo(false)
            }
            held = false
        }
    }

    PredictiveBackHandler(enabled = interceptBack && visible) { events ->
        seeking = true
        try {
            events.collect { event ->
                transitionState.seekTo(event.progress.coerceIn(0f, 1f), false)
            }
            transitionState.snapTo(false)
            held = false
            seeking = false
            onDismiss()
        } catch (e: CancellationException) {
            transitionState.animateTo(true)
            seeking = false
            throw e
        }
    }

    transition.AnimatedVisibility(
        visible = { it },
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(motion.defaultEffectsSpec()) +
            slideInHorizontally(motion.defaultSpatialSpec()) { it / 8 } +
            scaleIn(motion.defaultSpatialSpec(), initialScale = 0.92f),
        exit = fadeOut(tween(durationMillis = 220, easing = LinearEasing), targetAlpha = 0.92f) +
            scaleOut(tween(durationMillis = 220, easing = LinearEasing), targetScale = 0.9f) +
            slideOutHorizontally(tween(durationMillis = 220, easing = LinearEasing)) { it / 14 },
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(Modifier.fillMaxSize()) {
                content()
            }
        }
    }
}
