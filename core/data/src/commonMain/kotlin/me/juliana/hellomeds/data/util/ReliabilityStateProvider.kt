// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import me.juliana.hellomeds.data.preferences.ReliabilityPreferences

/**
 * Single Flow that aggregates everything the UI needs to know about reminder
 * pipeline reliability — missed doses (common), exact-alarm permission state
 * (Android), and local-notification budget state (iOS).
 *
 * Emits [ReliabilityState.Healthy] when nothing is degraded, else
 * [ReliabilityState.Degraded] carrying every active concern. The shared UI
 * observes this and renders banners / recovery dialogs in one place.
 */
class ReliabilityStateProvider(
    private val missedDoseDetector: MissedDoseDetector,
    private val reliabilityPrefs: ReliabilityPreferences,
) {

    fun state(): Flow<ReliabilityState> = combine(
        missedDoseDetector.missedDoses(),
        reliabilityPrefs.exactAlarmsDisabled,
        reliabilityPrefs.iosNotificationBudgetExhausted,
        reliabilityPrefs.databaseRecovered,
    ) { missed, exactDisabled, budgetExhausted, dbRecovered ->
        if (missed.isEmpty() && !exactDisabled && !budgetExhausted && !dbRecovered) {
            ReliabilityState.Healthy
        } else {
            ReliabilityState.Degraded(
                missedDoses = missed,
                exactAlarmsDisabled = exactDisabled,
                iosNotificationBudgetExhausted = budgetExhausted,
                databaseRecovered = dbRecovered,
            )
        }
    }

    /**
     * Clears the database-recovery banner. Called after the user acknowledges
     * the "data lost — restore from backup" prompt. The flag is sticky in
     * DataStore until this is invoked, so the user cannot miss it across app
     * restarts.
     */
    suspend fun dismissDatabaseRecoveryWarning() {
        reliabilityPrefs.setDatabaseRecovered(false)
    }
}
