// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.domain.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StringSimilarityTest {

    private fun assertSymmetricDistance(a: String, b: String, expected: Int) {
        assertEquals(expected, StringSimilarity.levenshteinDistance(a, b), "Distance '$a' -> '$b'")
        assertEquals(expected, StringSimilarity.levenshteinDistance(b, a), "Distance '$b' -> '$a'")
    }

    // --- levenshteinDistance ---

    @Test
    fun identicalStrings_distanceZero() {
        assertSymmetricDistance("ibuprofen", "ibuprofen", 0)
    }

    @Test
    fun emptyStrings_distanceZero() {
        assertSymmetricDistance("", "", 0)
    }

    @Test
    fun oneEmpty_distanceIsLength() {
        assertSymmetricDistance("", "abc", 3)
    }

    @Test
    fun singleSubstitution_distanceOne() {
        assertSymmetricDistance("ibuprofen", "ibuprofan", 1)
    }

    @Test
    fun singleInsertion_distanceOne() {
        assertSymmetricDistance("aspirin", "aspirine", 1)
    }

    @Test
    fun singleDeletion_distanceOne() {
        assertSymmetricDistance("aspirin", "asprin", 1)
    }

    @Test
    fun completelyDifferent_highDistance() {
        val dist = StringSimilarity.levenshteinDistance("abc", "xyz")
        assertEquals(3, dist)
    }

    @Test
    fun caseSensitive_differentCase_hasDistance() {
        // Levenshtein is case-sensitive by default
        val dist = StringSimilarity.levenshteinDistance("Ibuprofen", "ibuprofen")
        assertEquals(1, dist)
    }

    @Test
    fun unicodeAccents_countsAsEdits() {
        // ë vs e is a substitution
        assertSymmetricDistance("naproxen", "naproxën", 1)
    }

    // --- fuzzyMatch ---

    @Test
    fun withinDefaultThreshold_returnsTrue() {
        // 1 edit, default maxDistance=2
        assertTrue(StringSimilarity.fuzzyMatch("aspirin", "aspirine"))
    }

    @Test
    fun atExactThreshold_returnsTrue() {
        // 2 edits, default maxDistance=2
        assertTrue(StringSimilarity.fuzzyMatch("aspirin", "aspirene"))
    }

    @Test
    fun beyondDefaultThreshold_returnsFalse() {
        assertFalse(StringSimilarity.fuzzyMatch("aspirin", "paracetamol"))
    }

    @Test
    fun lengthDifferenceTooLarge_earlyRejection() {
        // Length diff > maxDistance → false without computing full distance
        assertFalse(StringSimilarity.fuzzyMatch("a", "abcde", maxDistance = 2))
    }

    @Test
    fun customMaxDistance_respected() {
        // Distance is 1, but maxDistance=0 rejects it
        assertFalse(StringSimilarity.fuzzyMatch("cat", "bat", maxDistance = 0))
        assertTrue(StringSimilarity.fuzzyMatch("cat", "bat", maxDistance = 1))
    }
}
