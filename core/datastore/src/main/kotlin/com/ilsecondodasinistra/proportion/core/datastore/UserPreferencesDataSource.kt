package com.ilsecondodasinistra.proportion.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ilsecondodasinistra.proportion.core.model.AppTheme
import com.ilsecondodasinistra.proportion.core.model.ThemeMode
import com.ilsecondodasinistra.proportion.core.model.UserPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Small, boring, and the only place that knows the preference keys.
 *
 * The store is injected rather than created from a Context extension so that a test can hand it a
 * throwaway file instead of sharing one process-wide instance.
 */
@Singleton
class UserPreferencesDataSource @Inject constructor(
    private val store: DataStore<Preferences>,
) {

    val preferences: Flow<UserPreferences> = store.data.map { stored ->
        UserPreferences(
            themeMode = stored[THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            useDynamicColour = stored[DYNAMIC_COLOUR] ?: true,
            appTheme = stored[APP_THEME]
                ?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() }
                ?: AppTheme.PASTEL,
            syncEnabled = stored[SYNC_ENABLED] ?: false,
            syncFolderUri = stored[SYNC_FOLDER_URI],
            syncIntervalHours = stored[SYNC_INTERVAL_HOURS] ?: 4,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColour(enabled: Boolean) {
        store.edit { it[DYNAMIC_COLOUR] = enabled }
    }

    suspend fun setAppTheme(theme: AppTheme) {
        store.edit { it[APP_THEME] = theme.name }
    }

    suspend fun setSyncEnabled(enabled: Boolean) {
        store.edit { it[SYNC_ENABLED] = enabled }
    }

    /** Null clears the choice — the folder picker was cancelled, or the user turned sync off. */
    suspend fun setSyncFolderUri(uri: String?) {
        store.edit {
            if (uri == null) it.remove(SYNC_FOLDER_URI) else it[SYNC_FOLDER_URI] = uri
        }
    }

    suspend fun setSyncIntervalHours(hours: Int) {
        store.edit { it[SYNC_INTERVAL_HOURS] = hours }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOUR = booleanPreferencesKey("dynamic_colour")
        val APP_THEME = stringPreferencesKey("app_theme")
        val SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
        val SYNC_FOLDER_URI = stringPreferencesKey("sync_folder_uri")
        val SYNC_INTERVAL_HOURS = intPreferencesKey("sync_interval_hours")
    }
}
