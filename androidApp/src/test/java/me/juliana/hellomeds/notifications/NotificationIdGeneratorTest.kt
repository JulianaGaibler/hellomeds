// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationIdGeneratorTest {

    @Test
    fun `alarm request code is positive`() {
        val code = NotificationIdGenerator.generateAlarmRequestCode(
            scheduledTime = 1_700_000_000_000L,
        )
        assertTrue(code >= 0)
    }

    @Test
    fun `alarm request code unique per scheduled time`() {
        val code1 = NotificationIdGenerator.generateAlarmRequestCode(scheduledTime = 1_700_000_000_000L)
        val code2 = NotificationIdGenerator.generateAlarmRequestCode(scheduledTime = 1_700_000_060_000L)
        assertNotEquals(code1, code2)
    }

    @Test
    fun `alarm request code unique per follow-up index`() {
        val code0 = NotificationIdGenerator.generateAlarmRequestCode(
            scheduledTime = 1_700_000_000_000L,
            followUpIndex = 0,
        )
        val code1 = NotificationIdGenerator.generateAlarmRequestCode(
            scheduledTime = 1_700_000_000_000L,
            followUpIndex = 1,
        )
        assertNotEquals(code0, code1)
    }

    @Test
    fun `alarm request code large follow-up index in range`() {
        val code = NotificationIdGenerator.generateAlarmRequestCode(
            scheduledTime = 1_700_000_000_000L,
            followUpIndex = 1000,
        )
        assertTrue(code in 0..Int.MAX_VALUE)
    }

    @Test
    fun `alarm request code large timestamp in range`() {
        // Year ~2100 timestamp
        val code = NotificationIdGenerator.generateAlarmRequestCode(
            scheduledTime = 4_102_444_800_000L,
        )
        assertTrue(code in 0..Int.MAX_VALUE)
    }

    @Test
    fun `session notification id is positive and bounded`() {
        val id = NotificationIdGenerator.generateSessionNotificationId(1_700_000_000_000L)
        assertTrue("ID should be >= 0", id >= 0)
        assertTrue("ID should be < 1_000_000", id < 1_000_000)
    }

    @Test
    fun `session notification id is stable`() {
        val id1 = NotificationIdGenerator.generateSessionNotificationId(1_700_000_000_000L)
        val id2 = NotificationIdGenerator.generateSessionNotificationId(1_700_000_000_000L)
        assertEquals(id1, id2)
    }

    @Test
    fun `session notification id same for identical timestamps`() {
        val time = 1_700_000_000_000L
        val a = NotificationIdGenerator.generateSessionNotificationId(time)
        val b = NotificationIdGenerator.generateSessionNotificationId(time)
        assertEquals("Same timestamp should yield same notification ID for update-in-place", a, b)
    }

    @Test
    fun `session notification id bounded for multiple timestamps`() {
        val timestamps = listOf(
            0L,
            1L,
            1_700_000_000_000L,
            4_102_444_800_000L,
            Long.MAX_VALUE,
        )
        for (ts in timestamps) {
            val id = NotificationIdGenerator.generateSessionNotificationId(ts)
            assertTrue("ID for $ts should be >= 0, was $id", id >= 0)
            assertTrue("ID for $ts should be < 1_000_000, was $id", id < 1_000_000)
        }
    }

    @Test
    fun `session notification id no negative when hash is Int MIN_VALUE`() {
        // Find a value whose Long.hashCode() == Int.MIN_VALUE
        // Long.hashCode() = (value xor (value ushr 32)).toInt()
        // Int.MIN_VALUE = 0x80000000
        // We need a Long where lower 32 bits XOR upper 32 bits = 0x80000000
        // e.g. Long with upper=0x80000000, lower=0x00000000 → 0x80000000_00000000L
        val tricky =
            Long.MIN_VALUE // hashCode() = (MIN_VALUE xor (MIN_VALUE ushr 32)).toInt() = (0x80000000).toInt() = Int.MIN_VALUE
        assertEquals("Precondition: hash should be Int.MIN_VALUE", Int.MIN_VALUE, tricky.hashCode())
        val id = NotificationIdGenerator.generateSessionNotificationId(tricky)
        assertTrue("ID should be >= 0 even for Int.MIN_VALUE hash, was $id", id >= 0)
        assertTrue("ID should be < 1_000_000, was $id", id < 1_000_000)
    }

    @Test
    fun `medication notification id is positive`() {
        val id = NotificationIdGenerator.generateMedicationNotificationId(
            scheduledTime = 1_700_000_000_000L,
            scheduleId = 42,
        )
        assertTrue(id >= 0)
    }

    @Test
    fun `medication notification id is in expected range`() {
        val id = NotificationIdGenerator.generateMedicationNotificationId(
            scheduledTime = 1_700_000_000_000L,
            scheduleId = 42,
        )
        assertTrue("ID should be >= 3_000_000", id >= 3_000_000)
        assertTrue("ID should be < 4_000_000", id < 4_000_000)
    }

    @Test
    fun `medication notification id unique per scheduleId`() {
        val id1 = NotificationIdGenerator.generateMedicationNotificationId(
            scheduledTime = 1_700_000_000_000L,
            scheduleId = 1,
        )
        val id2 = NotificationIdGenerator.generateMedicationNotificationId(
            scheduledTime = 1_700_000_000_000L,
            scheduleId = 2,
        )
        assertNotEquals(id1, id2)
    }

    @Test
    fun `medication notification id stable for same inputs`() {
        val id1 = NotificationIdGenerator.generateMedicationNotificationId(
            scheduledTime = 1_700_000_000_000L,
            scheduleId = 5,
        )
        val id2 = NotificationIdGenerator.generateMedicationNotificationId(
            scheduledTime = 1_700_000_000_000L,
            scheduleId = 5,
        )
        assertEquals(id1, id2)
    }

    @Test
    fun `medication notification id does not collide with depletion ids`() {
        val medId = NotificationIdGenerator.generateMedicationNotificationId(
            scheduledTime = 1_700_000_000_000L,
            scheduleId = 1,
        )
        val depletionId = NotificationIdGenerator.generateDepletionNotificationId(1)
        assertNotEquals(medId, depletionId)
    }

    @Test
    fun `all id ranges are non-overlapping`() {
        val sessionId = NotificationIdGenerator.generateSessionNotificationId(1_700_000_000_000L)
        val depletionId = NotificationIdGenerator.generateDepletionNotificationId(500)
        val lowStockId = NotificationIdGenerator.generateLowStockNotificationId(500)
        val medicationId = NotificationIdGenerator.generateMedicationNotificationId(
            scheduledTime = 1_700_000_000_000L,
            scheduleId = 500,
        )

        // Verify each falls in its designated range
        assertTrue("Session ID in [0, 1M)", sessionId in 0 until 1_000_000)
        assertTrue("Depletion ID in [1M, 2M)", depletionId in 1_000_000 until 2_000_000)
        assertTrue("Low stock ID in [2M, 3M)", lowStockId in 2_000_000 until 3_000_000)
        assertTrue("Medication ID in [3M, 4M)", medicationId in 3_000_000 until 4_000_000)

        // Verify no two are equal (redundant given ranges, but explicit)
        val ids = setOf(sessionId, depletionId, lowStockId, medicationId)
        assertEquals("All four IDs should be distinct", 4, ids.size)
    }
}
