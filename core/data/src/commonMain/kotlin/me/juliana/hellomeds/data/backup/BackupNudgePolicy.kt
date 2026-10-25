// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.backup

/** When to ask the user to turn on automatic backups. All times are epoch milliseconds. */
object BackupNudgePolicy {
    const val FIRST_ASK_DELAY_MS = 2 * 24 * 60 * 60 * 1000L
    const val AFTER_MEDICATION_DELAY_MS = 3 * 60 * 60 * 1000L
    const val REPEAT_DELAY_MS = 3 * 24 * 60 * 60 * 1000L

    /** The stored time, else two days after onboarding, else never (null). */
    fun effectiveNextAt(nextAt: Long, onboardingCompletedAt: Long): Long? = when {
        nextAt > 0L -> nextAt
        onboardingCompletedAt > 0L -> onboardingCompletedAt + FIRST_ASK_DELAY_MS
        else -> null
    }

    fun shouldShow(
        now: Long,
        nextAt: Long,
        onboardingCompletedAt: Long,
        backupsEnabled: Boolean,
        optedOut: Boolean,
        hasMedications: Boolean,
    ): Boolean {
        if (backupsEnabled || optedOut || !hasMedications) return false
        val due = effectiveNextAt(nextAt, onboardingCompletedAt) ?: return false
        return now >= due
    }

    /** A new medication is the first moment there is data worth protecting, so ask a bit sooner. */
    fun afterMedicationAdded(now: Long, nextAt: Long, onboardingCompletedAt: Long): Long {
        val soon = now + AFTER_MEDICATION_DELAY_MS
        val current = effectiveNextAt(nextAt, onboardingCompletedAt) ?: return soon
        return minOf(current, soon)
    }

    fun afterSnooze(now: Long): Long = now + REPEAT_DELAY_MS
}
