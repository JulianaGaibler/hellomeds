// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.navigation3

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent

// 1. LINEAR SPEC (For Sliding)
// Allows the slide to track the finger exactly 1:1 without stopping.
private val slideSpec = tween<IntOffset>(durationMillis = 350, easing = LinearEasing)

// 2. SMOOTH CURVE (For Scale/Fade)
// A standard ease-out. Makes the scaling feel natural but fully completes by the end.
private val scaleSpec =
    tween<Float>(durationMillis = 350, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f))

/**
 * UNIFIED POP TRANSITION
 * Used for BOTH 'predictivePopTransitionSpec' (Drag) and 'popTransitionSpec' (Commit).
 *
 * Behavior:
 * - Entering (Background): Comes from Left (-30%), Scales Up (0.9 -> 1.0), Fades In.
 * - Exiting (Foreground): Goes to Right (100%), Scales Down (1.0 -> 0.9), Fades Out.
 */
fun smoothScalePopTransition(): ContentTransform {
    val enter =
        // Slide: Moves from -30% to 0%
        slideInHorizontally(slideSpec) { width -> -(width * 0.3f).toInt() } +
            // Scale: Grows from 90% to 100%
            scaleIn(scaleSpec, initialScale = 0.9f) +
            // Fade: From Scrim (0.3) to Bright (1.0)
            fadeIn(scaleSpec, initialAlpha = 0.3f)

    val exit =
        // Slide: Moves from 0% to 100% (Follows finger completely)
        slideOutHorizontally(slideSpec) { width -> width } +
            // Scale: Shrinks from 1.0 to 0.9
            scaleOut(scaleSpec, targetScale = 0.9f) +
            // Fade: Fades out to transparent (User request)
            fadeOut(scaleSpec, targetAlpha = 0.0f)

    return (enter togetherWith exit).apply {
        // Essential: Background stays behind Foreground
        targetContentZIndex = -1f
    }
}

/**
 * PUSH TRANSITION (Reverse of Pop)
 *
 * Behavior:
 * - Entering (New): Slides from Right, Scales Up, Fades In.
 * - Exiting (Old): Slides to Left (-30%), Scales Down, Fades Out.
 */
fun smoothScalePushTransition(): ContentTransform {
    val enter =
        slideInHorizontally(slideSpec) { width -> width } +
            scaleIn(scaleSpec, initialScale = 0.9f) +
            fadeIn(scaleSpec, initialAlpha = 0.0f)

    val exit =
        slideOutHorizontally(slideSpec) { width -> -(width * 0.3f).toInt() } +
            scaleOut(scaleSpec, targetScale = 0.9f) +
            fadeOut(scaleSpec, targetAlpha = 0.3f)

    return (enter togetherWith exit).apply {
        // Essential: New screen stays on top
        targetContentZIndex = 1f
    }
}

// Gesture-optimized transition: parallax slide + scrim-like fade, all LinearEasing.
// LinearEasing maps animation progress 1:1 to gesture progress for direct finger tracking.
// Parallax (background at 30% speed vs foreground at 100%) creates depth.
// Fade from/to dim simulates a scrim overlay between layers.
private fun gesturePopTransition(): ContentTransform {
    val duration = 350
    val gestureSlideSpec = tween<IntOffset>(durationMillis = duration, easing = LinearEasing)
    val gestureFadeSpec = tween<Float>(durationMillis = duration, easing = LinearEasing)

    // Background (previous screen): parallax from -30%, dimmed -> bright
    val enter =
        slideInHorizontally(gestureSlideSpec) { width -> -(width * 0.3f).toInt() } +
            fadeIn(gestureFadeSpec, initialAlpha = 0.1f)

    // Foreground (current screen): slides right 1:1, shrinks slightly, fades to dim
    val exit =
        slideOutHorizontally(gestureSlideSpec) { width -> width } +
            scaleOut(gestureFadeSpec, targetScale = 0.95f) +
            fadeOut(gestureFadeSpec, targetAlpha = 0.1f)

    return (enter togetherWith exit).apply {
        targetContentZIndex = -1f
    }
}

// NavDisplay-compatible transition spec lambdas

val pushTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
    smoothScalePushTransition()
}

val popTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
    smoothScalePopTransition()
}

val predictivePopTransitionSpec:
    AnimatedContentTransitionScope<Scene<NavKey>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform =
    {
        gesturePopTransition()
    }
