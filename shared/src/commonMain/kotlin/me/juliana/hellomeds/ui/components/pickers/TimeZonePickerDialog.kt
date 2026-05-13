// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.pickers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.offsetAt
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.timezone_current
import me.juliana.hellomeds.shared.timezone_picker_search
import me.juliana.hellomeds.shared.timezone_picker_title
import org.jetbrains.compose.resources.stringResource

/**
 * Pre-computed display model for a timezone entry.
 */
data class TimeZoneDisplayItem(
    val id: String,
    val city: String,
    val region: String,
    val offset: String,
    val offsetSeconds: Int,
)

/**
 * Formats an IANA timezone ID into city-first display.
 * "America/New_York" → city="New York", region="America"
 * "America/Kentucky/Louisville" → city="Louisville", region="America / Kentucky"
 * "UTC" → city="UTC", region=""
 */
fun formatTimeZoneForDisplay(id: String): Pair<String, String> {
    val parts = id.split("/")
    if (parts.size < 2) return id to ""
    val city = parts.last().replace("_", " ")
    val region = parts.dropLast(1).joinToString(" / ") { it.replace("_", " ") }
    return city to region
}

/**
 * Formats a UTC offset for display, e.g. "UTC+05:30" or "UTC-04:00".
 */
private fun formatOffset(offset: UtcOffset): String {
    val totalSeconds = offset.totalSeconds
    val sign = if (totalSeconds >= 0) "+" else "-"
    val absSeconds = kotlin.math.abs(totalSeconds)
    val hours = absSeconds / 3600
    val minutes = (absSeconds % 3600) / 60
    return "UTC${sign}${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
}

/**
 * Builds pre-computed display items for all available timezones.
 */
private fun buildTimeZoneItems(now: kotlin.time.Instant): List<TimeZoneDisplayItem> {
    return TimeZone.availableZoneIds
        .mapNotNull { id ->
            val tz = runCatching { TimeZone.of(id) }.getOrNull() ?: return@mapNotNull null
            val offset = tz.offsetAt(now)
            val (city, region) = formatTimeZoneForDisplay(id)
            TimeZoneDisplayItem(
                id = id,
                city = city,
                region = region,
                offset = formatOffset(offset),
                offsetSeconds = offset.totalSeconds,
            )
        }
        .sortedBy { it.city.lowercase() }
}

/**
 * Searchable timezone picker dialog.
 *
 * @param selectedTimeZone currently selected IANA timezone ID (for checkmark indicator)
 * @param onSelect called with the selected IANA timezone ID
 * @param onDismiss called when the dialog is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeZonePickerDialog(
    selectedTimeZone: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val now = remember { kotlin.time.Clock.System.now() }
    val allItems = remember { buildTimeZoneItems(now) }
    val currentSystemId = remember { TimeZone.currentSystemDefault().id }
    val currentItem = remember { allItems.find { it.id == currentSystemId } }

    val filteredList by remember {
        derivedStateOf {
            if (searchQuery.isEmpty()) {
                allItems
            } else {
                allItems.filter {
                    it.city.contains(searchQuery, ignoreCase = true) ||
                        it.region.contains(searchQuery, ignoreCase = true)
                }
            }
        }
    }

    // Reset scroll when search changes
    LaunchedEffect(searchQuery) {
        listState.scrollToItem(0)
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime)
                    .padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.timezone_picker_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(Res.string.timezone_picker_search)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                ) {
                    // Pinned "Current" item at top (only when not searching)
                    if (searchQuery.isEmpty() && currentItem != null) {
                        item(key = "current_pin") {
                            TimeZoneListItem(
                                item = currentItem,
                                isSelected = currentItem.id == selectedTimeZone,
                                badge = stringResource(Res.string.timezone_current),
                                onClick = {
                                    onSelect(currentItem.id)
                                    onDismiss()
                                },
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }

                    items(
                        items = filteredList,
                        key = { it.id },
                    ) { item ->
                        // Skip the current item in the main list if it's already pinned
                        if (searchQuery.isEmpty() && item.id == currentSystemId) return@items
                        TimeZoneListItem(
                            item = item,
                            isSelected = item.id == selectedTimeZone,
                            onClick = {
                                onSelect(item.id)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeZoneListItem(
    item: TimeZoneDisplayItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    badge: String? = null,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.city)
                if (badge != null) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        supportingContent = {
            val text = if (item.region.isNotEmpty()) "${item.region} · ${item.offset}" else item.offset
            Text(text)
        },
        trailingContent = if (isSelected) {
            {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(onClick = onClick),
    )
}
