// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.di

import me.juliana.hellomeds.ui.util.StockGraphBuilder
import me.juliana.hellomeds.ui.viewmodel.AutoBackupViewModel
import me.juliana.hellomeds.ui.viewmodel.BackupViewModel
import me.juliana.hellomeds.ui.viewmodel.ImportanceLabelViewModel
import me.juliana.hellomeds.ui.viewmodel.MedicationViewModel
import me.juliana.hellomeds.ui.viewmodel.ScheduleViewModel
import me.juliana.hellomeds.ui.viewmodel.StockTrackingViewModel
import me.juliana.hellomeds.ui.viewmodel.SupportViewModel
import me.juliana.hellomeds.ui.viewmodel.TrackingViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Shared UI DI module — ViewModels and UI services that work cross-platform.
 * Platform-specific formatters and Android-only ViewModels are bound in the app module.
 */
val sharedUiModule = module {
    single { StockGraphBuilder(get(), get(), get()) }

    viewModelOf(::MedicationViewModel)
    viewModelOf(::ScheduleViewModel)
    viewModelOf(::TrackingViewModel)
    viewModelOf(::ImportanceLabelViewModel)
    viewModelOf(::StockTrackingViewModel)
    viewModelOf(::BackupViewModel)
    viewModelOf(::AutoBackupViewModel)
    viewModelOf(::SupportViewModel)
}
