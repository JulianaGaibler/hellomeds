// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class DiagnosticLogPersistenceTest {

    private lateinit var logFile: File

    @Before
    fun setup() {
        DiagnosticLog.clear()
        logFile = File.createTempFile("diagnostic_test_", ".log")
        logFile.writeText("") // start empty
    }

    @After
    fun teardown() {
        DiagnosticLog.verbose = false
        DiagnosticLog.clear()
        logFile.delete()
    }

    /** Wait for async file I/O to flush. */
    private fun awaitFlush() = runBlocking { delay(200) }

    @Test
    fun `entries are persisted to file after add`() {
        DiagnosticLog.configure(logFile.absolutePath)
        awaitFlush()

        DiagnosticLog.w("Tag1", "warning one")
        DiagnosticLog.e("Tag2", "error one")
        awaitFlush()

        val lines = logFile.readText().trim().lines().filter { it.isNotBlank() }
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("|W|Tag1|warning one"))
        assertTrue(lines[1].contains("|E|Tag2|error one"))
    }

    @Test
    fun `entries survive clear and reconfigure (simulating app restart)`() {
        DiagnosticLog.configure(logFile.absolutePath)
        awaitFlush()

        DiagnosticLog.w("Tag", "before restart")
        DiagnosticLog.e("Tag", "also before restart")
        awaitFlush()

        // Simulate app restart: clear in-memory, reconfigure from same file
        DiagnosticLog.clear()
        awaitFlush()
        // clear() writes empty string to file, so we need to write back the entries manually
        // Actually, let's test the real flow: write entries, then "restart" without clearing the file
        // Reset: write entries fresh
        DiagnosticLog.configure(logFile.absolutePath)
        awaitFlush()

        DiagnosticLog.w("Tag", "persistent entry")
        awaitFlush()

        // Now simulate restart by creating a new log file path scenario:
        // Re-read what's on disk
        val fileContent = logFile.readText().trim()
        assertTrue(fileContent.contains("persistent entry"))

        // Clear in-memory only (don't touch file — simulating process death)
        // We can't truly clear without touching file via public API,
        // so instead verify the file has content and reconfigure loads it
        val tempFile2 = File.createTempFile("diagnostic_restart_", ".log")
        try {
            tempFile2.writeText(fileContent)

            // Clear in-memory state
            DiagnosticLog.clear()
            awaitFlush()
            assertTrue(DiagnosticLog.getEntries().isEmpty())

            // Reconfigure from the saved file (simulates app restart)
            DiagnosticLog.configure(tempFile2.absolutePath)
            awaitFlush()

            val restored = DiagnosticLog.getEntries()
            assertTrue(restored.isNotEmpty())
            assertTrue(restored.any { it.message == "persistent entry" })
        } finally {
            tempFile2.delete()
        }
    }

    @Test
    fun `configure loads existing entries from file`() {
        // Pre-populate the file with encoded entries
        val lines = listOf(
            "${System.currentTimeMillis()}|W|OldTag|loaded from file",
            "${System.currentTimeMillis()}|E|OldTag|error from file",
        )
        logFile.writeText(lines.joinToString("\n"))

        DiagnosticLog.configure(logFile.absolutePath)
        awaitFlush()

        val entries = DiagnosticLog.getEntries()
        assertEquals(2, entries.size)
        assertEquals("loaded from file", entries[0].message)
        assertEquals("error from file", entries[1].message)
        assertEquals(DiagnosticLog.LogLevel.WARN, entries[0].level)
        assertEquals(DiagnosticLog.LogLevel.ERROR, entries[1].level)
    }

    @Test
    fun `configure prunes entries older than 48h`() {
        val now = System.currentTimeMillis()
        val old = now - 172_800_000L - 60_000 // 48h + 1 min ago
        val recent = now - 1000 // 1 second ago

        val lines = listOf(
            "$old|W|Tag|stale entry",
            "$recent|E|Tag|fresh entry",
        )
        logFile.writeText(lines.joinToString("\n"))

        DiagnosticLog.configure(logFile.absolutePath)
        awaitFlush()

        val entries = DiagnosticLog.getEntries()
        assertEquals(1, entries.size)
        assertEquals("fresh entry", entries[0].message)
    }

    @Test
    fun `malformed lines in file are skipped without crashing`() {
        val now = System.currentTimeMillis()
        val lines = listOf(
            "garbage line",
            "not-a-number|W|tag|msg",
            "$now|X|tag|msg", // invalid level
            "$now|W|ValidTag|valid entry",
            "", // empty line
            "$now|E|ValidTag|another valid entry",
        )
        logFile.writeText(lines.joinToString("\n"))

        DiagnosticLog.configure(logFile.absolutePath)
        awaitFlush()

        val entries = DiagnosticLog.getEntries()
        assertEquals(2, entries.size)
        assertEquals("valid entry", entries[0].message)
        assertEquals("another valid entry", entries[1].message)
    }

    @Test
    fun `empty file does not crash on configure`() {
        logFile.writeText("")

        DiagnosticLog.configure(logFile.absolutePath)
        awaitFlush()

        assertTrue(DiagnosticLog.getEntries().isEmpty())
    }

    @Test
    fun `nonexistent file does not crash on configure`() {
        val missing = File(logFile.parent, "does_not_exist.log")

        DiagnosticLog.configure(missing.absolutePath)
        awaitFlush()

        assertTrue(DiagnosticLog.getEntries().isEmpty())
        missing.delete() // cleanup in case it was created
    }

    @Test
    fun `pipes and newlines in messages survive persistence roundtrip`() {
        DiagnosticLog.configure(logFile.absolutePath)
        awaitFlush()

        DiagnosticLog.e("Tag", "pipes | in | message\nand newlines")
        awaitFlush()

        // Clear and reload from file
        val fileContent = logFile.readText()
        val tempFile = File.createTempFile("diagnostic_roundtrip_", ".log")
        try {
            tempFile.writeText(fileContent)
            DiagnosticLog.clear()
            awaitFlush()

            DiagnosticLog.configure(tempFile.absolutePath)
            awaitFlush()

            val entries = DiagnosticLog.getEntries()
            assertEquals(1, entries.size)
            assertEquals("pipes | in | message\nand newlines", entries[0].message)
        } finally {
            tempFile.delete()
        }
    }

    // --- Verbose mode ---

    @Test
    fun `verbose mode persists debug and info entries`() {
        DiagnosticLog.verbose = true
        DiagnosticLog.configure(logFile.absolutePath)
        awaitFlush()

        DiagnosticLog.d("Tag1", "debug message")
        DiagnosticLog.i("Tag2", "info message")
        DiagnosticLog.w("Tag3", "warning message")
        awaitFlush()

        val entries = DiagnosticLog.getEntries()
        assertEquals(3, entries.size)
        assertEquals(DiagnosticLog.LogLevel.DEBUG, entries[0].level)
        assertEquals(DiagnosticLog.LogLevel.INFO, entries[1].level)
        assertEquals(DiagnosticLog.LogLevel.WARN, entries[2].level)

        // Verify file persistence includes D/I
        val fileContent = logFile.readText()
        assertTrue(fileContent.contains("|D|Tag1|debug message"))
        assertTrue(fileContent.contains("|I|Tag2|info message"))
    }

    @Test
    fun `non-verbose mode does not persist debug or info entries`() {
        DiagnosticLog.verbose = false
        DiagnosticLog.configure(logFile.absolutePath)
        awaitFlush()

        DiagnosticLog.d("Tag1", "debug message")
        DiagnosticLog.i("Tag2", "info message")
        DiagnosticLog.w("Tag3", "warning message")
        awaitFlush()

        val entries = DiagnosticLog.getEntries()
        assertEquals(1, entries.size)
        assertEquals(DiagnosticLog.LogLevel.WARN, entries[0].level)
    }

    @Test
    fun `verbose mode D and I entries survive file roundtrip`() {
        DiagnosticLog.verbose = true
        DiagnosticLog.configure(logFile.absolutePath)
        awaitFlush()

        DiagnosticLog.d("Sched", "alarm set at 1234")
        DiagnosticLog.i("Sched", "reconcile complete")
        awaitFlush()

        // Simulate restart: clear in-memory, reconfigure from file
        val tempFile = File.createTempFile("diagnostic_roundtrip_", ".log")
        try {
            logFile.copyTo(tempFile, overwrite = true)
            DiagnosticLog.clear()
            awaitFlush()
            assertTrue(DiagnosticLog.getEntries().isEmpty())

            DiagnosticLog.configure(tempFile.absolutePath)
            awaitFlush()

            val restored = DiagnosticLog.getEntries()
            assertEquals(2, restored.size)
            assertEquals(DiagnosticLog.LogLevel.DEBUG, restored[0].level)
            assertEquals("alarm set at 1234", restored[0].message)
            assertEquals(DiagnosticLog.LogLevel.INFO, restored[1].level)
        } finally {
            tempFile.delete()
        }
    }
}
