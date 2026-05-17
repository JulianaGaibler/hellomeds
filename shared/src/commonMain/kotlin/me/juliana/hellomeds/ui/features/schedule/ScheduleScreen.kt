// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.model.enums.CycleType
import me.juliana.hellomeds.data.model.enums.FrequencyType
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.designsystem.testing.ScreenshotTestTags
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.content_description_back
import me.juliana.hellomeds.shared.schedule_add
import me.juliana.hellomeds.shared.schedule_cycle_info_body_1
import me.juliana.hellomeds.shared.schedule_cycle_info_body_2
import me.juliana.hellomeds.shared.schedule_cycle_info_title
import me.juliana.hellomeds.shared.schedule_reminder_hint_action_dismiss
import me.juliana.hellomeds.shared.schedule_reminder_hint_action_edit
import me.juliana.hellomeds.shared.schedule_reminder_hint_body
import me.juliana.hellomeds.shared.schedule_reminder_hint_title
import me.juliana.hellomeds.shared.schedule_screen_description
import me.juliana.hellomeds.shared.schedule_screen_title
import me.juliana.hellomeds.shared.schedule_section_archived
import me.juliana.hellomeds.shared.schedule_section_past
import me.juliana.hellomeds.ui.compat.ListItemShapes
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.components.common.AppScaffold
import me.juliana.hellomeds.ui.components.list.SmartList
import me.juliana.hellomeds.ui.components.list.SmartListDivider
import me.juliana.hellomeds.ui.components.list.SmartListInfoCard
import me.juliana.hellomeds.ui.components.list.smartListSegmentedShapes
import me.juliana.hellomeds.ui.components.medication.CycleProgressIndicator
import me.juliana.hellomeds.ui.theme.HelloMedsTheme
import me.juliana.hellomeds.ui.util.formatDoseText
import me.juliana.hellomeds.ui.util.formatScheduleEndDate
import me.juliana.hellomeds.ui.util.formatScheduleTitle
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    schedules: List<Schedule>,
    medicationType: MedicationType,
    onNavigateBack: () -> Unit,
    onAddSchedule: () -> Unit,
    onEditSchedule: (Schedule) -> Unit,
    medication: Medication? = null,
    showReminderTypeHint: Boolean = false,
    onEditReminderType: () -> Unit = {},
    onDismissReminderTypeHint: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward },
    )

    val activeSchedules = schedules.filter { !it.isEffectivelyArchived() }
    val pastSchedules = schedules.filter { it.isDateArchived() }
    val archivedSchedules = schedules.filter { it.isManuallyArchived() }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        AppScaffold(
            topBar = {
                LargeTopAppBar(
                    title = {
                        Text(text = stringResource(Res.string.schedule_screen_title))
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.content_description_back),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            },
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                state = scrollState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        text = stringResource(Res.string.schedule_screen_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                }

                if (showReminderTypeHint && schedules.isNotEmpty()) {
                    item {
                        SmartList(modifier = Modifier.padding(bottom = 16.dp)) {
                            SmartListInfoCard(
                                headlineContent = {
                                    Text(
                                        stringResource(Res.string.schedule_reminder_hint_title),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                },
                                supportingContent = {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            stringResource(Res.string.schedule_reminder_hint_body),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                        ) {
                                            TextButton(onClick = {
                                                onDismissReminderTypeHint()
                                                onEditReminderType()
                                            }) {
                                                Text(stringResource(Res.string.schedule_reminder_hint_action_edit))
                                            }
                                            Button(
                                                onClick = onDismissReminderTypeHint,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                ),
                                            ) {
                                                Text(stringResource(Res.string.schedule_reminder_hint_action_dismiss))
                                            }
                                        }
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }

                if (medication?.cycleType == CycleType.CYCLIC) {
                    item {
                        SmartList(modifier = Modifier.padding(bottom = 24.dp)) {
                            SmartListInfoCard(
                                headlineContent = {
                                    Text(
                                        stringResource(Res.string.schedule_cycle_info_title),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                },
                                supportingContent = {
                                    val body1 = stringResource(Res.string.schedule_cycle_info_body_1)
                                    val body2 = stringResource(Res.string.schedule_cycle_info_body_2)
                                    Text(
                                        "$body1\n\n$body2",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                shapes = smartListSegmentedShapes(index = 0, count = 2),
                            )
                            SmartListDivider()
                            val currentDate = Clock.System.now()
                                .toLocalDateTime(TimeZone.currentSystemDefault()).date
                            Surface(
                                shape = smartListSegmentedShapes(index = 1, count = 2).shape,
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                ) {
                                    CycleProgressIndicator(
                                        medication = medication,
                                        currentDate = currentDate,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        onClick = onAddSchedule,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(ScreenshotTestTags.SCHEDULE_ADD_BUTTON),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(Res.string.schedule_add),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                if (activeSchedules.isNotEmpty()) {
                    item {
                        SmartList {
                            activeSchedules.forEachIndexed { index, schedule ->
                                ScheduleListItem(
                                    schedule = schedule,
                                    medicationType = medicationType,
                                    onClick = { onEditSchedule(schedule) },
                                    shapes = smartListSegmentedShapes(
                                        index = index,
                                        count = activeSchedules.size,
                                    ),
                                )

                                if (index < activeSchedules.lastIndex) {
                                    SmartListDivider()
                                }
                            }
                        }
                    }
                }

                if (pastSchedules.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(Res.string.schedule_section_past),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }

                    item {
                        SmartList {
                            pastSchedules.forEachIndexed { index, schedule ->
                                ScheduleListItem(
                                    schedule = schedule,
                                    medicationType = medicationType,
                                    onClick = { onEditSchedule(schedule) },
                                    shapes = smartListSegmentedShapes(index = index, count = pastSchedules.size),
                                )

                                if (index < pastSchedules.lastIndex) {
                                    SmartListDivider()
                                }
                            }
                        }
                    }
                }

                if (archivedSchedules.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(Res.string.schedule_section_archived),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }

                    item {
                        SmartList {
                            archivedSchedules.forEachIndexed { index, schedule ->
                                ScheduleListItem(
                                    schedule = schedule,
                                    medicationType = medicationType,
                                    onClick = { onEditSchedule(schedule) },
                                    shapes = smartListSegmentedShapes(
                                        index = index,
                                        count = archivedSchedules.size,
                                    ),
                                )

                                if (index < archivedSchedules.lastIndex) {
                                    SmartListDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleListItem(
    schedule: Schedule,
    medicationType: MedicationType,
    onClick: () -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
) {
    platformContext()

    val title = formatScheduleTitle(schedule)
    val doseText = formatDoseText(schedule, medicationType)
    val endDateText = formatScheduleEndDate(schedule)

    val supportingText = buildString {
        append(doseText)
        if (endDateText != null) {
            append("\n")
            append(endDateText)
        }
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        shape = shapes.shape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScheduleScreenPreview() {
    HelloMedsTheme {
        ScheduleScreen(
            schedules = listOf(
                Schedule(
                    id = 1,
                    medicationId = 1,
                    dose = 1.0,
                    startDate = Clock.System.now().toEpochMilliseconds(),
                    endDate = null,
                    timeOfDay = "08:00",
                    frequencyType = FrequencyType.INTERVAL,
                    frequencyValue = 1,
                    daysOfWeek = null,
                    isArchived = false,
                ),
                Schedule(
                    id = 2,
                    medicationId = 1,
                    dose = 2.0,
                    startDate = Clock.System.now().toEpochMilliseconds(),
                    endDate = null,
                    timeOfDay = "20:00",
                    frequencyType = FrequencyType.DAYS_OF_WEEK,
                    frequencyValue = 0,
                    daysOfWeek = "MONDAY,WEDNESDAY,FRIDAY",
                    isArchived = false,
                ),
                Schedule(
                    id = 3,
                    medicationId = 1,
                    dose = 1.0,
                    startDate = Clock.System.now().toEpochMilliseconds() - 86400000L * 30,
                    endDate = Clock.System.now().toEpochMilliseconds(),
                    timeOfDay = "12:00",
                    frequencyType = FrequencyType.INTERVAL,
                    frequencyValue = 2,
                    daysOfWeek = null,
                    isArchived = true,
                ),
            ),
            medicationType = MedicationType.INJECTION,
            onNavigateBack = {},
            onAddSchedule = {},
            onEditSchedule = {},
        )
    }
}
