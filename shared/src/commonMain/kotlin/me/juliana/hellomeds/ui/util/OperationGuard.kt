// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

/**
 * Prevents duplicate concurrent operations on the same key.
 * Used to guard against double-tap dose logging (take/skip).
 *
 * Designed for main-thread use (UI events): all acquire/release calls
 * must happen on the same thread (e.g., the main/UI thread).
 */
class OperationGuard {
    private val activeKeys = mutableSetOf<String>()

    /**
     * Try to acquire the guard for [key].
     * Returns true if acquired (no other operation in flight for this key),
     * false if another operation is already in progress.
     */
    fun tryAcquire(key: String): Boolean = activeKeys.add(key)

    /**
     * Release the guard for [key].
     */
    fun release(key: String) {
        activeKeys.remove(key)
    }

    /**
     * Execute [block] only if no other operation is in flight for [key].
     * Automatically releases the guard when done (including on exception).
     * Returns the result of [block], or null if the guard was not acquired.
     */
    inline fun <T> withGuard(key: String, block: () -> T): T? {
        if (!tryAcquire(key)) return null
        return try {
            block()
        } finally {
            release(key)
        }
    }
}
