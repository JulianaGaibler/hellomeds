// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

actual class PlatformNavigator {

    /**
     * iOS has no equivalent of `SCHEDULE_EXACT_ALARM` — the exact-alarm banner
     * never fires on this platform (the Android-only flag stays false). This
     * method is provided for symmetry; if it ever fires on iOS (e.g. test
     * code), open the app's main system settings page so the action does
     * something useful instead of silently no-op'ing.
     */
    actual fun openExactAlarmSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url)
    }
}
