// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.interfaces

import me.juliana.hellomeds.data.support.ReconcilerDiagnostic

/**
 * Platform-agnostic interface for schedule alarm reconciliation.
 * Android: AlarmReconciler (AlarmManager-based single alarm)
 * iOS: IOSScheduleReconciler (batch-scheduled UNNotifications)
 */
interface ScheduleReconciler {
    suspend fun reconcile()

    /** Returns a diagnostic summary of the reconciler's health state. */
    suspend fun getDiagnosticSummary(): ReconcilerDiagnostic = ReconcilerDiagnostic(
        healthy = true,
        healthMessage = "Not implemented",
        timezoneId = "",
        utcOffsetSeconds = 0,
        details = emptyMap(),
    )
}
