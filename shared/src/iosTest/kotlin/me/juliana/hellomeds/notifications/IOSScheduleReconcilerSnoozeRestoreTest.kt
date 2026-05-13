// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression test for the iOS snooze restoration filter in
 * [IOSScheduleReconciler]'s Step 5. Pre-fix, the restoration loop passed the
 * session's original `scheduleIds` (captured at snooze time) directly into
 * `rescheduleSnoozeNotification`, so any med the user logged in-app during
 * the snooze window was re-surfaced on the restored snooze notification —
 * misleading the user toward a double dose.
 *
 * The fix narrows the list via [computeStillPendingScheduleIds] against the
 * current pending set from the projector. This test pins both branches of
 * that filter:
 *  - partial: only the still-pending id remains in the result
 *  - all-taken: the result is empty, so the caller drops the session entirely
 *
 * MockK is JVM-only and a full IOSScheduleReconciler graph is not testable
 * without standing up a real DataStore, UNUserNotificationCenter, and AlarmKit
 * shim. The helper extraction keeps the regression contract observable with
 * pure kotlin.test, runnable on the iOS simulator alongside the other
 * shared-module tests.
 */
class IOSScheduleReconcilerSnoozeRestoreTest {

    @Test
    fun computeStillPendingScheduleIds_excludesTakenMedFromSnoozeRestore() {
        // Snooze captured both meds (1 and 2). During the snooze, the user
        // logged med 1 via the app. When reconcile() restores the snooze
        // notification, only med 2 must appear — surfacing med 1 again would
        // mis-trust the user into a double dose.
        val sessionScheduleIds = listOf(1, 2)
        val pendingAtSlot = setOf(2)

        val stillPending = computeStillPendingScheduleIds(sessionScheduleIds, pendingAtSlot)

        assertEquals(listOf(2), stillPending)
    }

    @Test
    fun computeStillPendingScheduleIds_allMedsTakenDuringSnooze_returnsEmpty() {
        // Both meds in the snoozed slot were logged in-app during the snooze
        // window. The filter must yield an empty list so reconcile() drops the
        // session entirely (no rescheduled snooze notification + AlarmKit
        // cancelled) instead of firing a phantom alarm for already-taken meds.
        val sessionScheduleIds = listOf(1, 2)
        val pendingAtSlot = emptySet<Int>()

        val stillPending = computeStillPendingScheduleIds(sessionScheduleIds, pendingAtSlot)

        assertTrue(stillPending.isEmpty(), "Empty pending set must collapse stillPendingIds")
    }

    @Test
    fun computeStillPendingScheduleIds_preservesOrderAndDistinctness() {
        // Order in the result reflects the session's captured order — the
        // notification body lists meds in the same order across the initial
        // and restored snooze notifications. A regression that, say, sorts or
        // dedupes here would change the user-visible ordering on snooze.
        val sessionScheduleIds = listOf(2, 1, 3)
        val pendingAtSlot = setOf(1, 2, 3)

        val stillPending = computeStillPendingScheduleIds(sessionScheduleIds, pendingAtSlot)

        assertEquals(listOf(2, 1, 3), stillPending)
    }
}
