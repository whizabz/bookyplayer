package xyz.saltedchips.bookyplayer.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme

private const val PredictivePopMillis = 220

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun MotionScheme.overlayEnter(): EnterTransition {
    return fadeIn(defaultEffectsSpec()) +
        slideInHorizontally(defaultSpatialSpec()) { it / 8 } +
        scaleIn(defaultSpatialSpec(), initialScale = 0.92f)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun MotionScheme.overlayExit(): ExitTransition {
    return fadeOut(
        tween(durationMillis = PredictivePopMillis, easing = LinearEasing),
        targetAlpha = 0.92f,
    ) + scaleOut(
        tween(durationMillis = PredictivePopMillis, easing = LinearEasing),
        targetScale = 0.9f,
    ) + slideOutHorizontally(
        tween(durationMillis = PredictivePopMillis, easing = LinearEasing),
    ) { it / 14 }
}
