package com.ilsecondodasinistra.proportion.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.model.AppTheme
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
        assertThat(prefs.appTheme).isEqualTo(AppTheme.PASTEL)
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
    fun `the chosen app theme survives a read back`() = runTest {
        dataSource.setAppTheme(AppTheme.HIGH_CONTRAST)

        assertThat(dataSource.preferences.first().appTheme).isEqualTo(AppTheme.HIGH_CONTRAST)
    }

    @Test
    fun `sync is off with no folder chosen by default, every 4 hours`() = runTest {
        val prefs = dataSource.preferences.first()

        assertThat(prefs.syncEnabled).isFalse()
        assertThat(prefs.syncFolderUri).isNull()
        assertThat(prefs.syncIntervalHours).isEqualTo(4)
    }

    @Test
    fun `the chosen sync interval survives a read back`() = runTest {
        dataSource.setSyncIntervalHours(12)

        assertThat(dataSource.preferences.first().syncIntervalHours).isEqualTo(12)
    }

    @Test
    fun `enabling sync and choosing a folder both survive a read back`() = runTest {
        dataSource.setSyncEnabled(true)
        dataSource.setSyncFolderUri("content://com.android.externalstorage.documents/tree/primary")

        val prefs = dataSource.preferences.first()
        assertThat(prefs.syncEnabled).isTrue()
        assertThat(prefs.syncFolderUri).isEqualTo("content://com.android.externalstorage.documents/tree/primary")
    }

    @Test
    fun `clearing the folder uri removes it rather than storing an empty string`() = runTest {
        dataSource.setSyncFolderUri("content://some/tree")
        dataSource.setSyncFolderUri(null)

        assertThat(dataSource.preferences.first().syncFolderUri).isNull()
    }
}
