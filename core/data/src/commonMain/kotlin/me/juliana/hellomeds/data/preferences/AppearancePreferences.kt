// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppearancePreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        private val SCREEN_PRIVACY = booleanPreferencesKey("screen_privacy")
    }

    /**
     * Whether to use Material You dynamic colors (Android 12+)
     * Defaults to true
     */
    val useDynamicColor: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[USE_DYNAMIC_COLOR] ?: true
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[USE_DYNAMIC_COLOR] = enabled
        }
    }

    /**
     * Whether to prevent screenshots and hide content in the app switcher.
     * On Android: sets FLAG_SECURE. On iOS: overlays a privacy screen when backgrounded.
     * Defaults to false (opt-in).
     */
    val screenPrivacy: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SCREEN_PRIVACY] ?: false
    }

    suspend fun setScreenPrivacy(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SCREEN_PRIVACY] = enabled
        }
    }
}
