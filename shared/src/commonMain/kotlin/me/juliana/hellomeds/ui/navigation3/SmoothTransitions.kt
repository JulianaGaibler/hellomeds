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

// LinearEasing on slide tracks the finger 1:1 during predictive-back drag.
private val slideSpec = tween<IntOffset>(durationMillis = 350, easing = LinearEasing)
private val scaleSpec =
    tween<Float>(durationMillis = 350, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f))

fun smoothScalePopTransition(): ContentTransform {
    val enter =
        slideInHorizontally(slideSpec) { width -> -(width * 0.3f).toInt() } +
            scaleIn(scaleSpec, initialScale = 0.9f) +
            fadeIn(scaleSpec, initialAlpha = 0.3f)

    val exit =
        slideOutHorizontally(slideSpec) { width -> width } +
            scaleOut(scaleSpec, targetScale = 0.9f) +
            fadeOut(scaleSpec, targetAlpha = 0.0f)

    return (enter togetherWith exit).apply {
        targetContentZIndex = -1f
    }
}

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
        targetContentZIndex = 1f
    }
}

// Parallax (background 30% / foreground 100%) under LinearEasing for 1:1 gesture tracking.
private fun gesturePopTransition(): ContentTransform {
    val duration = 350
    val gestureSlideSpec = tween<IntOffset>(durationMillis = duration, easing = LinearEasing)
    val gestureFadeSpec = tween<Float>(durationMillis = duration, easing = LinearEasing)

    val enter =
        slideInHorizontally(gestureSlideSpec) { width -> -(width * 0.3f).toInt() } +
            fadeIn(gestureFadeSpec, initialAlpha = 0.1f)

    val exit =
        slideOutHorizontally(gestureSlideSpec) { width -> width } +
            scaleOut(gestureFadeSpec, targetScale = 0.95f) +
            fadeOut(gestureFadeSpec, targetAlpha = 0.1f)

    return (enter togetherWith exit).apply {
        targetContentZIndex = -1f
    }
}

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
