package com.booky.app.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun MotionScheme.expressiveStackTransform(forward: Boolean): ContentTransform {
    return if (forward) {
        (
            fadeIn(defaultEffectsSpec()) +
                slideInHorizontally(defaultSpatialSpec()) { it / 8 } +
                scaleIn(fastSpatialSpec(), initialScale = 0.92f)
            ) togetherWith fadeOut(fastEffectsSpec())
    } else {
        fadeIn(defaultEffectsSpec()) togetherWith (
            fadeOut(fastEffectsSpec()) +
                slideOutHorizontally(fastSpatialSpec()) { it / 8 } +
                scaleOut(fastSpatialSpec(), targetScale = 0.92f)
            )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun MotionScheme.expressiveFadeTransform(): ContentTransform {
    return fadeIn(defaultEffectsSpec()) togetherWith fadeOut(fastEffectsSpec())
}
