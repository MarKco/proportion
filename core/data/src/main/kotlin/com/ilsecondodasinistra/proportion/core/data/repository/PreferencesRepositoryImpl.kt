package com.ilsecondodasinistra.proportion.core.data.repository

import com.ilsecondodasinistra.proportion.core.datastore.UserPreferencesDataSource
import com.ilsecondodasinistra.proportion.core.domain.repository.PreferencesRepository
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

    override suspend fun setLanguage(tag: String?) = dataSource.setLanguage(tag)
}
