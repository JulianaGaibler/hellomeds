// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.flavor

import android.app.Activity

/**
 * F-Droid flavor: no camera detection feature.
 * ML Kit and CameraX are not included in this build variant.
 */
object CameraFeature {
    val isAvailable: Boolean = false

    fun getCameraScreen(): Nothing? = null

    fun registerMlStatusChecker(activity: Activity) {
        // No-op: ML Kit not available in F-Droid flavor
    }
}
