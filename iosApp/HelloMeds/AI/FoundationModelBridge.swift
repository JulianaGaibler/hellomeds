// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

import Foundation
import FoundationModels

/// Result type bridged to Kotlin via ObjC interop
@objc public class MedicationAIResult: NSObject {
    @objc public let names: [String]
    @objc public let type: String?
    @objc public let strengthValue: NSNumber?
    @objc public let strengthUnit: String?
    @objc public let success: Bool

    init(names: [String], type: String?, strengthValue: Double?, strengthUnit: String?) {
        self.names = names
        self.type = type
        self.strengthValue = strengthValue.map { NSNumber(value: $0) }
        self.strengthUnit = strengthUnit
        self.success = !names.isEmpty
    }

    @objc public static func empty() -> MedicationAIResult {
        return MedicationAIResult(names: [], type: nil, strengthValue: nil, strengthUnit: nil)
    }
}

/// Structured output type for guided generation
@available(iOS 26.0, *)
@Generable
struct MedicationInfo {
    /// Up to 4 distinct medication names (generic, brand, common)
    var names: [String]
    /// Medication form type (capsule, tablet, liquid, etc.)
    var type: String?
    /// Dosage strength as a number
    var strengthValue: Double?
    /// Dosage unit (mg, mcg, g, mL, IU, mEq, %)
    var strengthUnit: String?
}

/// Bridge class accessible from Kotlin via ObjC interop.
/// Wraps Apple's Foundation Models framework for on-device medication text extraction.
@objc public class FoundationModelBridge: NSObject {

    @objc public static func isAvailable() -> Bool {
        if #available(iOS 26.0, *) {
            return SystemLanguageModel.default.availability == .available
        }
        return false
    }

    /// Analyze OCR text using the on-device Foundation Model.
    /// Calls the completion handler on the main thread with the result.
    @objc public static func analyzeMedicationText(
        _ ocrText: String,
        completion: @escaping (MedicationAIResult) -> Void
    ) {
        guard #available(iOS 26.0, *),
              SystemLanguageModel.default.availability == .available else {
            DispatchQueue.main.async {
                completion(MedicationAIResult.empty())
            }
            return
        }

        Task {
            do {
                let session = LanguageModelSession()

                let prompt = """
                Extract medication information from this OCR text from a medication label.
                Return the medication name(s), type/form, strength value, and unit.

                OCR text: "\(ocrText)"

                Rules:
                - names: up to 4 distinct names (generic name, brand name)
                - type: one of: capsule, tablet, liquid, topical, cream, drops, foam, gel, inhaler, injection, lotion, ointment, patch, powder, spray, suppository
                - strengthValue: numeric dosage value (e.g., 100, 500, 0.5)
                - strengthUnit: one of: mg, mcg, g, mL, IU, mEq, %
                """

                let response = try await session.respond(
                    to: prompt,
                    generating: MedicationInfo.self
                )

                let info = response.content
                let result = MedicationAIResult(
                    names: info.names.filter { !$0.isEmpty },
                    type: info.type,
                    strengthValue: info.strengthValue,
                    strengthUnit: info.strengthUnit
                )

                DispatchQueue.main.async {
                    completion(result)
                }
            } catch {
                print("Foundation Models error: \(error)")
                DispatchQueue.main.async {
                    completion(MedicationAIResult.empty())
                }
            }
        }
    }
}
