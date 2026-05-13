// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.juliana.hellomeds.data.model.enums.DetectionMethod

/**
 * Manages user preferences for camera detection feature.
 *
 * Stores:
 * - Whether user has consented to use camera detection
 * - Preferred detection method (Heuristic vs Gemini Nano)
 * - Whether first-time dialog has been shown
 */
class CameraPreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val HAS_CONSENTED = booleanPreferencesKey("has_consented_to_camera")
        private val DETECTION_METHOD = stringPreferencesKey("detection_method")
        private val HAS_SHOWN_DIALOG = booleanPreferencesKey("has_shown_first_time_dialog")
    }

    /**
     * Flow of whether user has consented to camera detection
     */
    val hasConsented: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[HAS_CONSENTED] ?: false
    }

    /**
     * Flow of user's preferred detection method
     */
    val detectionMethod: Flow<DetectionMethod> = dataStore.data.map { preferences ->
        val methodString = preferences[DETECTION_METHOD] ?: DetectionMethod.GEMINI.name
        try {
            DetectionMethod.valueOf(methodString)
        } catch (e: IllegalArgumentException) {
            DetectionMethod.GEMINI
        }
    }

    /**
     * Flow of whether first-time dialog has been shown
     */
    val hasShownDialog: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[HAS_SHOWN_DIALOG] ?: false
    }

    /**
     * Set user consent for camera detection
     */
    suspend fun setConsent(consented: Boolean) {
        dataStore.edit { preferences ->
            preferences[HAS_CONSENTED] = consented
        }
    }

    /**
     * Set user's preferred detection method
     */
    suspend fun setDetectionMethod(method: DetectionMethod) {
        dataStore.edit { preferences ->
            preferences[DETECTION_METHOD] = method.name
        }
    }

    /**
     * Mark that first-time dialog has been shown (or reset it)
     */
    suspend fun markDialogShown(shown: Boolean = true) {
        dataStore.edit { preferences ->
            preferences[HAS_SHOWN_DIALOG] = shown
        }
    }

    /**
     * Reset all preferences (for testing or settings reset)
     */
    suspend fun reset() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
