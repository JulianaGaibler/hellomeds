// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.reliability

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.util.ReliabilityState
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.reliability_banner_database_recovered_action
import me.juliana.hellomeds.shared.reliability_banner_database_recovered_message
import me.juliana.hellomeds.shared.reliability_banner_exact_alarms_action
import me.juliana.hellomeds.shared.reliability_banner_exact_alarms_message
import me.juliana.hellomeds.shared.reliability_banner_ios_budget_message
import org.jetbrains.compose.resources.stringResource

/**
 * Persistent reliability banner that surfaces system-level reminder pipeline
 * degradations: revoked exact-alarm permission (Android), notification budget
 * exhaustion (iOS), and destructive database recovery (cross-platform — fired
 * after a Keychain/MasterKey invalidation forced a wipe).
 *
 * Renders nothing on [ReliabilityState.Healthy]. Renders nothing for the
 * `missedDoses`-only Degraded case — those surface in the Tracking screen's
 * Missed/Overdue sections instead.
 *
 * Multiple flags can theoretically be set at once; this composable stacks the
 * banners vertically. In practice, `exactAlarmsDisabled` is Android-only and
 * `iosNotificationBudgetExhausted` is iOS-only, so at most one platform banner
 * renders on a given device. The `databaseRecovered` banner can stack on top
 * because the wipe affects both platforms equally and the user must explicitly
 * dismiss it.
 */
@Composable
fun ReliabilityBanner(
    state: ReliabilityState,
    onFixExactAlarms: () -> Unit,
    onDismissDatabaseRecovered: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val degraded = state as? ReliabilityState.Degraded ?: return
    if (!degraded.exactAlarmsDisabled &&
        !degraded.iosNotificationBudgetExhausted &&
        !degraded.databaseRecovered
    ) {
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Render the data-loss banner first — it is the most severe of the three
        // (user data was actually wiped) and may need re-import action before the
        // other banners are even meaningful.
        if (degraded.databaseRecovered) {
            BannerRow(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                icon = { tint ->
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                },
                message = stringResource(Res.string.reliability_banner_database_recovered_message),
                action = {
                    TextButton(onClick = onDismissDatabaseRecovered) {
                        Text(
                            text = stringResource(Res.string.reliability_banner_database_recovered_action),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                },
            )
        }
        if (degraded.exactAlarmsDisabled) {
            BannerRow(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                icon = { tint ->
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                },
                message = stringResource(Res.string.reliability_banner_exact_alarms_message),
                action = {
                    TextButton(onClick = onFixExactAlarms) {
                        Text(
                            text = stringResource(Res.string.reliability_banner_exact_alarms_action),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                },
            )
        }
        if (degraded.iosNotificationBudgetExhausted) {
            BannerRow(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                icon = { tint ->
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                },
                message = stringResource(Res.string.reliability_banner_ios_budget_message),
                action = null,
            )
        }
    }
}

@Composable
private fun BannerRow(
    containerColor: Color,
    contentColor: Color,
    icon: @Composable (tint: Color) -> Unit,
    message: String,
    action: (@Composable () -> Unit)?,
) {
    Surface(
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            icon(contentColor)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )
            if (action != null) action()
        }
    }
}
