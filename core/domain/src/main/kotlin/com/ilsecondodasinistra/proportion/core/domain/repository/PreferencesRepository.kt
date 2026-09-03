package com.ilsecondodasinistra.proportion.core.domain.repository

import com.ilsecondodasinistra.proportion.core.model.AppTheme
import com.ilsecondodasinistra.proportion.core.model.ThemeMode
import com.ilsecondodasinistra.proportion.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    fun observePreferences(): Flow<UserPreferences>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColour(enabled: Boolean)
    suspend fun setAppTheme(theme: AppTheme)
    suspend fun setSyncEnabled(enabled: Boolean)

    /** Null clears the choice — the folder picker was cancelled, or the user turned sync off. */
    suspend fun setSyncFolderUri(uri: String?)

    suspend fun setSyncIntervalHours(hours: Int)
}
