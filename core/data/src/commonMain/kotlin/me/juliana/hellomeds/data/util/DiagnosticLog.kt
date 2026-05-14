// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.time.Clock

/**
 * Ring buffer for recent log entries, with optional file-backed persistence.
 * Included in bug reports to provide temporal context for transient issues.
 *
 * Always captures W and E levels. D and I levels are only captured when [verbose] is true
 * (intended for debug builds — scheduling decisions, alarm API choices, time deltas).
 *
 * In-memory updates are synchronous (@Volatile + copy-on-write) for immediate reads.
 * File I/O is deferred to a background coroutine to avoid main-thread jank.
 * No PII should be logged (use tags and generic messages).
 *
 * Call [configure] at app startup with a platform-specific file path to enable persistence.
 * Without it, the log is in-memory only (entries lost on restart).
 */
object DiagnosticLog {
    private const val MAX_ENTRIES = 200
    private const val VERBOSE_MAX_ENTRIES = 2000
    internal const val RETENTION_MS = 172_800_000L // 48 hours

    /** When true, D/I levels are persisted alongside W/E. Set to true for debug builds. */
    @Volatile
    var verbose: Boolean = false

    private val effectiveMaxEntries: Int get() = if (verbose) VERBOSE_MAX_ENTRIES else MAX_ENTRIES
    private val effectivePruneThreshold: Int get() = (effectiveMaxEntries * 1.2).toInt()

    // In-memory: @Volatile + copy-on-write for fast lock-free reads.
    // Tiny race window on concurrent add() is acceptable for a diagnostic log.
    @Volatile
    private var entries: List<LogEntry> = emptyList()

    // File I/O runs on background scope with Mutex to avoid main-thread jank
    private val ioScope = CoroutineScope(Dispatchers.Default)
    private val ioMutex = Mutex()

    @Volatile
    private var filePath: String? = null

    /**
     * Enables file-backed persistence. Call once at app startup with a platform path.
     * Loads existing entries from the file, prunes entries older than 48h, and rewrites the file.
     */
    fun configure(path: String) {
        filePath = path
        ioScope.launch {
            ioMutex.withLock {
                loadFromFile(path)
                pruneOldEntries(path)
            }
        }
    }

    fun d(tag: String, message: String) {
        if (verbose) add(LogLevel.DEBUG, tag, message)
    }

    fun i(tag: String, message: String) {
        if (verbose) add(LogLevel.INFO, tag, message)
    }

    fun w(tag: String, message: String) {
        add(LogLevel.WARN, tag, message)
    }

    fun e(tag: String, message: String) {
        add(LogLevel.ERROR, tag, message)
    }

    fun getEntries(sinceTimestamp: Long = 0): List<LogEntry> {
        val snapshot = entries
        return if (sinceTimestamp > 0) {
            snapshot.filter { it.timestamp >= sinceTimestamp }
        } else {
            snapshot
        }
    }

    fun clear() {
        entries = emptyList()
        val path = filePath ?: return
        ioScope.launch {
            ioMutex.withLock {
                try {
                    writeDiagnosticLogFile(path, "")
                } catch (_: Exception) {
                    // Non-fatal
                }
            }
        }
    }

    private fun add(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = Clock.System.now().toEpochMilliseconds(),
            level = level,
            tag = tag,
            message = message,
        )

        // Synchronous in-memory update (immediate for readers)
        val maxEntries = effectiveMaxEntries
        val current = entries.toMutableList()
        while (current.size >= maxEntries) {
            current.removeAt(0)
        }
        current.add(entry)
        entries = current

