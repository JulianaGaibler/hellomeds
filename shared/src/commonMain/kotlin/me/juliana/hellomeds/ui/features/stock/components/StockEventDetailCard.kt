// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.stock.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.model.StockDataPoint
import me.juliana.hellomeds.data.model.StockEvent
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.stock_event_detail_stock
import me.juliana.hellomeds.shared.stock_event_type_adjustment
import me.juliana.hellomeds.shared.stock_event_type_container_switch
import me.juliana.hellomeds.shared.stock_event_type_dose_taken
import me.juliana.hellomeds.shared.stock_event_type_initial_stock
import me.juliana.hellomeds.shared.stock_event_type_manual_correction
import me.juliana.hellomeds.shared.stock_event_type_predicted
import me.juliana.hellomeds.shared.stock_event_type_predicted_empty
import me.juliana.hellomeds.shared.stock_event_type_refill
import me.juliana.hellomeds.shared.stock_event_type_stock_level
import me.juliana.hellomeds.ui.components.graph.util.TimeFormatter
import org.jetbrains.compose.resources.stringResource

@Composable
fun StockEventDetailCard(point: StockDataPoint, modifier: Modifier = Modifier) {
    val event = point.event
    val (icon, label) = when (event) {
        is StockEvent.DoseTaken -> Icons.Default.Check to stringResource(Res.string.stock_event_type_dose_taken)
        is StockEvent.Refill -> Icons.Default.Add to stringResource(
            Res.string.stock_event_type_refill,
            me.juliana.hellomeds.ui.util.formatDecimal(event.quantity),
        )

        is StockEvent.Adjustment -> {
            val typeLabel = when (event.adjustmentType) {
                "INITIAL_STOCK" -> stringResource(Res.string.stock_event_type_initial_stock)
                "MANUAL_CORRECTION" -> stringResource(Res.string.stock_event_type_manual_correction)
                "PREDICTED_EMPTY" -> stringResource(Res.string.stock_event_type_predicted_empty)
                else -> stringResource(Res.string.stock_event_type_adjustment)
            }
            Icons.Default.Edit to typeLabel
        }

        is StockEvent.ContainerSwitch -> Icons.Default.Info to stringResource(
            Res.string.stock_event_type_container_switch,
        )
        null -> Icons.Default.Info to if (point.isFuture) {
            stringResource(Res.string.stock_event_type_predicted)
        } else {
            stringResource(
                Res.string.stock_event_type_stock_level,
            )
        }
    }

    val timestamp = TimeFormatter.formatDetailed(point.timestamp)
    val stockValue = me.juliana.hellomeds.ui.util.formatDecimal(point.value)
    val notes = (event as? StockEvent.Adjustment)?.notes

    ElevatedCard(modifier = modifier.padding(horizontal = 16.dp)) {
        ListItem(
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (point.isFuture) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            },
            headlineContent = { Text(label) },
            supportingContent = {
                val text = buildString {
                    append(timestamp)
                    append(stringResource(Res.string.stock_event_detail_stock, stockValue))
                    if (notes != null) {
                        append("\n$notes")
                    }
                }
                Text(text)
            },
        )
    }
}
