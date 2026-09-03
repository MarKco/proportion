package com.ilsecondodasinistra.proportion.core.data.repository

import com.ilsecondodasinistra.proportion.core.datastore.UserPreferencesDataSource
import com.ilsecondodasinistra.proportion.core.domain.repository.PreferencesRepository
import com.ilsecondodasinistra.proportion.core.model.AppTheme
import com.ilsecondodasinistra.proportion.core.model.ThemeMode
import com.ilsecondodasinistra.proportion.core.model.UserPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class PreferencesRepositoryImpl @Inject constructor(
    private val dataSource: UserPreferencesDataSource,
) : PreferencesRepository {

    override fun observePreferences(): Flow<UserPreferences> = dataSource.preferences

    override suspend fun setThemeMode(mode: ThemeMode) = dataSource.setThemeMode(mode)

    override suspend fun setDynamicColour(enabled: Boolean) = dataSource.setDynamicColour(enabled)

    override suspend fun setAppTheme(theme: AppTheme) = dataSource.setAppTheme(theme)

    override suspend fun setSyncEnabled(enabled: Boolean) = dataSource.setSyncEnabled(enabled)

    override suspend fun setSyncFolderUri(uri: String?) = dataSource.setSyncFolderUri(uri)

    override suspend fun setSyncIntervalHours(hours: Int) = dataSource.setSyncIntervalHours(hours)
}
