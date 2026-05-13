// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.di

import me.juliana.hellomeds.ui.viewmodel.CmpMedicationDisplayFormatter
import me.juliana.hellomeds.ui.viewmodel.CmpStockDisplayFormatter
import me.juliana.hellomeds.ui.viewmodel.DebugViewModel
import me.juliana.hellomeds.ui.viewmodel.MedicationDisplayFormatter
import me.juliana.hellomeds.ui.viewmodel.StockDisplayFormatter
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Android-only DI module — platform formatters and Android-specific ViewModels.
 * Shared ViewModels are registered in sharedUiModule (sharedUi/commonMain).
 */
val viewModelModule = module {
    // Platform formatter implementations (shared CMP resources)
    single<MedicationDisplayFormatter> { CmpMedicationDisplayFormatter() }
    single<StockDisplayFormatter> { CmpStockDisplayFormatter() }

    // Android-only ViewModels
    viewModelOf(::DebugViewModel)
}
