// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.util

/**
 * Aggregated reliability state for the medication reminder pipeline.
 *
 * The UI almost always wants to short-circuit on [Healthy] (no banners, no
 * recovery dialogs) and only render recovery surfaces on [Degraded] — sealed
 * makes that a single `when` away.
 *
 * [Degraded] carries multiple concurrent issues because they CAN all be true at
 * once (e.g., an Android user with revoked exact-alarm permission AND missed
 * doses from a recent OS-induced alarm drop). Platform flags are mutually
 * exclusive in practice:
 *   - [Degraded.exactAlarmsDisabled]: meaningful only on Android. Always false on iOS.
 *   - [Degraded.iosNotificationBudgetExhausted]: meaningful only on iOS. Always false on Android.
 *   - [Degraded.databaseRecovered]: cross-platform — fires after a destructive key
 *     wipe forced by Keychain/MasterKey invalidation. Cleared by user
 *     acknowledgement via [ReliabilityStateProvider.dismissDatabaseRecoveryWarning].
 */
sealed class ReliabilityState {

    /** Pipeline is functioning normally — no banners, no missed-dose prompts. */
    data object Healthy : ReliabilityState()

    /**
     * One or more reliability degradations are active. Render the appropriate
     * recovery surface(s) for each non-default flag.
     */
    data class Degraded(
        val missedDoses: List<MissedDose>,
        val exactAlarmsDisabled: Boolean,
        val iosNotificationBudgetExhausted: Boolean,
        val databaseRecovered: Boolean = false,
    ) : ReliabilityState() {
        init {
            require(
                missedDoses.isNotEmpty() ||
                    exactAlarmsDisabled ||
                    iosNotificationBudgetExhausted ||
                    databaseRecovered,
            ) {
                "Degraded must carry at least one issue — use Healthy when no issues are present"
            }
        }
    }
}
