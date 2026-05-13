// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.juliana.hellomeds.createNotificationSession
import me.juliana.hellomeds.data.model.NotificationSession
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSerializationTest {

    // Use same Json config as production (NotificationSessionManager line 43)
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `round-trip serialization preserves all fields`() {
        val session = createNotificationSession(
            timeSlotKey = "08:00_1",
            scheduleIds = listOf(1, 2, 3),
            notificationId = 42,
            followUpsFired = 2,
            maxFollowUps = 5,
            followUpIntervalMs = 600_000L,
            nextFollowUpTime = 1_000_000_900_000L,
            channelId = "medication_reminders",
            hasCriticalMed = true,
            criticalAfterFollowUp = 3,
            createdAt = 1_000_000_000_000L,
        )

        val encoded = json.encodeToString(session)
        val decoded = json.decodeFromString<NotificationSession>(encoded)

        assertEquals(session, decoded)
    }

    @Test
    fun `null nextFollowUpTime serializes correctly`() {
        val session = createNotificationSession(nextFollowUpTime = null)

        val encoded = json.encodeToString(session)
        val decoded = json.decodeFromString<NotificationSession>(encoded)

        assertEquals(session, decoded)
    }

    @Test
    fun `null criticalAfterFollowUp serializes correctly`() {
        val session = createNotificationSession(criticalAfterFollowUp = null)

        val encoded = json.encodeToString(session)
        val decoded = json.decodeFromString<NotificationSession>(encoded)

        assertEquals(session, decoded)
    }

    @Test
    fun `deserialize with unknown fields succeeds`() {
        val session = createNotificationSession(timeSlotKey = "test_key", createdAt = 1000L)
        val encoded = json.encodeToString(session)

        // Inject an unknown field into the JSON
        val withExtraField = encoded.replaceFirst("{", """{"futureField":true,""")
        val decoded = json.decodeFromString<NotificationSession>(withExtraField)

        assertEquals(session.timeSlotKey, decoded.timeSlotKey)
        assertEquals(session.createdAt, decoded.createdAt)
    }

    // SessionState is private in NotificationSessionManager, so test the map pattern
    // using a local equivalent
    @Serializable
    private data class TestSessionState(
        val sessions: Map<String, NotificationSession> = emptyMap(),
    )

    @Test
    fun `session map round-trip with multiple sessions`() {
        val state = TestSessionState(
            sessions = mapOf(
                "slot_1" to createNotificationSession(timeSlotKey = "slot_1", notificationId = 1),
                "slot_2" to createNotificationSession(timeSlotKey = "slot_2", notificationId = 2),
                "slot_3" to createNotificationSession(timeSlotKey = "slot_3", notificationId = 3),
            ),
        )

        val encoded = json.encodeToString(state)
        val decoded = json.decodeFromString<TestSessionState>(encoded)

        assertEquals(state, decoded)
        assertEquals(3, decoded.sessions.size)
    }

    @Test
    fun `empty session map round-trip`() {
        val state = TestSessionState(sessions = emptyMap())

        val encoded = json.encodeToString(state)
        val decoded = json.decodeFromString<TestSessionState>(encoded)

        assertEquals(state, decoded)
        assertEquals(0, decoded.sessions.size)
    }
}
