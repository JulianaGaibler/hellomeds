// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.screenshots

import androidx.test.platform.app.InstrumentationRegistry

/**
 * Drives Android's built-in `sysui demo mode` so the status bar shows
 * a deterministic clock, full battery, and full signal for screenshots.
 *
 * Requires `adb shell settings put global sysui_demo_allowed 1` (done by
 * `./hm screenshots` before launching the test). Silently no-ops on
 * locked-down OEM images.
 */
object SysUiDemoMode {
    fun enable() {
        shell("am broadcast -a com.android.systemui.demo -e command enter")
        shell("am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1000")
        shell("am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false")
        shell("am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4")
        shell("am broadcast -a com.android.systemui.demo -e command network -e mobile show -e datatype none -e level 4")
        shell("am broadcast -a com.android.systemui.demo -e command notifications -e visible false")
    }

    fun disable() {
        shell("am broadcast -a com.android.systemui.demo -e command exit")
    }

    private fun shell(cmd: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd).close()
    }
}
