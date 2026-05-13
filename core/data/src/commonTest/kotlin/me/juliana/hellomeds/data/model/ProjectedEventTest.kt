// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.model

import me.juliana.hellomeds.data.createHistory
import me.juliana.hellomeds.data.createProjectedEvent
import me.juliana.hellomeds.data.database.entities.MedicationHistory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectedEventTest {

    @Test
    fun isPending_trueWhenNoHistory() {
        val event = createProjectedEvent(historyRecord = null)
        assertTrue(event.isPending)
    }

    @Test
    fun isPending_falseWhenHistoryExists() {
        val event = createProjectedEvent(
            historyRecord = createHistory(status = MedicationHistory.STATUS_TAKEN),
        )
        assertFalse(event.isPending)
    }

    @Test
    fun isTaken_trueForTakenStatus() {
        val event = createProjectedEvent(
            historyRecord = createHistory(status = MedicationHistory.STATUS_TAKEN),
        )
        assertTrue(event.isTaken)
    }

    @Test
    fun isTaken_falseForSkippedStatus() {
        val event = createProjectedEvent(
            historyRecord = createHistory(status = MedicationHistory.STATUS_SKIPPED),
        )
        assertFalse(event.isTaken)
    }

    @Test
    fun isTaken_falseWhenNoHistory() {
        val event = createProjectedEvent(historyRecord = null)
        assertFalse(event.isTaken)
    }

    @Test
    fun isSkipped_trueForSkippedStatus() {
        val event = createProjectedEvent(
            historyRecord = createHistory(status = MedicationHistory.STATUS_SKIPPED),
        )
        assertTrue(event.isSkipped)
    }

    @Test
    fun isSkipped_trueForAutoSkippedStatus() {
        val event = createProjectedEvent(
            historyRecord = createHistory(status = MedicationHistory.STATUS_AUTO_SKIPPED),
        )
        assertTrue(event.isSkipped)
    }

    @Test
    fun isSkipped_falseForTakenStatus() {
        val event = createProjectedEvent(
            historyRecord = createHistory(status = MedicationHistory.STATUS_TAKEN),
        )
        assertFalse(event.isSkipped)
    }

    @Test
    fun isSkipped_falseWhenNoHistory() {
        val event = createProjectedEvent(historyRecord = null)
        assertFalse(event.isSkipped)
    }

    @Test
    fun compositeKey_format() {
        val event = createProjectedEvent(scheduleId = 5, scheduledTime = 1000L)
        assertEquals("1_5_1000", event.compositeKey)
    }
}
