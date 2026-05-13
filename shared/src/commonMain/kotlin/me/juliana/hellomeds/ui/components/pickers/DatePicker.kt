// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.pickers

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.model.DayCompletionStatus
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.date_picker_today
import me.juliana.hellomeds.shared.date_picker_tomorrow
import me.juliana.hellomeds.shared.date_picker_yesterday
import me.juliana.hellomeds.shared.medication_all_taken
import me.juliana.hellomeds.shared.medication_completion_status
import me.juliana.hellomeds.shared.outline_task_done_24px
import me.juliana.hellomeds.ui.theme.HelloMedsTheme
import me.juliana.hellomeds.ui.util.DayOfWeekFormatter
import me.juliana.hellomeds.ui.util.formatDate
import me.juliana.hellomeds.ui.util.formatDateWithTodayPrefix
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.time.Clock

private suspend fun LazyListState.animateScrollAndCentralizeItem(index: Int) {
    val itemInfo = this.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (itemInfo != null) {
        val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
        val childCenter = itemInfo.offset + itemInfo.size / 2
        animateScrollBy((childCenter - viewportCenter).toFloat())
    } else {
        animateScrollToItem(index)
    }
}

private suspend fun LazyListState.scrollAndCentralizeItem(index: Int) {
    val itemInfo = this.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (itemInfo != null) {
        val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
        val childCenter = itemInfo.offset + itemInfo.size / 2
        scrollBy((childCenter - viewportCenter).toFloat())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerHeader(
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val headerText = when (selectedDate) {
        today -> stringResource(Res.string.date_picker_today)
        today.plus(1, DateTimeUnit.DAY) -> stringResource(Res.string.date_picker_tomorrow)
        today.plus(-1, DateTimeUnit.DAY) -> stringResource(Res.string.date_picker_yesterday)
        else -> DayOfWeekFormatter.fullName(selectedDate.dayOfWeek)
    }

    TopAppBar(
        title = {
            Column(
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Text(
                    text = headerText,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = formatDate(selectedDate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier,
    )
}

@Composable
fun HorizontalDatePicker(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    dayRange: Int = 30,
    completionStatusMap: Map<LocalDate, DayCompletionStatus> = emptyMap(),
    onCenteredDateChanged: ((LocalDate) -> Unit)? = null,
) {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val startDate = today.plus(-dayRange, DateTimeUnit.DAY)
    val totalDays = dayRange * 2 + 1

    val initialIndex = remember(selectedDate, startDate) {
        startDate.daysUntil(selectedDate)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Calculate the centered item index
    val centeredItemIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = layoutInfo.viewportSize.width / 2
            layoutInfo.visibleItemsInfo
                .minByOrNull { abs((it.offset + it.size / 2) - viewportCenter) }
                ?.index ?: initialIndex
        }
    }

    // Center the initial item on first load (without animation)
    LaunchedEffect(initialIndex) {
        listState.scrollAndCentralizeItem(initialIndex)
    }

    // Trigger haptic feedback when centered date changes during scrolling
    LaunchedEffect(centeredItemIndex) {
        // Only trigger haptic if user is actively scrolling (not on initial load)
        if (listState.isScrollInProgress) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // Update centered date in real-time (for header preview)
    LaunchedEffect(centeredItemIndex, startDate) {
        if (centeredItemIndex in 0 until totalDays) {
            val centeredDate = startDate.plus(centeredItemIndex, DateTimeUnit.DAY)
            onCenteredDateChanged?.invoke(centeredDate)
        }
    }

    // Update selected date only when scrolling has settled
    LaunchedEffect(listState, startDate) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (!isScrolling) {
                    val index = centeredItemIndex
                    if (index in 0 until totalDays) {
                        val date = startDate.plus(index, DateTimeUnit.DAY)
                        onDateSelected(date)
                    }
                }
            }
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        flingBehavior = rememberSnapFlingBehavior(
            lazyListState = listState,
        ),
    ) {
        items(totalDays) { index ->
            val date = startDate.plus(index, DateTimeUnit.DAY)
            val isSelected = centeredItemIndex == index
            val isToday = date == today
            val completionStatus = completionStatusMap[date]

            DatePickerItem(
                date = date,
                isSelected = isSelected,
                isToday = isToday,
                completionStatus = completionStatus,
                onClick = {
                    coroutineScope.launch {
                        listState.animateScrollAndCentralizeItem(index)
                    }
                },
            )
        }
    }
}

@Composable
private fun DatePickerItem(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    completionStatus: DayCompletionStatus? = null,
) {
    val dayOfWeek = DayOfWeekFormatter.shortName(date.dayOfWeek).first().uppercaseChar()
    val contentDesc = formatDateWithTodayPrefix(date)

    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val isPastOrToday = date <= today
    val showCompletionIndicator = isPastOrToday && completionStatus?.hasScheduledMedications == true
    val allTakenDesc = if (showCompletionIndicator && completionStatus?.isFullyCompleted == true) {
        stringResource(Res.string.medication_all_taken)
    } else {
        null
    }
    val completionDesc = if (showCompletionIndicator && completionStatus!!.completed > 0 && !completionStatus.isFullyCompleted) {
        stringResource(
            Res.string.medication_completion_status,
            completionStatus.completed,
            completionStatus.totalScheduled,
        )
    } else {
        null
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.surfaceContainerLowest
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            )
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .semantics {
                this.contentDescription = contentDesc
                val state = allTakenDesc ?: completionDesc
                if (state != null) {
                    this.stateDescription = state
                }
            }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Circle with completion indicator
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (showCompletionIndicator) {
                if (completionStatus!!.isFullyCompleted) {
                    // Show checkmark for fully completed days
                    Icon(
                        painter = painterResource(Res.drawable.outline_task_done_24px),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else if (completionStatus.completed > 0) {
                    // Show progress circle for partially completed days (only if some taken)
                    val progress = completionStatus.completionPercentage
                    val color = MaterialTheme.colorScheme.onPrimaryContainer

                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clearAndSetSemantics { }
                            .drawBehind {
                                val strokeWidth = 2.dp.toPx()
                                val size = this.size.minDimension - strokeWidth
                                val topLeft = Offset((this.size.width - size) / 2, (this.size.height - size) / 2)

                                // Draw progress arc (starts at top, goes clockwise)
                                val sweepAngle = 360f * progress
                                drawArc(
                                    color = color,
                                    startAngle = -90f, // Start at top
                                    sweepAngle = sweepAngle,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = Size(size, size),
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                )
                            },
                    )
                }
            }
        }

        // Day letter
        Text(
            text = dayOfWeek.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
        )

        // Today indicator dot
        if (isToday) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        } else {
            // Placeholder to maintain consistent spacing
            Box(modifier = Modifier.size(4.dp))
        }
    }
}

@Composable
private fun DatePickerHeaderPreview() {
    HelloMedsTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            DatePickerHeader(
                selectedDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            )
        }
    }
}

@Composable
private fun DatePickerHeaderTomorrowPreview() {
    HelloMedsTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            DatePickerHeader(
                selectedDate = Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault()).date.plus(1, DateTimeUnit.DAY),
            )
        }
    }
}

@Composable
private fun HorizontalDatePickerPreview() {
    HelloMedsTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            HorizontalDatePicker(
                selectedDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
                onDateSelected = {},
            )
        }
    }
}
