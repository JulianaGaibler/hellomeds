// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.domain.validation

import kotlin.math.abs

/**
 * String similarity utilities for fuzzy matching.
 * Used for medication name matching with OCR errors.
 */
object StringSimilarity {

    /**
     * Calculate Levenshtein distance between two strings.
     *
     * The Levenshtein distance is the minimum number of single-character edits
     * (insertions, deletions, or substitutions) required to change one string into another.
     *
     * @param a First string
     * @param b Second string
     * @return The Levenshtein distance between the strings
     *
     * Time complexity: O(n * m) where n and m are the lengths of the strings
     * Space complexity: O(n * m)
     */
    fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }

        // Initialize first column (distance from empty string)
        for (i in 0..a.length) {
            dp[i][0] = i
        }

        // Initialize first row (distance from empty string)
        for (j in 0..b.length) {
            dp[0][j] = j
        }

        // Fill the matrix
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                if (a[i - 1] == b[j - 1]) {
                    // Characters match, no operation needed
                    dp[i][j] = dp[i - 1][j - 1]
                } else {
                    // Take minimum of three operations: insert, delete, substitute
                    dp[i][j] = 1 + minOf(
                        dp[i - 1][j], // Delete from a
                        dp[i][j - 1], // Insert into a
                        dp[i - 1][j - 1], // Substitute
                    )
                }
            }
        }

        return dp[a.length][b.length]
    }

    /**
     * Check if two strings are fuzzy matches within a maximum edit distance.
     *
     * Useful for matching medication names that may have OCR errors or typos.
     * Includes a length check optimization to quickly reject strings that are too different.
     *
     * @param a First string
     * @param b Second string
     * @param maxDistance Maximum allowed Levenshtein distance (default: 2)
     * @return True if the strings are within maxDistance edits of each other
     *
     * Example:
     * ```
     * fuzzyMatch("ibuprofen", "ibuprofan")  // true (distance = 1)
     * fuzzyMatch("aspirin", "aspirine")     // true (distance = 1)
     * fuzzyMatch("aspirin", "paracetamol")  // false (too different)
     * ```
     */
    fun fuzzyMatch(a: String, b: String, maxDistance: Int = 2): Boolean {
        // Quick length check: if length difference > maxDistance, they can't match
        if (abs(a.length - b.length) > maxDistance) {
            return false
        }

        return levenshteinDistance(a, b) <= maxDistance
    }
}
