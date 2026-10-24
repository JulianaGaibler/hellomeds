// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.model.enums

/** What `UnloggedDoseResolver` records for a dose of this medication that was never logged. */
enum class UnloggedDoseAction {
    /** Record the dose as `AUTO_SKIPPED`. */
    SKIP,

    /** Record the dose as `AUTO_TAKEN` and deduct stock. */
    TAKE,
}
