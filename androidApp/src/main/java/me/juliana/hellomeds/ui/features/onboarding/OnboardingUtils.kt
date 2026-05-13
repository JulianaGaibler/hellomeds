// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.onboarding

import android.content.Context
import me.juliana.hellomeds.util.PermissionUtils

object OnboardingUtils {

    /**
     * Check if all critical permissions are granted
     */
    fun areAllPermissionsGranted(context: Context): Boolean {
        return PermissionUtils.areNotificationsEnabled(context) &&
            PermissionUtils.canScheduleExactAlarms(context)
    }

    /**
     * Check which permissions are missing
     * @return Map of permission name to granted status
     */
    fun getPermissionStatus(context: Context): Map<String, Boolean> {
        return mapOf(
            "notifications" to PermissionUtils.areNotificationsEnabled(context),
            "exactAlarms" to PermissionUtils.canScheduleExactAlarms(context),
        )
    }

    /**
     * Get a list of missing permission names for display
     */
    fun getMissingPermissions(context: Context): List<String> {
        val missing = mutableListOf<String>()
        if (!PermissionUtils.areNotificationsEnabled(context)) {
            missing.add("Notifications")
        }
        if (!PermissionUtils.canScheduleExactAlarms(context)) {
            missing.add("Exact Alarms")
        }
        return missing
    }

    /**
     * Detect which permissions have been revoked since onboarding
     *
     * This is used to show the "Permission Revoked" dialog when a user
     * returns to the app after revoking a permission in system settings.
     *
     * @param context Application context
     * @param onboardingCompleted Whether the user has completed onboarding
     * @return List of revoked permissions that should trigger a dialog
     */
    fun detectRevokedPermissions(context: Context, onboardingCompleted: Boolean): List<RevokedPermission> {
        // Only check if onboarding was completed (meaning permissions were granted at some point)
        if (!onboardingCompleted) {
            return emptyList()
        }

        val revoked = mutableListOf<RevokedPermission>()

        // Check each permission
        if (!PermissionUtils.areNotificationsEnabled(context)) {
            revoked.add(RevokedPermission.NOTIFICATIONS)
        }

        if (!PermissionUtils.canScheduleExactAlarms(context)) {
            revoked.add(RevokedPermission.EXACT_ALARMS)
        }

        return revoked
    }
}
