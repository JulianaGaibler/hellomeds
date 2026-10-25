// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupNudgePolicyTest {

    private val day = 24 * 60 * 60 * 1000L
    private val onboarded = 1_000_000L

    private fun show(
        now: Long,
        nextAt: Long = 0L,
        enabled: Boolean = false,
        optedOut: Boolean = false,
        hasMeds: Boolean = true,
    ) = BackupNudgePolicy.shouldShow(now, nextAt, onboarded, enabled, optedOut, hasMeds)

    @Test
    fun firstAsk_dueTwoDaysAfterOnboarding() {
        assertFalse(show(onboarded + 2 * day - 1))
        assertTrue(show(onboarded + 2 * day))
    }

    @Test
    fun neverShows_whenBackupsOnOptedOutOrNoMedications() {
        val late = onboarded + 10 * day
        assertFalse(show(late, enabled = true))
        assertFalse(show(late, optedOut = true))
        assertFalse(show(late, hasMeds = false))
    }

    @Test
    fun medicationAdded_pullsTheAskForwardButNeverPushesItBack() {
        val now = onboarded + day
        assertEquals(
            now + BackupNudgePolicy.AFTER_MEDICATION_DELAY_MS,
            BackupNudgePolicy.afterMedicationAdded(now, 0L, onboarded),
        )
        val alreadySoon = now + 1000
        assertEquals(alreadySoon, BackupNudgePolicy.afterMedicationAdded(now, alreadySoon, onboarded))
    }

    @Test
    fun snooze_repeatsEveryThreeDays() {
        val now = onboarded + 5 * day
        val next = BackupNudgePolicy.afterSnooze(now)
        assertFalse(show(now + 3 * day - 1, nextAt = next))
        assertTrue(show(next, nextAt = next))
    }
}
