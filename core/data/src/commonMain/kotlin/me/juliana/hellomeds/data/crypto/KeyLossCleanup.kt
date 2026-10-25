// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.crypto

/** Removes a database that was set aside after its key was lost. Nothing is set aside on iOS. */
interface KeyLossCleanup {
    fun deleteQuarantine()
    fun deleteExpiredQuarantine()
}

object NoKeyLossCleanup : KeyLossCleanup {
    override fun deleteQuarantine() = Unit
    override fun deleteExpiredQuarantine() = Unit
}
