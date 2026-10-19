package com.sledje.agentpro.core.designsystem

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Motion (docs/DESIGN_SYSTEM.md, Motion): Material 3 standard easing, 150ms for state changes and
 * 250ms for navigation, shared-axis X forward/back, fade-through for tabs, crossfade for in-place
 * state changes, no bounce.
 *
 * "Remove animations": Compose scales every animation by the platform's [MotionDurationScale]
 * (on Android, the system animator duration scale), so a scale of 0 makes them instant.
 * [rememberReduceMotion] exposes the same setting for things that aren't animations of a value,
 * such as the skeleton shimmer.
 */
object Motion {
    /** Material 3 "standard" easing. */
    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val SHORT_MILLIS: Int = Tokens.Motion.short
    const val MEDIUM_MILLIS: Int = Tokens.Motion.medium

    /** One skeleton shimmer cycle. */
    const val SHIMMER_MILLIS: Int = 1200

    fun <T> stateChange(): FiniteAnimationSpec<T> = tween(SHORT_MILLIS, easing = StandardEasing)

    fun <T> navigation(): FiniteAnimationSpec<T> = tween(MEDIUM_MILLIS, easing = StandardEasing)

    /** Shared-axis X: forward slides in from the end, back from the start (a tenth of the width). */
    fun sharedAxisXEnter(forward: Boolean): EnterTransition =
        slideInHorizontally(navigation()) { width -> if (forward) width / 10 else -width / 10 } + fadeIn(navigation())

    fun sharedAxisXExit(forward: Boolean): ExitTransition =
        slideOutHorizontally(navigation()) { width -> if (forward) -width / 10 else width / 10 } + fadeOut(navigation())

    /**
     * Fade-through for tab switches and the app gate: the old screen fades out first (100ms), then
     * the new one fades in while growing from 92% (150ms), 250ms in all.
     */
    fun fadeThroughEnter(): EnterTransition =
        fadeIn(tween(SHORT_MILLIS, delayMillis = FADE_THROUGH_OUT_MILLIS, easing = StandardEasing)) +
            scaleIn(tween(SHORT_MILLIS, delayMillis = FADE_THROUGH_OUT_MILLIS, easing = StandardEasing), initialScale = 0.92f)

    fun fadeThroughExit(): ExitTransition = fadeOut(tween(FADE_THROUGH_OUT_MILLIS, easing = StandardEasing))

    fun fadeThrough(): ContentTransform = fadeThroughEnter() togetherWith fadeThroughExit()

    /** Crossfade for content that changes in place: skeleton → content, chart ↔ table. */
    fun crossfade(): ContentTransform = fadeIn(stateChange()) togetherWith fadeOut(stateChange())

    private const val FADE_THROUGH_OUT_MILLIS: Int = MEDIUM_MILLIS - SHORT_MILLIS
}

/** True when the platform's animation scale is 0 ("remove animations"). */
@Composable
fun rememberReduceMotion(): Boolean {
    val scope = rememberCoroutineScope()
    return remember(scope) { (scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f }
}

/** A haptic tick for a successful action. */
@Composable
fun rememberSuccessHaptic(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { { haptics.performHapticFeedback(HapticFeedbackType.Confirm) } }
}
