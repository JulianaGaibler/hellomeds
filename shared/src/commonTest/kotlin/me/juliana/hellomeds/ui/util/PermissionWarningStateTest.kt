// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionWarningStateTest {

    @Test
    fun notificationsDisabled_producesHighPriorityWarning() {
        // Consequence of failure: User has notifications disabled but the app shows no
        // warning anywhere. They unknowingly miss ALL medication alerts.
        val state = PermissionWarningState(notificationsEnabled = false)

        assertTrue(state.hasWarnings, "Should have warnings when notifications disabled")
        assertEquals(
            PermissionWarning.NOTIFICATIONS_DISABLED,
            state.warnings.first(),
            "NOTIFICATIONS_DISABLED should be the highest priority warning",
        )
    }

    @Test
    fun allPermissionsGranted_noWarnings() {
        // Consequence of failure: False positive warnings annoy the user.
        val state = PermissionWarningState(
            notificationsEnabled = true,
            exactAlarmsEnabled = true,
            fullScreenIntentEnabled = true,
            criticalChannelBypassesDnd = true,
            alarmKitEnabled = true,
            criticalAlertsEnabled = true,
        )

        assertFalse(state.hasWarnings, "No warnings when all permissions granted")
        assertTrue(state.warnings.isEmpty())
    }

    @Test
    fun criticalChannelDndBlocked_warningPresent() {
        // Consequence of failure: Critical medication notifications silenced by DND
        // with no user-visible warning. User misses critical dose.
        val state = PermissionWarningState(criticalChannelBypassesDnd = false)

        assertTrue(state.hasWarnings)
        assertTrue(
            state.warnings.contains(PermissionWarning.CRITICAL_CHANNEL_DND_BLOCKED),
            "Should warn about DND blocking critical notifications",
        )
    }

    @Test
    fun multipleWarnings_orderedByPriority() {
        // Consequence of failure: Lower-priority warning shown first, user doesn't see
        // the most critical issue.
        val state = PermissionWarningState(
            notificationsEnabled = false,
            exactAlarmsEnabled = false,
            criticalChannelBypassesDnd = false,
        )

        assertEquals(3, state.warnings.size)
        // NOTIFICATIONS_DISABLED should be first (most critical)
        assertEquals(PermissionWarning.NOTIFICATIONS_DISABLED, state.warnings[0])
    }

    @Test
    fun alarmKitDisabled_warningPresent() {
        val state = PermissionWarningState(alarmKitEnabled = false)

        assertTrue(state.hasWarnings)
        assertTrue(state.warnings.contains(PermissionWarning.ALARMKIT_DISABLED))
    }

    @Test
    fun criticalAlertsDisabled_warningPresent() {
        val state = PermissionWarningState(criticalAlertsEnabled = false)

        assertTrue(state.hasWarnings)
        assertTrue(state.warnings.contains(PermissionWarning.CRITICAL_ALERTS_DISABLED))
    }
}
