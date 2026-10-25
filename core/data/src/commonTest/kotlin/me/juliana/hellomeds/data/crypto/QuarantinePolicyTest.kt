// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.crypto

import me.juliana.hellomeds.data.crypto.QuarantinePolicy.Action
import kotlin.test.Test
import kotlin.test.assertEquals

class QuarantinePolicyTest {

    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun readableOldDataAndEmptyNewDatabase_swapsBack() {
        assertEquals(Action.SWAP_BACK, QuarantinePolicy.decide(day, 0, quarantinedMedications = 3, liveMedications = 0))
    }

    @Test
    fun newDataExists_neverSwapsBack() {
        assertEquals(Action.KEEP, QuarantinePolicy.decide(day, 0, quarantinedMedications = 3, liveMedications = 1))
        assertEquals(Action.KEEP, QuarantinePolicy.decide(day, 0, quarantinedMedications = 3, liveMedications = null))
    }

    @Test
    fun unreadableOldData_isKeptUntilRetentionEnds() {
        assertEquals(Action.KEEP, QuarantinePolicy.decide(13 * day, 0, null, 0))
        assertEquals(Action.DELETE, QuarantinePolicy.decide(14 * day, 0, null, 0))
    }

    @Test
    fun emptyOldDatabase_isDeleted() {
        assertEquals(Action.DELETE, QuarantinePolicy.decide(day, 0, quarantinedMedications = 0, liveMedications = 0))
    }
}
