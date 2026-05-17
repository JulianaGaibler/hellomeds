// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.domain.ml

import me.juliana.hellomeds.data.model.enums.MedicationStrengthUnit
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.data.util.AppLogger
import me.juliana.hellomeds.domain.ml.MedicationDictionaries.ALLOWED_UNITS
import me.juliana.hellomeds.domain.ml.MedicationDictionaries.KNOWN_MEDICATIONS
import me.juliana.hellomeds.domain.ml.MedicationDictionaries.MAX_NAME_SUGGESTIONS
import me.juliana.hellomeds.domain.ml.MedicationDictionaries.MAX_TYPE_SUGGESTIONS
import me.juliana.hellomeds.domain.ml.MedicationDictionaries.MEDICATION_SUFFIXES
import me.juliana.hellomeds.domain.ml.MedicationDictionaries.MIN_TOKEN_LENGTH
import me.juliana.hellomeds.domain.ml.MedicationDictionaries.PENALIZED_WORDS
import me.juliana.hellomeds.domain.validation.StringSimilarity
import kotlin.math.abs

private const val TAG = "HeuristicMedicationEngine"

/**
 * Pure-Kotlin heuristic medication detection engine.
 *
 * This fallback system is used when on-device AI (Gemini Nano / Apple Intelligence)
 * is unavailable. It employs:
 * - Multi-factor scoring for medication name detection
 * - Fuzzy string matching for OCR error tolerance
 * - Pattern recognition for pharmaceutical naming conventions
 * - Regex-based strength/dosage extraction
 */
class HeuristicMedicationEngine : MedicationIntelligenceEngine {

    /**
     * Main entry point: Create medication detection result from OCR text.
     *
     * @param ocrText Raw OCR text from medication label
     * @return MedicationDetectionResult with detected names, types, and strength
     */
    override suspend fun guessMedicationDetails(ocrText: String): MedicationDetectionResult {
        AppLogger.d(TAG, "Running heuristic engine on text: ${ocrText.take(100)}...")

        val nameSuggestions = extractNameSuggestions(ocrText)
        val typeSuggestions = extractTypeSuggestions(ocrText)
        val strengthSuggestion = extractStrengthSuggestion(ocrText, nameSuggestions)

        AppLogger.d(
            TAG,
            "Heuristic results: ${nameSuggestions.size} names, ${typeSuggestions.size} types, strength=${strengthSuggestion != null}",
        )

        return MedicationDetectionResult(
            nameSuggestions = nameSuggestions,
            typeSuggestions = typeSuggestions,
            strengthSuggestion = strengthSuggestion,
            usedAI = false,
        )
    }

    /**
     * Extracts medication name candidates from OCR text using multi-factor token scoring.
     *
     * @param text OCR text to analyze
     * @return Up to MAX_NAME_SUGGESTIONS candidates, sorted by score descending
     */
    private fun extractNameSuggestions(text: String): List<String> {
        AppLogger.d(TAG, "  Extracting name suggestions from OCR text...")

        val normalized = normalizeOCR(text)
        AppLogger.d(
            TAG,
            "    Normalized: ${normalized.take(100)}${if (normalized.length > 100) "..." else ""}",
        )

        val tokens = preprocessTokens(normalized)
        AppLogger.d(TAG, "    Tokens (>=$MIN_TOKEN_LENGTH chars): ${tokens.joinToString(", ")}")

        // Bigrams catch compound names like "vitamin d3" where d3 alone is below MIN_TOKEN_LENGTH.
        val bigrams = generateBigrams(normalized)
        if (bigrams.isNotEmpty()) {
            AppLogger.d(TAG, "    Bigrams: ${bigrams.joinToString(", ")}")
        }

        val candidates = tokens + bigrams
        AppLogger.d(TAG, "    Scoring ${candidates.size} candidates...")

        val scoredTokens = candidates
            .map { token -> token to scoreToken(token) }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }
            .distinctBy { (token, _) -> token }
            .take(MAX_NAME_SUGGESTIONS)
            .also { scored ->
                if (scored.isNotEmpty()) {
                    AppLogger.d(TAG, "  Final name rankings:")
                    scored.forEachIndexed { index, (token, score) ->
                        AppLogger.d(TAG, "    ${index + 1}. '$token' (score=$score)")
                    }
                } else {
                    AppLogger.d(TAG, "  No name candidates with positive scores found")
                }
            }
            .map { (token, _) -> cleanAndCapitalize(token) }
            .filter { it.isNotEmpty() }

