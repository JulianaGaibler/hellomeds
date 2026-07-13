// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.theme

import kotlin.test.Test
import kotlin.test.assertSame

class MedicationColorTest {

    // Guards persistence: `Medication.shapeColor` stores `MedicationColor.name`,
    // and read paths resolve it back via `fromName`. If either side drifts (typo,
    // someone reintroduces a `::class.simpleName` pattern that R8 can obfuscate),
    // saved colors will silently revert to "no color" in release builds.
    @Test
    fun nameRoundTripsThroughFromName() {
        MedicationColor.all.forEach { color ->
            assertSame(
                color,
                MedicationColor.fromName(color.name),
                "fromName(${color.name}) did not return the same instance",
            )
        }
    }
}
