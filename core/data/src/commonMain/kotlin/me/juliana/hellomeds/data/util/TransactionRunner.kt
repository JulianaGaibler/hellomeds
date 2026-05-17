// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.util

import androidx.room.RoomDatabase

/**
 * Abstracts "run this block inside one atomic write transaction".
 *
 * Production wires a [RoomTransactionRunner] backed by [RoomDatabase]; tests
 * substitute a no-op runner so they don't need to spin up Room's runtime
 * (which requires the Room builder to initialize `coroutineScope`).
 */
interface TransactionRunner {
    suspend fun <R> run(block: suspend () -> R): R
}

class RoomTransactionRunner(private val database: RoomDatabase) : TransactionRunner {
    override suspend fun <R> run(block: suspend () -> R): R = database.performTransaction(block)
}
