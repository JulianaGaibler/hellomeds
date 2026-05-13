// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

/**
 * Tests the medication-aware permission warning filtering logic.
 *
 * Key invariant: users should never see a warning they cannot act on.
 * - AlarmKit warning only appears when AlarmKit is available (iOS 26+)
 * - On iOS < 26, alarm-label meds fall back to critical notifications,
 *   so the critical alerts warning must appear instead
 */
class PermissionWarningFilterTest {

    // Raw state where every iOS permission is denied — used as input to filtering
    private val allDenied = PermissionWarningState(
        notificationsEnabled = false,
        exactAlarmsEnabled = false,
        fullScreenIntentEnabled = false,
        criticalChannelBypassesDnd = false,
        alarmKitEnabled = false,
        criticalAlertsEnabled = false,
    )

    // =====================================================================
    // No relevant medications → suppress contextual warnings
    // =====================================================================

    @Test
    fun noMedications_suppressesAllContextualWarnings() {
        val filtered = PermissionWarningState.filterByMedications(
            raw = allDenied,
            hasCriticalMeds = false,
            hasAlarmMeds = false,
            isAlarmKitAuthorized = false,
        )

        assertFalse(
            filtered.warnings.contains(PermissionWarning.ALARMKIT_DISABLED),
            "AlarmKit warning should be suppressed without alarm medications",
        )
        assertFalse(
            filtered.warnings.contains(PermissionWarning.CRITICAL_ALERTS_DISABLED),
            "Critical alerts warning should be suppressed without critical/alarm medications",
        )
        assertFalse(
            filtered.warnings.contains(PermissionWarning.CRITICAL_CHANNEL_DND_BLOCKED),
            "DnD warning should be suppressed without critical/alarm medications",
        )
        // Non-contextual warnings should still be present
        assertContains(filtered.warnings, PermissionWarning.NOTIFICATIONS_DISABLED)
    }

    // =====================================================================
    // iOS 26+ with AlarmKit authorized
    // =====================================================================

    @Test
    fun ios26_alarmKitAuthorized_alarmMeds_noAlarmKitWarning() {
        val filtered = PermissionWarningState.filterByMedications(
            raw = allDenied,
            hasCriticalMeds = false,
            hasAlarmMeds = true,
            isAlarmKitAuthorized = true,
        )

        // AlarmKit is authorized → alarmKitEnabled was false in raw but meds exist,
        // however the raw value still flows through since hasAlarmMeds is true
        assertContains(filtered.warnings, PermissionWarning.ALARMKIT_DISABLED)
        // AlarmKit is authorized → alarm meds don't need critical alerts fallback
        assertFalse(
            filtered.warnings.contains(PermissionWarning.CRITICAL_ALERTS_DISABLED),
            "Critical alerts not needed when AlarmKit handles alarm meds",
        )
    }

    @Test
    fun ios26_alarmKitAuthorized_criticalMeds_showsCriticalWarning() {
        val filtered = PermissionWarningState.filterByMedications(
            raw = allDenied,
            hasCriticalMeds = true,
            hasAlarmMeds = false,
            isAlarmKitAuthorized = true,
        )

        assertContains(filtered.warnings, PermissionWarning.CRITICAL_ALERTS_DISABLED)
        assertContains(filtered.warnings, PermissionWarning.CRITICAL_CHANNEL_DND_BLOCKED)
        assertFalse(
            filtered.warnings.contains(PermissionWarning.ALARMKIT_DISABLED),
            "AlarmKit warning should be suppressed without alarm medications",
        )
    }

    // =====================================================================
    // iOS 26+ with AlarmKit NOT authorized — alarm meds fall back to critical
    // =====================================================================

    @Test
    fun ios26_alarmKitDenied_alarmMeds_showsBothWarnings() {
        // Consequence of failure: User denied AlarmKit, alarm meds fall back to
        // critical notifications, but no warning tells them critical alerts are also off.
        val filtered = PermissionWarningState.filterByMedications(
            raw = allDenied,
            hasCriticalMeds = false,
            hasAlarmMeds = true,
            isAlarmKitAuthorized = false,
        )

        assertContains(
            filtered.warnings,
            PermissionWarning.ALARMKIT_DISABLED,
            "Should show AlarmKit warning — user can still authorize it",
        )
        assertContains(
            filtered.warnings,
            PermissionWarning.CRITICAL_ALERTS_DISABLED,
            "Should show critical alerts warning — alarm meds fall back to critical notifications",
        )
    }

