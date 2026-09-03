package com.ilsecondodasinistra.proportion.core.data.sync

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.domain.repository.PreferencesRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.SyncRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.SyncResult
import com.ilsecondodasinistra.proportion.core.model.AppTheme
import com.ilsecondodasinistra.proportion.core.model.SyncLogEntry
import com.ilsecondodasinistra.proportion.core.model.ThemeMode
import com.ilsecondodasinistra.proportion.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class FakePreferencesRepository(syncEnabled: Boolean) : PreferencesRepository {
    private val state = MutableStateFlow(UserPreferences(syncEnabled = syncEnabled))
    override fun observePreferences(): Flow<UserPreferences> = state
    override suspend fun setThemeMode(mode: ThemeMode) = Unit
    override suspend fun setDynamicColour(enabled: Boolean) = Unit
    override suspend fun setAppTheme(theme: AppTheme) = Unit
    override suspend fun setSyncEnabled(enabled: Boolean) = Unit
    override suspend fun setSyncFolderUri(uri: String?) = Unit
}

private class FakeSyncRepository(private val onSyncNow: suspend () -> SyncResult) : SyncRepository {
    var callCount = 0
        private set
    override suspend fun exportRecipe(recipeId: String) = Unit
    override suspend fun exportIngredient(ingredientId: String) = Unit
    override suspend fun exportTag(tagId: String) = Unit
    override suspend fun syncNow(): SyncResult {
        callCount++
        return onSyncNow()
    }
    override fun observeLog(): Flow<List<SyncLogEntry>> = flowOf(emptyList())
}

@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {

    private fun worker(sync: SyncRepository, preferences: PreferencesRepository): SyncWorker =
        TestListenableWorkerBuilder<SyncWorker>(ApplicationProvider.getApplicationContext())
            .setWorkerFactory(FakeWorkerFactory(sync, preferences))
            .build()

    @Test
    fun `calls syncNow when sync is enabled`() = runTest {
        val sync = FakeSyncRepository { SyncResult(0, 0, 0, 0) }
        val result = worker(sync, FakePreferencesRepository(syncEnabled = true)).doWork()

        assertThat(sync.callCount).isEqualTo(1)
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `does nothing when sync is disabled`() = runTest {
        val sync = FakeSyncRepository { SyncResult(0, 0, 0, 0) }
        val result = worker(sync, FakePreferencesRepository(syncEnabled = false)).doWork()

        assertThat(sync.callCount).isEqualTo(0)
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `an unexpected failure asks for a retry instead of crashing the process`() = runTest {
        val sync = FakeSyncRepository { error("boom") }
        val result = worker(sync, FakePreferencesRepository(syncEnabled = true)).doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.retry())
    }
}

private class FakeWorkerFactory(
    private val sync: SyncRepository,
    private val preferences: PreferencesRepository,
) : androidx.work.WorkerFactory() {
    override fun createWorker(
        appContext: android.content.Context,
        workerClassName: String,
        workerParameters: androidx.work.WorkerParameters,
    ): ListenableWorker = SyncWorker(appContext, workerParameters, sync, preferences)
}
