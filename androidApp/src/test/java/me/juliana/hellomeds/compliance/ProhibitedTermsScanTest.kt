// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.compliance

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Regression test for the Google Play "Medical misinformation" policy.
 *
 * Scans user-facing string resources for terms that imply HelloMeds cures,
 * diagnoses, or treats disease. Hits are only allowed inside the protective
 * onboarding disclaimer (which uses these words in their negated form:
 * "does not provide medical advice, diagnosis, or treatment").
 *
 * If this test starts failing after a copy edit, either rephrase the new
 * string to drop the term, or — if the new string is itself a disclaimer —
 * add its resource name to [ALLOWLISTED_RESOURCE_NAMES].
 */
class ProhibitedTermsScanTest {

    private val prohibitedPattern = Regex(
        pattern = "\\b(cure|cures|cured|curing|diagnose|diagnoses|diagnosed|diagnosis|treat|treats|treated|treating|treatment)\\b",
        option = RegexOption.IGNORE_CASE,
    )

    @Test
    fun `no medical-claim language outside the onboarding disclaimer`() {
        val stringsFiles = STRING_RESOURCE_PATHS.map { File(it) }
        val missing = stringsFiles.filterNot { it.exists() }
        assertTrue(
            "Expected string resource files to exist: $missing",
            missing.isEmpty(),
        )

        val violations = mutableListOf<String>()
        val entryRegex = Regex("""<string\s+name="([^"]+)"[^>]*>([\s\S]*?)</string>""")

        for (file in stringsFiles) {
            val xml = file.readText()
            for (match in entryRegex.findAll(xml)) {
                val name = match.groupValues[1]
                val body = match.groupValues[2]
                if (name in ALLOWLISTED_RESOURCE_NAMES) continue
                val hits = prohibitedPattern.findAll(body).map { it.value }.toList()
                if (hits.isNotEmpty()) {
                    violations += "${file.name} :: $name -> ${hits.distinct()} :: \"$body\""
                }
            }
        }

        if (violations.isNotEmpty()) {
            fail(
                buildString {
                    appendLine(
                        "Found ${violations.size} prohibited-term occurrence(s). " +
                            "Either rephrase the string or, if it is itself a protective " +
                            "disclaimer, add its name to ALLOWLISTED_RESOURCE_NAMES.",
                    )
                    violations.forEach { appendLine("  - $it") }
                },
            )
        }
    }

    companion object {
        private val STRING_RESOURCE_PATHS = listOf(
            "../shared/src/commonMain/composeResources/values/strings.xml",
            "../shared/src/commonMain/composeResources/values-de/strings.xml",
        )

        // Strings that *use* prohibited terms in their negated form to disclaim
        // that the app is a medical device. Keep this list as small as possible.
        private val ALLOWLISTED_RESOURCE_NAMES = setOf(
            "onboarding_disclaimer_paragraph1",
        )
    }
}