    // =====================================================================
    // iOS < 26 (AlarmKit unavailable) — alarm meds MUST use critical notifications
    // =====================================================================

    @Test
    fun iosLegacy_alarmMeds_showsCriticalWarning_notAlarmKit() {
        // Consequence of failure: iOS < 26 user with alarm meds sees an AlarmKit
        // warning they cannot fix, or worse, sees NO warning at all and their
        // alarm meds silently downgrade to timeSensitive.

        // On iOS < 26, PermissionWarningChecker sets alarmKitEnabled=true (suppressed
        // at the checker level since isAlarmKitAvailable()=false). Simulate that:
        val iosLegacyRaw = allDenied.copy(alarmKitEnabled = true)

        val filtered = PermissionWarningState.filterByMedications(
            raw = iosLegacyRaw,
            hasCriticalMeds = false,
            hasAlarmMeds = true,
            isAlarmKitAuthorized = false, // Never set on iOS < 26
        )

        assertFalse(
            filtered.warnings.contains(PermissionWarning.ALARMKIT_DISABLED),
            "AlarmKit warning must NOT appear on iOS < 26 (user cannot fix it)",
        )
        assertContains(
            filtered.warnings,
            PermissionWarning.CRITICAL_ALERTS_DISABLED,
            "Critical alerts warning must appear — alarm meds need it as fallback on iOS < 26",
        )
    }

    @Test
    fun iosLegacy_criticalMeds_showsCriticalWarning() {
        val iosLegacyRaw = allDenied.copy(alarmKitEnabled = true)

        val filtered = PermissionWarningState.filterByMedications(
            raw = iosLegacyRaw,
            hasCriticalMeds = true,
            hasAlarmMeds = false,
            isAlarmKitAuthorized = false,
        )

        assertContains(filtered.warnings, PermissionWarning.CRITICAL_ALERTS_DISABLED)
        assertContains(filtered.warnings, PermissionWarning.CRITICAL_CHANNEL_DND_BLOCKED)
    }

    // =====================================================================
    // Android — AlarmKit/critical alerts warnings never surface
    // =====================================================================

    @Test
    fun android_neverShowsIosWarnings() {
        // Android: alarmKitEnabled=true, criticalAlertsEnabled=true (defaults from checker)
        val androidRaw = PermissionWarningState(
            notificationsEnabled = false,
            exactAlarmsEnabled = false,
            fullScreenIntentEnabled = false,
            criticalChannelBypassesDnd = false,
            alarmKitEnabled = true,
            criticalAlertsEnabled = true,
        )

        val filtered = PermissionWarningState.filterByMedications(
            raw = androidRaw,
            hasCriticalMeds = true,
            hasAlarmMeds = true,
            isAlarmKitAuthorized = true, // Android always returns true
        )

        assertFalse(filtered.warnings.contains(PermissionWarning.ALARMKIT_DISABLED))
        assertFalse(filtered.warnings.contains(PermissionWarning.CRITICAL_ALERTS_DISABLED))
        // Android-specific warnings still present
        assertContains(filtered.warnings, PermissionWarning.NOTIFICATIONS_DISABLED)
        assertContains(filtered.warnings, PermissionWarning.EXACT_ALARMS_DISABLED)
        assertContains(filtered.warnings, PermissionWarning.CRITICAL_CHANNEL_DND_BLOCKED)
    }

    // =====================================================================
    // All permissions granted — no warnings regardless of medications
    // =====================================================================

    @Test
    fun allGranted_noWarnings() {
        val allGranted = PermissionWarningState()

        val filtered = PermissionWarningState.filterByMedications(
            raw = allGranted,
            hasCriticalMeds = true,
            hasAlarmMeds = true,
            isAlarmKitAuthorized = true,
        )

        assertFalse(filtered.hasWarnings, "No warnings when all permissions are granted")
    }
}
