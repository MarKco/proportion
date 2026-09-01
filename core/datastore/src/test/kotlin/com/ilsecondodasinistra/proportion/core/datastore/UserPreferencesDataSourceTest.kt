package com.ilsecondodasinistra.proportion.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserPreferencesDataSourceTest {

    private lateinit var dataSource: UserPreferencesDataSource

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        // A fresh file per test: preferences must not leak from one test into the next.
        val file = context.filesDir.resolve("prefs-${System.nanoTime()}.preferences_pb")
        dataSource = UserPreferencesDataSource(PreferenceDataStoreFactory.create { file })
    }

    @Test
    fun `defaults follow the system and keep Material You on`() = runTest {
        val prefs = dataSource.preferences.first()

        assertThat(prefs.themeMode).isEqualTo(ThemeMode.SYSTEM)
        assertThat(prefs.useDynamicColour).isTrue()
        assertThat(prefs.language).isNull()
    }

    @Test
    fun `the chosen theme survives a read back`() = runTest {
        dataSource.setThemeMode(ThemeMode.DARK)

        assertThat(dataSource.preferences.first().themeMode).isEqualTo(ThemeMode.DARK)
    }

    @Test
    fun `dynamic colour can be turned off to keep the brand palette`() = runTest {
        dataSource.setDynamicColour(false)

        assertThat(dataSource.preferences.first().useDynamicColour).isFalse()
    }

    @Test
    fun `clearing the language falls back to the system language`() = runTest {
        dataSource.setLanguage("it")
        assertThat(dataSource.preferences.first().language).isEqualTo("it")

        dataSource.setLanguage(null)
        assertThat(dataSource.preferences.first().language).isNull()
    }
}
