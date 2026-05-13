// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OperationGuardTest {

    // --- Double-tap prevention ---

    @Test
    fun tryAcquire_sameKeyTwice_secondCallReturnsFalse() {
        // Consequence of failure: DOUBLE DOSE. User taps "Take" twice rapidly,
        // both calls pass through, creating two history records and double stock decrement.
        val guard = OperationGuard()
        val key = "1_1_1742040000000"

        assertTrue(guard.tryAcquire(key), "First acquire should succeed")
        assertFalse(guard.tryAcquire(key), "Second acquire should fail (duplicate in flight)")
    }

    @Test
    fun release_allowsReacquire() {
        // Consequence of failure: After completing a dose log, the user can never
        // log the same dose again (e.g., after undo and re-take).
        val guard = OperationGuard()
        val key = "1_1_1742040000000"

        assertTrue(guard.tryAcquire(key))
        guard.release(key)
        assertTrue(guard.tryAcquire(key), "After release, re-acquire should succeed")
    }

    @Test
    fun differentKeys_independent() {
        // Consequence of failure: Marking med A as taken blocks marking med B,
        // causing a missed dose for med B.
        val guard = OperationGuard()
        val keyA = "1_1_1742040000000"
        val keyB = "2_2_1742040000000"

        assertTrue(guard.tryAcquire(keyA), "Key A should acquire")
        assertTrue(guard.tryAcquire(keyB), "Key B should also acquire (independent)")
    }

    @Test
    fun withGuard_executesBlockOnce() {
        // Consequence of failure: Block not executed, dose not logged.
        val guard = OperationGuard()
        var callCount = 0

        val result = guard.withGuard("key") {
            callCount++
            "done"
        }

        assertEquals(1, callCount)
        assertEquals("done", result)
    }

    @Test
    fun withGuard_secondCallWhileFirstInFlight_returnsNull() {
        // Consequence of failure: DOUBLE DOSE from concurrent execution.
        val guard = OperationGuard()
        val key = "1_1_1742040000000"

        // Simulate first call still in flight
        guard.tryAcquire(key)

        val result = guard.withGuard(key) { "should not run" }

        assertNull(result, "Second withGuard should return null (first still in flight)")
    }

    @Test
    fun withGuard_releasesOnException() {
        // Consequence of failure: After an error, the guard is permanently locked
        // and the user can never retry logging that dose.
        val guard = OperationGuard()
        val key = "1_1_1742040000000"

        try {
            guard.withGuard(key) { error("simulated failure") }
        } catch (_: IllegalStateException) {
            // expected
        }

        // Should be released despite the exception
        assertTrue(guard.tryAcquire(key), "Guard should be released after exception")
    }
}