        return scoredTokens
    }

    /**
     * Clean and capitalize medication name.
     * Removes special characters at start/end and applies normalization:
     * - Words >3 chars that are ALL CAPS -> Title Case
     * - Other words -> Title Case
     *
     * @param name Raw medication name
     * @return Cleaned and capitalized name
     */
    private fun cleanAndCapitalize(name: String): String {
        // Remove special characters from start and end
        val cleaned = name.trim().trimStart { !it.isLetterOrDigit() }.trimEnd { !it.isLetterOrDigit() }

        // Apply capitalization normalization
        return cleaned.split(" ")
            .joinToString(" ") { word ->
                if (word.length > 3 && word.all { it.isUpperCase() || !it.isLetter() }) {
                    // Word is >3 chars and ALL CAPS -> convert to Title Case
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                } else {
                    // Apply standard title case
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }
    }

    /**
     * Score a single token for likelihood of being a medication name.
     *
     * Scoring system:
     * - -10: Penalized word (tablet, capsule, etc.)
     * - -5: Has special characters at start or end (likely OCR error)
     * - +10: Exact match to known medication
     * - +7: Fuzzy match to known medication (Levenshtein <=2)
     * - +6: Ends with medication suffix and has sufficient length
     * - +1-4: Chemical-looking characteristics
     *
     * @param token Normalized token to score
     * @return Score (higher = more likely to be medication name)
     */
    private fun scoreToken(token: String): Int {
        val scoreBreakdown = mutableListOf<String>()
        var score = 0

        // Heavy penalty for non-medication words
        if (PENALIZED_WORDS.contains(token)) {
            AppLogger.d(TAG, "    Token '$token': penalized word -> score=-10")
            return -10
        }

        // Penalize tokens with special characters at start or end (likely OCR errors)
        if (token.isNotEmpty() && (
                !token.first().isLetterOrDigit() || !token.last()
                    .isLetterOrDigit()
                )
        ) {
            score -= 5
            scoreBreakdown.add("special_chars(-5)")
        }

        // Check against known medications
        val knownMedScore = scoreKnownMedication(token)
        if (knownMedScore > 0) {
            score += knownMedScore
            scoreBreakdown.add(if (knownMedScore == 10) "exact_match(+10)" else "fuzzy_match(+7)")
        }

        // Check for pharmaceutical suffixes
        val suffixScore = scoreSuffix(token)
        if (suffixScore > 0) {
            score += suffixScore
            scoreBreakdown.add("suffix(+6)")
        }

        // Check for chemical-looking characteristics
        val chemicalScore = scoreChemicalLooking(token)
        if (chemicalScore > 0) {
            score += chemicalScore
            scoreBreakdown.add("chemical(+$chemicalScore)")
        }

        if (score > 0) {
            AppLogger.d(TAG, "    Token '$token': ${scoreBreakdown.joinToString(" + ")} -> score=$score")
        }

        return score
    }

    /**
     * Score based on known medication matching.
     *
     * @return +10 for exact match, +7 for fuzzy match, 0 otherwise
     */
    private fun scoreKnownMedication(token: String): Int {
        // Exact match
        if (KNOWN_MEDICATIONS.contains(token)) {
            return 10
        }

        // Fuzzy match (tolerates OCR errors)
        for (medication in KNOWN_MEDICATIONS) {
            if (StringSimilarity.fuzzyMatch(token, medication, maxDistance = 2)) {
                return 7
            }
        }

        return 0
    }

    /**
     * Score based on pharmaceutical suffix patterns.
     *
     * Many medications follow naming conventions where drugs in the same class
     * share a common suffix (e.g., -pril for ACE inhibitors, -statin for statins).
     *
     * @return +6 if ends with recognized suffix and has sufficient prefix, 0 otherwise
     */
    private fun scoreSuffix(token: String): Int {
        for (suffix in MEDICATION_SUFFIXES) {
            // Must have at least 3 characters before the suffix
            if (token.endsWith(suffix) && token.length >= suffix.length + 3) {
                return 6
            }
        }
        return 0
    }

    /**
     * Score based on chemical-looking characteristics.
     *
     * Pharmaceutical names often have patterns that distinguish them from common words:
     * - Longer length (7+ characters)
     * - Uncommon letters (x, z)
     * - Chemical-sounding patterns (ph, th, yl, mn)
     * - Generic endings (ine, one, ide, ate)
     *
     * @return Score 0-4 based on number of characteristics present
     */
    private fun scoreChemicalLooking(token: String): Int {
        var score = 0

        // Longer names are more likely to be pharmaceutical
        if (token.length >= 7) {
            score += 1
        }

        // Uncommon letters suggest pharmaceutical names
        if (token.contains(Regex("[xz]"))) {
            score += 1
        }

        // Chemical-sounding patterns
        if (token.contains(Regex("ph|th|yl|mn"))) {
            score += 1
        }

        // Generic endings common in pharmaceutical names
        if (token.matches(Regex(".*(?:ine|one|ide|ate)$"))) {
            score += 1
        }

        return score
    }

    /**
     * Extract medication type suggestions (dosage forms).
     *
     * Uses text matching with fuzzy tolerance for OCR errors.
     * Prioritizes exact matches over fuzzy matches.
     *
     * @param text OCR text to analyze
     * @return List of up to MAX_TYPE_SUGGESTIONS medication types, sorted by match quality
     */
    private fun extractTypeSuggestions(text: String): List<MedicationType> {
        val textLower = text.lowercase()
        val exactMatches = mutableListOf<MedicationType>()
        val fuzzyMatches = mutableListOf<MedicationType>()

        // Iterate through all MedicationType enum values
        for (typeEnum in MedicationType.entries) {
            val typeValue = typeEnum.value

            // Exact match (case-insensitive) - higher priority
            if (textLower.contains(typeValue)) {
                exactMatches.add(typeEnum)
                continue
            }

            // Fuzzy match for OCR errors (e.g., "tablet" vs "tablef") - lower priority
            val words = textLower.split(Regex("\\s+"))
            for (word in words) {
                if (StringSimilarity.fuzzyMatch(word, typeValue, maxDistance = 1)) {
                    fuzzyMatches.add(typeEnum)
                    break
                }
            }
        }

        // Combine: exact matches first, then fuzzy matches
        val result = (exactMatches + fuzzyMatches).distinct().take(MAX_TYPE_SUGGESTIONS)
        result.forEach { type ->
            AppLogger.d(TAG, "  Type detected: '${type.value}'")
        }

        return result
    }

    /**
     * Extract strength/dosage information using regex pattern matching.
     * Weights dosages by proximity to detected medication names.
     *
     * Matches patterns like:
     * - 10mg
     * - 5 mg
     * - .5mg
     * - 100 mg
     * - "xxx 3g xxx" or "xxx 3 g xxx"
     *
     * Does NOT match:
     * - "D3" (no space before digit)
     * - "Vitamin D3 Galen" -> "3g" (letters immediately before digit)
     *
     * Uses word boundary or whitespace requirement to avoid false matches.
     *
     * When multiple dosages are found, selects the one closest to any detected name.
     *
     * @param text OCR text to analyze
     * @param nameSuggestions List of detected medication names for proximity scoring
     * @return StrengthSuggestion if found, null otherwise
     */
    private fun extractStrengthSuggestion(text: String, nameSuggestions: List<String>): StrengthSuggestion? {
        // Captures `<number> <unit>` after a word boundary or whitespace; unit list defined in ALLOWED_UNITS.
        val unitsPattern = ALLOWED_UNITS.joinToString("|")
        val regex =
            Regex("""(?:^|\s)(\d+(?:\.\d+)?|\.\d+)\s*($unitsPattern)\b""", RegexOption.IGNORE_CASE)

        val matches = regex.findAll(text).toList()

        if (matches.isEmpty()) {
            return null
        }

        // If we have name suggestions, weight by proximity to names
        val bestMatch = if (nameSuggestions.isNotEmpty()) {
            AppLogger.d(
                TAG,
                "  Found ${matches.size} strength candidates, weighting by proximity to names...",
            )

            // Find positions of all name suggestions in text (case-insensitive)
            val namePositions = nameSuggestions.flatMap { name ->
                val cleanedName =
                    name.trim().trimStart { !it.isLetterOrDigit() }.trimEnd { !it.isLetterOrDigit() }
                val nameRegex = Regex(Regex.escape(cleanedName), RegexOption.IGNORE_CASE)
                nameRegex.findAll(text).map { it.range.first }.toList()
            }

            if (namePositions.isEmpty()) {
                AppLogger.d(TAG, "    No name positions found in text, using first match")
                matches.first()
            } else {
                // Score each match by minimum distance to any name
                matches.minByOrNull { match ->
                    val matchPos = match.range.first
                    namePositions.minOf { namePos -> abs(matchPos - namePos) }
                }.also { bestMatch ->
                    if (bestMatch != null) {
                        val matchPos = bestMatch.range.first
                        val minDist = namePositions.minOf { namePos -> abs(matchPos - namePos) }
                        AppLogger.d(
                            TAG,
                            "    Best match at position $matchPos (distance $minDist from nearest name)",
                        )
                    }
                } ?: matches.first()
            }
        } else {
            // No names detected, use first match
            matches.first()
        }

        val valueStr = bestMatch.groupValues[1]
        val unitStr = bestMatch.groupValues[2].lowercase()

        val unitEnum = MedicationStrengthUnit.fromValue(unitStr)
        if (unitEnum == null) {
            AppLogger.w(TAG, "Invalid strength unit detected: $unitStr")
            return null
        }

        try {
            val value = valueStr.toDouble()
            if (value > 0) {
                AppLogger.d(TAG, "  Strength detected: $value ${unitEnum.value}")
                return StrengthSuggestion(value = value, unit = unitEnum)
            }
        } catch (e: NumberFormatException) {
            AppLogger.w(TAG, "Failed to parse strength value: $valueStr")
        }

        return null
    }

    /**
     * Normalize OCR text to fix common recognition errors.
     *
     * Common OCR errors:
     * - Letter 'O' misread as digit '0' near numbers
     * - Letter 'l' misread as digit '1' near numbers
     *
     * @param text Raw OCR text
     * @return Normalized text with common errors corrected
     */
    private fun normalizeOCR(text: String): String {
        return text
            .lowercase()
            // Fix 'o' -> '0' when adjacent to digits
            .replace(Regex("""o(?=\d)"""), "0") // o followed by digit
            .replace(Regex("""(?<=\d)o"""), "0") // o preceded by digit
            // Fix 'l' -> '1' when adjacent to digits
            .replace(Regex("""l(?=\d)"""), "1") // l followed by digit
            .replace(Regex("""(?<=\d)l"""), "1") // l preceded by digit
    }

    /**
     * Preprocess OCR text into clean tokens.
     *
     * Steps:
     * 1. Remove special characters (keep alphanumeric, spaces, dots, hyphens)
     * 2. Normalize whitespace
     * 3. Split into words
     * 4. Filter short tokens (likely noise)
     *
     * @param text Normalized OCR text
     * @return List of clean tokens
     */
    private fun preprocessTokens(text: String): List<String> {
        return text
            // Remove special characters except dots and hyphens
            .replace(Regex("""[^a-z0-9\s.-]"""), " ")
            // Normalize whitespace
            .replace(Regex("""\s+"""), " ")
            .trim()
            // Split and filter
            .split(" ")
            .filter { it.length >= MIN_TOKEN_LENGTH }
    }

    /**
     * Generate bigrams (two-word combinations) from tokens.
     *
     * This helps detect compound medication names like:
     * - "vitamin d3" (where d3 is too short to be a token on its own)
     * - "vitamin c"
     * - "co codamol"
     *
     * We parse the original normalized text to catch short words that were
     * filtered out during tokenization (e.g., "d3" < 4 chars).
     *
     * @param normalizedText The normalized OCR text (before tokenization)
     * @return List of bigrams (space-separated two-word combinations)
     */
    private fun generateBigrams(normalizedText: String): List<String> {
        val bigrams = mutableListOf<String>()

        // Split normalized text into ALL words (including short ones)
        val allWords = normalizedText
            .replace(Regex("""[^a-z0-9\s.-]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .split(" ")
            .filter { it.isNotEmpty() }

        // Generate bigrams from all consecutive word pairs
        for (i in 0 until allWords.size - 1) {
            val first = allWords[i]
            val second = allWords[i + 1]

            // At least one word must be a proper token (>=4 chars)
            // This catches "vitamin d3" (first is token) but filters noise like "a b"
            if (first.length >= MIN_TOKEN_LENGTH || second.length >= MIN_TOKEN_LENGTH) {
                bigrams.add("$first $second")
            }
        }

        return bigrams.distinct()
    }
}
