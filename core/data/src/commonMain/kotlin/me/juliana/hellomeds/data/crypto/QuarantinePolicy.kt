// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.crypto

/** What to do with a database that was set aside after its encryption key was lost. */
object QuarantinePolicy {
    const val RETENTION_MS = 14 * 24 * 60 * 60 * 1000L

    enum class Action { KEEP, SWAP_BACK, DELETE }

    /**
     * @param quarantinedMedications medications in the set-aside database, or null when it cannot be read.
     * @param liveMedications medications the user has since added to the new database, or null when unknown.
     */
    fun decide(now: Long, quarantinedAt: Long, quarantinedMedications: Int?, liveMedications: Int?): Action = when {
        now - quarantinedAt >= RETENTION_MS -> Action.DELETE
        quarantinedMedications == null -> Action.KEEP
        quarantinedMedications == 0 -> Action.DELETE
        liveMedications == 0 -> Action.SWAP_BACK
        else -> Action.KEEP
    }
}
