// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Observable state for notification tap → deep link.
 *
 * When the user taps a notification (default action), IOSNotificationDelegate
 * stores the scheduleIds here. MainViewController passes this to HelloMedsApp,
 * which flows it through to TrackingScreen.
 *
 * For alarm-type notifications, [isAlarmNotification] is true, which causes the
 * UI to show the fullscreen alarm screen instead of the normal log bottom sheet.
 *
 * Must call [consume] after the action is triggered to prevent re-opening on
 * recomposition (e.g., rotation).
 */
object NotificationDeepLinkState {
    private val _pendingEventIds = MutableStateFlow<IntArray?>(null)
    val pendingEventIds: StateFlow<IntArray?> = _pendingEventIds

    private val _isAlarmNotification = MutableStateFlow(false)
    val isAlarmNotification: StateFlow<Boolean> = _isAlarmNotification

    private val _alarmMedicationNames = MutableStateFlow<List<String>>(emptyList())
    val alarmMedicationNames: StateFlow<List<String>> = _alarmMedicationNames

    private val _alarmScheduledTime = MutableStateFlow(0L)
    val alarmScheduledTime: StateFlow<Long> = _alarmScheduledTime

    fun setPending(scheduleIds: IntArray, isAlarm: Boolean = false) {
        _isAlarmNotification.value = isAlarm
        _pendingEventIds.value = scheduleIds
    }

    fun setAlarmData(medicationNames: List<String>, scheduledTime: Long) {
        _alarmMedicationNames.value = medicationNames
        _alarmScheduledTime.value = scheduledTime
    }

    fun consume() {
        _pendingEventIds.value = null
        _isAlarmNotification.value = false
        _alarmMedicationNames.value = emptyList()
        _alarmScheduledTime.value = 0L
    }
}
