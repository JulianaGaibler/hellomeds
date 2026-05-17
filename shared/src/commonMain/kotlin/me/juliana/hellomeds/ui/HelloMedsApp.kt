// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.util.ReliabilityState
import me.juliana.hellomeds.data.util.ReliabilityStateProvider
import me.juliana.hellomeds.ui.compat.collectAsStateWithLifecycle
import me.juliana.hellomeds.ui.components.reliability.ReliabilityBanner
import me.juliana.hellomeds.ui.navigation3.HelloMedsNavigation
import me.juliana.hellomeds.ui.navigation3.MainAppRoute
import me.juliana.hellomeds.ui.navigation3.NavigationScreenProviders
import me.juliana.hellomeds.ui.util.LocalPermissionWarnings
import me.juliana.hellomeds.ui.util.PermissionWarningState
import me.juliana.hellomeds.ui.util.PlatformCapabilities
import me.juliana.hellomeds.ui.util.PlatformNavigator
import me.juliana.hellomeds.ui.util.rememberPermissionWarnings
import org.koin.compose.koinInject

/**
 * Shared top-level app composable.
 *
 * Hosts the full navigation graph via [HelloMedsNavigation].
 * Platform-specific screens (Settings, Debug, Camera, etc.) are injected via
 * [NavigationScreenProviders].
 *
 * @param screenProviders Provides app-module-only screen implementations.
 *   On Android the app module passes real screens; on iOS defaults are used.
 * @param notificationEventIds Deep-link payload from notification tap.
 * @param notificationGroupingMode Notification grouping mode from intent.
 * @param onNotificationHandled Callback to clear notification deep-link data.
 * @param stockDetailMedicationId Deep-link medication ID for stock detail.
 * @param onStockDetailHandled Callback to clear stock detail deep-link data.
 */
@Composable
fun HelloMedsApp(
    screenProviders: NavigationScreenProviders = NavigationScreenProviders(),
    notificationEventIds: IntArray? = null,
    notificationGroupingMode: String? = null,
    onNotificationHandled: () -> Unit = {},
    stockDetailMedicationId: Int? = null,
    onStockDetailHandled: () -> Unit = {},
) {
    val permissionWarnings = rememberPermissionWarnings()
    val medicationDao: MedicationDao = koinInject()
    val hasCriticalMeds by medicationDao.hasActiveMedicationsWithCriticalLabel()
        .collectAsStateWithLifecycle(initial = false)
    val hasAlarmMeds by medicationDao.hasActiveMedicationsWithAlarmLabel()
        .collectAsStateWithLifecycle(initial = false)

    // Only show permission warnings when a medication actually needs the capability.
    val effectiveWarnings = PermissionWarningState.filterByMedications(
        raw = permissionWarnings,
        hasCriticalMeds = hasCriticalMeds,
        hasAlarmMeds = hasAlarmMeds,
        isAlarmKitAuthorized = PlatformCapabilities.isAlarmKitAuthorized(),
    )

    // Reliability state surfacing — banner above the navigation graph for
    // system-level reminder pipeline degradations (revoked exact-alarm
    // permission on Android, notification budget exhaustion on iOS).
    // The `missedDoses`-only Degraded case is rendered by the Tracking screen,
    // not the banner — see ReliabilityBanner for the early-return on that.
    val reliabilityProvider: ReliabilityStateProvider = koinInject()
    val platformNavigator: PlatformNavigator = koinInject()
    val reliabilityFlow = remember(reliabilityProvider) { reliabilityProvider.state() }
    val reliabilityState by reliabilityFlow.collectAsStateWithLifecycle(
        initial = ReliabilityState.Healthy,
    )
    val reliabilityScope = rememberCoroutineScope()

    CompositionLocalProvider(LocalPermissionWarnings provides effectiveWarnings) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ReliabilityBanner(
                    state = reliabilityState,
                    onFixExactAlarms = { platformNavigator.openExactAlarmSettings() },
                    onDismissDatabaseRecovered = {
                        reliabilityScope.launch { reliabilityProvider.dismissDatabaseRecoveryWarning() }
                    },
                )
                HelloMedsNavigation(
                    initialRoute = MainAppRoute,
                    notificationEventIds = notificationEventIds,
                    notificationGroupingMode = notificationGroupingMode,
                    onNotificationHandled = onNotificationHandled,
                    stockDetailMedicationId = stockDetailMedicationId,
                    onStockDetailHandled = onStockDetailHandled,
                    screenProviders = screenProviders,
                )
            }
        }
    }
}
