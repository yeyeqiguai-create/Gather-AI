package com.tim.futureai.ui.liquid

import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween

/** One motion language for navigation; exits finish before the new page settles. */
fun <S> AnimatedContentTransitionScope<S>.gatherPageTransition(forward: Boolean, reducedMotion: Boolean): ContentTransform {
    if (reducedMotion) return EnterTransition.None togetherWith ExitTransition.None
    val ease = CubicBezierEasing(0.22f, 0.72f, 0f, 1f)
    val direction = if (forward) 1 else -1
    return ((fadeIn(tween(220, 30, ease)) +
        slideInHorizontally(tween(300, easing = ease)) { direction * it / 12 }) togetherWith
        (fadeOut(tween(140)) + slideOutHorizontally(tween(240, easing = ease)) { -direction * it / 24 }))
        .using(SizeTransform(clip = false))
}
