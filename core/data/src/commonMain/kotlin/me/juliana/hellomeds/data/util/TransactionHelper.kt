// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.util

import androidx.room.RoomDatabase
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/**
 * Run [block] inside a real Room KMP IMMEDIATE write transaction.
 *
 * Replaces the previous no-op implementation that silently broke atomicity for every
 * caller — including dose-log writes and backup imports. Uses the Room 2.7+ direct
 * connection API: `useWriterConnection { it.immediateTransaction { block() } }`.
 *
 * Semantics:
 * - Room serializes writers (one writer connection per database), so concurrent calls
 *   from different coroutines wait their turn rather than racing.
 * - An exception thrown inside [block] rolls back the transaction; normal completion commits.
 * - Nested `performTransaction` calls (e.g. via inner repository methods) reuse the
 *   confined writer connection and create a SAVEPOINT under the parent — the inner
 *   transaction type is ignored, which is the correct semantics for nested writes.
 */
suspend fun <R> RoomDatabase.performTransaction(block: suspend () -> R): R = useWriterConnection { transactor ->
    transactor.immediateTransaction {
        block()
    }
}
