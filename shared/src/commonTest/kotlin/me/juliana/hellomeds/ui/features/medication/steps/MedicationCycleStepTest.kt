// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.medication.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import me.juliana.hellomeds.data.model.enums.TimeZoneMode
import me.juliana.hellomeds.ui.test.TestTags
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MedicationCycleStepTest {

    // --- Timezone toggle tests ---

    @Test
    fun timezoneToggle_defaultsToLocal_showsLocalDescription() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MedicationCycleStep(
                    timeZoneMode = TimeZoneMode.LOCAL,
                    onTimeZoneModeChange = {},
                    anchorTimeZone = null,
                    onAnchorTimeZoneChange = {},
                    cycleEnabled = false,
                    cycleDaysActive = 21,
                    cycleDaysBreak = 7,
                    cycleHasPlacebos = false,
                    cycleDayInCycle = 1,
                    onCycleEnabledChange = {},
                    onDaysActiveChange = {},
                    onDaysBreakChange = {},
                    onHasPlacebosChange = {},
                    onDayInCycleChange = {},
                )
            }
        }

        onNodeWithTag(TestTags.TIMEZONE_TOGGLE).assertIsDisplayed()
        // LOCAL mode shows the local description
        onNodeWithText("Adjusts to your current timezone").assertIsDisplayed()
    }

    @Test
    fun timezoneToggle_enableFixed_callsCallback() = runComposeUiTest {
        var capturedMode: TimeZoneMode? = null

        setContent {
            MaterialTheme {
                MedicationCycleStep(
                    timeZoneMode = TimeZoneMode.LOCAL,
                    onTimeZoneModeChange = { capturedMode = it },
                    anchorTimeZone = null,
                    onAnchorTimeZoneChange = {},
                    cycleEnabled = false,
                    cycleDaysActive = 21,
                    cycleDaysBreak = 7,
                    cycleHasPlacebos = false,
                    cycleDayInCycle = 1,
                    onCycleEnabledChange = {},
                    onDaysActiveChange = {},
                    onDaysBreakChange = {},
                    onHasPlacebosChange = {},
                    onDayInCycleChange = {},
                )
            }
        }

        onNodeWithTag(TestTags.TIMEZONE_TOGGLE).performClick()
        assertEquals(TimeZoneMode.FIXED, capturedMode)
    }

    @Test
    fun timezoneToggle_fixedMode_showsFixedDescription() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MedicationCycleStep(
                    timeZoneMode = TimeZoneMode.FIXED,
                    onTimeZoneModeChange = {},
                    anchorTimeZone = null,
                    onAnchorTimeZoneChange = {},
                    cycleEnabled = false,
                    cycleDaysActive = 21,
                    cycleDaysBreak = 7,
                    cycleHasPlacebos = false,
                    cycleDayInCycle = 1,
                    onCycleEnabledChange = {},
                    onDaysActiveChange = {},
                    onDaysBreakChange = {},
                    onHasPlacebosChange = {},
                    onDayInCycleChange = {},
                )
            }
        }

        onNodeWithText("Keeps exact timing when you travel").assertIsDisplayed()
        // Hint card should be visible
        onNodeWithText("Recommended for birth control or antibiotics where exact intervals matter")
            .assertIsDisplayed()
    }

    @Test
    fun timezoneToggle_localMode_hidesHint() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MedicationCycleStep(
                    timeZoneMode = TimeZoneMode.LOCAL,
                    onTimeZoneModeChange = {},
                    anchorTimeZone = null,
                    onAnchorTimeZoneChange = {},
                    cycleEnabled = false,
                    cycleDaysActive = 21,
                    cycleDaysBreak = 7,
                    cycleHasPlacebos = false,
                    cycleDayInCycle = 1,
                    onCycleEnabledChange = {},
                    onDaysActiveChange = {},
                    onDaysBreakChange = {},
                    onHasPlacebosChange = {},
                    onDayInCycleChange = {},
                )
            }
        }

        onNodeWithText("Recommended for birth control or antibiotics where exact intervals matter")
            .assertDoesNotExist()
    }

    // --- Cycle toggle tests ---

    @Test
    fun cycleToggle_defaultsToDisabled_configHidden() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MedicationCycleStep(
                    timeZoneMode = TimeZoneMode.LOCAL,
                    onTimeZoneModeChange = {},
                    anchorTimeZone = null,
                    onAnchorTimeZoneChange = {},
                    cycleEnabled = false,
                    cycleDaysActive = 21,
                    cycleDaysBreak = 7,
                    cycleHasPlacebos = false,
                    cycleDayInCycle = 1,
                    onCycleEnabledChange = {},
                    onDaysActiveChange = {},
                    onDaysBreakChange = {},
                    onHasPlacebosChange = {},
                    onDayInCycleChange = {},
                )
            }
        }

        onNodeWithTag(TestTags.CYCLE_TOGGLE).assertIsDisplayed()
        onNodeWithTag(TestTags.CYCLE_CONFIG).assertDoesNotExist()
    }

    @Test
    fun cycleToggle_enabled_showsConfigFields() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MedicationCycleStep(
                    timeZoneMode = TimeZoneMode.LOCAL,
                    onTimeZoneModeChange = {},
                    anchorTimeZone = null,
                    onAnchorTimeZoneChange = {},
                    cycleEnabled = true,
                    cycleDaysActive = 21,
                    cycleDaysBreak = 7,
                    cycleHasPlacebos = false,
                    cycleDayInCycle = 1,
                    onCycleEnabledChange = {},
                    onDaysActiveChange = {},
                    onDaysBreakChange = {},
                    onHasPlacebosChange = {},
                    onDayInCycleChange = {},
                )
            }
        }

        onNodeWithTag(TestTags.CYCLE_CONFIG).assertIsDisplayed()
    }

    @Test
    fun cycleEnabled_placeboToggle_visibleWhenBreakDaysPositive() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MedicationCycleStep(
                    timeZoneMode = TimeZoneMode.LOCAL,
                    onTimeZoneModeChange = {},
                    anchorTimeZone = null,
                    onAnchorTimeZoneChange = {},
                    cycleEnabled = true,
                    cycleDaysActive = 21,
                    cycleDaysBreak = 7,
                    cycleHasPlacebos = false,
                    cycleDayInCycle = 1,
                    onCycleEnabledChange = {},
                    onDaysActiveChange = {},
                    onDaysBreakChange = {},
                    onHasPlacebosChange = {},
                    onDayInCycleChange = {},
                )
            }
        }

        // With break days > 0, the placebo toggle should exist
        onNodeWithText("Pack includes placebo pills during break").assertIsDisplayed()
    }

    @Test
    fun cycleEnabled_placeboToggle_hiddenWhenNoBreakDays() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MedicationCycleStep(
                    timeZoneMode = TimeZoneMode.LOCAL,
                    onTimeZoneModeChange = {},
                    anchorTimeZone = null,
                    onAnchorTimeZoneChange = {},
                    cycleEnabled = true,
                    cycleDaysActive = 28,
                    cycleDaysBreak = 0,
                    cycleHasPlacebos = false,
                    cycleDayInCycle = 1,
                    onCycleEnabledChange = {},
                    onDaysActiveChange = {},
                    onDaysBreakChange = {},
                    onHasPlacebosChange = {},
                    onDayInCycleChange = {},
                )
            }
        }

        onNodeWithText("Pack includes placebo pills during break").assertDoesNotExist()
    }
}
