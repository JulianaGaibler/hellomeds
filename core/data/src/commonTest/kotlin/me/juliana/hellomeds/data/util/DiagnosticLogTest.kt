// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.util

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagnosticLogTest {

    @BeforeTest
    fun setup() {
        DiagnosticLog.clear()
    }

    @Test
    fun addAndRetrieveEntries() {
        DiagnosticLog.w("TestTag", "warning message")
        DiagnosticLog.e("TestTag", "error message")

        val entries = DiagnosticLog.getEntries()
        assertEquals(2, entries.size)
        assertEquals(DiagnosticLog.LogLevel.WARN, entries[0].level)
        assertEquals(DiagnosticLog.LogLevel.ERROR, entries[1].level)
        assertEquals("warning message", entries[0].message)
    }

    @Test
    fun clearRemovesAllEntries() {
        DiagnosticLog.w("A", "msg")
        DiagnosticLog.e("B", "msg")
        DiagnosticLog.clear()

        assertTrue(DiagnosticLog.getEntries().isEmpty())
    }

    @Test
    fun getEntriesSinceTimestamp_filtersCorrectly() {
        DiagnosticLog.w("A", "old entry")
        val afterFirst = DiagnosticLog.getEntries().last().timestamp
        DiagnosticLog.e("B", "new entry")

        val recent = DiagnosticLog.getEntries(sinceTimestamp = afterFirst)
        // Should include entries with timestamp >= afterFirst
        assertTrue(recent.all { it.timestamp >= afterFirst })
        assertTrue(recent.any { it.message == "new entry" })
    }

    @Test
    fun ringBufferEvictsOldestWhenFull() {
        // Fill beyond MAX_ENTRIES (200)
        repeat(210) { i ->
            DiagnosticLog.w("Tag", "msg $i")
        }

        val entries = DiagnosticLog.getEntries()
        assertEquals(200, entries.size)
        // Oldest entries (0-9) should have been evicted; entry 10 should be first
        assertEquals("msg 10", entries.first().message)
        assertEquals("msg 209", entries.last().message)
    }

    @Test
    fun entriesHaveTimestamps() {
        DiagnosticLog.w("Tag", "msg")

        val entry = DiagnosticLog.getEntries().first()
        assertTrue(entry.timestamp > 0, "Timestamp should be positive epoch millis")
    }

    // --- Encoding/decoding tests ---

    @Test
    fun encodeAndDecodeRoundTrip() {
        val entry = DiagnosticLog.LogEntry(
            timestamp = 1711500000000L,
            level = DiagnosticLog.LogLevel.WARN,
            tag = "TestTag",
            message = "simple message",
        )

        val encoded = DiagnosticLog.encodeLine(entry)
        val decoded = DiagnosticLog.decodeLine(encoded)

        assertEquals(entry, decoded)
    }

    @Test
    fun encodeAndDecodeWithPipesInMessage() {
        val entry = DiagnosticLog.LogEntry(
            timestamp = 1711500000000L,
            level = DiagnosticLog.LogLevel.ERROR,
            tag = "Tag",
            message = "User | entered | pipes",
        )

        val encoded = DiagnosticLog.encodeLine(entry)
        val decoded = DiagnosticLog.decodeLine(encoded)

        assertEquals(entry, decoded)
    }

    @Test
    fun encodeAndDecodeWithPipesInTag() {
        val entry = DiagnosticLog.LogEntry(
            timestamp = 1711500000000L,
            level = DiagnosticLog.LogLevel.WARN,
            tag = "Tag|With|Pipes",
            message = "msg",
        )

        val encoded = DiagnosticLog.encodeLine(entry)
        val decoded = DiagnosticLog.decodeLine(encoded)

        assertEquals(entry, decoded)
    }

    @Test
    fun encodeAndDecodeWithNewlinesInMessage() {
        val entry = DiagnosticLog.LogEntry(
            timestamp = 1711500000000L,
            level = DiagnosticLog.LogLevel.ERROR,
            tag = "Tag",
            message = "line one\nline two\nline three",
        )

        val encoded = DiagnosticLog.encodeLine(entry)
        // Encoded line should not contain actual newlines
        assertTrue('\n' !in encoded, "Encoded line should not contain real newlines")

        val decoded = DiagnosticLog.decodeLine(encoded)
        assertEquals(entry, decoded)
    }

    @Test
    fun encodeAndDecodeWithBackslashesInMessage() {
        val entry = DiagnosticLog.LogEntry(
            timestamp = 1711500000000L,
            level = DiagnosticLog.LogLevel.WARN,
            tag = "Tag",
            message = "path\\to\\file",
        )

        val encoded = DiagnosticLog.encodeLine(entry)
        val decoded = DiagnosticLog.decodeLine(encoded)

        assertEquals(entry, decoded)
    }

    @Test
    fun encodeAndDecodeWithAllSpecialChars() {
        val entry = DiagnosticLog.LogEntry(
            timestamp = 1711500000000L,
            level = DiagnosticLog.LogLevel.ERROR,
            tag = "Tag|\\Special",
            message = "pipes | and\nnewlines\\and backslashes",
        )

        val encoded = DiagnosticLog.encodeLine(entry)
        val decoded = DiagnosticLog.decodeLine(encoded)

        assertEquals(entry, decoded)
    }

    @Test
    fun decodeReturnsNullForMalformedLine() {
        assertNull(DiagnosticLog.decodeLine(""))
        assertNull(DiagnosticLog.decodeLine("not-a-number|W|tag|msg"))
        assertNull(DiagnosticLog.decodeLine("123|X|tag|msg")) // invalid level
        assertNull(DiagnosticLog.decodeLine("123|W")) // too few parts
        assertNull(DiagnosticLog.decodeLine("just garbage"))
    }

    @Test
    fun decodeSkipsEmptyLines() {
        assertNull(DiagnosticLog.decodeLine(""))
        assertNull(DiagnosticLog.decodeLine("   "))
    }

    @Test
    fun encodeProducesExpectedFormat() {
        val entry = DiagnosticLog.LogEntry(
            timestamp = 1711500000000L,
            level = DiagnosticLog.LogLevel.WARN,
            tag = "Reconciler",
            message = "Budget tight",
        )

        val encoded = DiagnosticLog.encodeLine(entry)
        assertEquals("1711500000000|W|Reconciler|Budget tight", encoded)
    }

    @Test
    fun encodeErrorLevel() {
        val entry = DiagnosticLog.LogEntry(
            timestamp = 42L,
            level = DiagnosticLog.LogLevel.ERROR,
            tag = "DB",
            message = "fail",
        )

        val encoded = DiagnosticLog.encodeLine(entry)
        assertEquals("42|E|DB|fail", encoded)
    }
}
