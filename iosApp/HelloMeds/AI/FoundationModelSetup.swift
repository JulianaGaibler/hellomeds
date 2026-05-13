// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

import Foundation
import shared // KMP shared framework

/// Registers the Apple Foundation Models callback with the Kotlin shared framework.
/// Called during app initialization from ContentView.
func setupFoundationModelBridge() {
    let isAvailable = FoundationModelBridge.isAvailable()

    if isAvailable {
        FoundationModelCallbackKt.registerFoundationModelCallback(
            isAvailable: true,
            callback: { ocrText, completion in
                FoundationModelBridge.analyzeMedicationText(ocrText) { result in
                    // K/N exports data class as FoundationModelResult (swift_name)
                    // Double? maps to KotlinDouble? in Swift
                    let strengthVal: KotlinDouble? = result.strengthValue.map { KotlinDouble(value: $0.doubleValue) }

                    let kotlinResult = FoundationModelResult(
                        names: result.names.map { $0 as String },
                        type: result.type,
                        strengthValue: strengthVal,
                        strengthUnit: result.strengthUnit,
                        success: result.success
                    )
                    let _ = completion(kotlinResult)
                }
            }
        )
        print("Foundation Models bridge registered (available)")
    } else {
        FoundationModelCallbackKt.registerFoundationModelCallback(
            isAvailable: false,
            callback: nil
        )
        print("Foundation Models bridge registered (not available on this device)")
    }
}
