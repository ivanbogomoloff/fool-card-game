package com.example.foolcardgame.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.foolcardgame.domain.model.ThemeMode
import com.example.foolcardgame.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_profile",
)

class ProfileDataStore(
    private val context: Context,
) : ProfileLocalStore {

    override fun observeProfile(): Flow<UserProfile> {
        return context.profileDataStore.data.map { preferences ->
            UserProfile(
                displayName = preferences[KEY_DISPLAY_NAME] ?: UserProfile.DEFAULT_DISPLAY_NAME,
                avatarId = preferences[KEY_AVATAR_ID] ?: UserProfile.DEFAULT_AVATAR_ID,
                soundsEnabled = preferences[KEY_SOUNDS_ENABLED] ?: true,
                themeMode = preferences[KEY_THEME_MODE]?.let { stored ->
                    ThemeMode.entries.firstOrNull { it.name == stored }
                } ?: ThemeMode.SYSTEM,
            )
        }
    }

    override suspend fun saveProfile(profile: UserProfile) {
        context.profileDataStore.edit { preferences ->
            preferences[KEY_DISPLAY_NAME] = profile.displayName
            preferences[KEY_AVATAR_ID] = profile.avatarId
            preferences[KEY_SOUNDS_ENABLED] = profile.soundsEnabled
            preferences[KEY_THEME_MODE] = profile.themeMode.name
        }
    }

    companion object {
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_AVATAR_ID = intPreferencesKey("avatar_id")
        private val KEY_SOUNDS_ENABLED = booleanPreferencesKey("sounds_enabled")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