        // Async file persistence
        val path = filePath ?: return
        ioScope.launch {
            ioMutex.withLock {
                try {
                    if (current.size > effectivePruneThreshold) {
                        pruneOldEntries(path)
                    } else {
                        appendDiagnosticLogFile(path, encodeLine(entry))
                    }
                } catch (_: Exception) {
                    // File I/O failure is non-fatal for a diagnostic log
                }
            }
        }
    }

    // --- File I/O (runs under ioMutex on background scope) ---

    private fun loadFromFile(path: String) {
        val content = try {
            readDiagnosticLogFile(path)
        } catch (_: Exception) {
            null
        }
        if (content.isNullOrBlank()) return

        val loaded = mutableListOf<LogEntry>()
        for (line in content.lines()) {
            if (line.isBlank()) continue
            try {
                decodeLine(line)?.let { loaded.add(it) }
            } catch (_: Exception) {
                // Skip malformed lines (e.g. crash mid-write)
            }
        }

        // Merge: file entries first (older), then any in-memory entries added before configure()
        val existingTimestamps = loaded.map { it.timestamp }.toSet()
        val newInMemory = entries.filter { it.timestamp !in existingTimestamps }
        loaded.addAll(newInMemory)
        loaded.sortBy { it.timestamp }

        // Trim to effective max
        val maxEntries = effectiveMaxEntries
        while (loaded.size > maxEntries) {
            loaded.removeAt(0)
        }

        entries = loaded
    }

    private fun pruneOldEntries(path: String) {
        val cutoff = Clock.System.now().toEpochMilliseconds() - RETENTION_MS
        val pruned = entries.filter { it.timestamp >= cutoff }.toMutableList()

        while (pruned.size > effectiveMaxEntries) {
            pruned.removeAt(0)
        }

        entries = pruned
        rewriteFile(path)
    }

    private fun rewriteFile(path: String) {
        try {
            val content = entries.joinToString("\n") { encodeLine(it) }
            writeDiagnosticLogFile(path, content)
        } catch (_: Exception) {
            // Non-fatal
        }
    }

    // --- Line encoding/decoding ---
    // Format: timestamp|W|tag|message
    // Pipes in tag/message escaped as \|, newlines as \n, backslashes as \\

    internal fun encodeLine(entry: LogEntry): String {
        val levelChar = when (entry.level) {
            LogLevel.DEBUG -> "D"
            LogLevel.INFO -> "I"
            LogLevel.WARN -> "W"
            LogLevel.ERROR -> "E"
        }
        return "${entry.timestamp}|$levelChar|${escapeField(entry.tag)}|${escapeField(entry.message)}"
    }

    internal fun decodeLine(line: String): LogEntry? {
        // Split on first 3 unescaped pipes: timestamp|level|tag|rest-is-message
        val parts = splitUnescapedPipes(line)
        if (parts.size < 4) return null

        val timestamp = parts[0].toLongOrNull() ?: return null
        val level = when (parts[1]) {
            "D" -> LogLevel.DEBUG
            "I" -> LogLevel.INFO
            "W" -> LogLevel.WARN
            "E" -> LogLevel.ERROR
            else -> return null
        }
        val tag = unescapeField(parts[2])
        val message = unescapeField(parts[3])
        return LogEntry(timestamp, level, tag, message)
    }

    private fun escapeField(value: String): String =
        value.replace("\\", "\\\\").replace("|", "\\|").replace("\n", "\\n")

    private fun unescapeField(value: String): String {
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            if (value[i] == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    '\\' -> sb.append('\\')
                    '|' -> sb.append('|')
                    'n' -> sb.append('\n')
                    else -> {
                        sb.append('\\')
                        sb.append(value[i + 1])
                    }
                }
                i += 2
            } else {
                sb.append(value[i])
                i++
            }
        }
        return sb.toString()
    }

    private fun splitUnescapedPipes(line: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < line.length) {
            when {
                line[i] == '\\' && i + 1 < line.length -> {
                    current.append(line[i])
                    current.append(line[i + 1])
                    i += 2
                }
                line[i] == '|' && parts.size < 3 -> {
                    // Only split on first 3 pipes (timestamp|level|tag|rest-is-message)
                    parts.add(current.toString())
                    current.clear()
                    i++
                }
                else -> {
                    current.append(line[i])
                    i++
                }
            }
        }
        parts.add(current.toString())
        return parts
    }

    data class LogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val tag: String,
        val message: String,
    )

    enum class LogLevel { DEBUG, INFO, WARN, ERROR }
}
