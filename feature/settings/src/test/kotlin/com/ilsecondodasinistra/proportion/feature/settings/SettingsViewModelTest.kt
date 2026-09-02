package com.ilsecondodasinistra.proportion.feature.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.data.PendingImport
import com.ilsecondodasinistra.proportion.core.domain.LocaleController
import com.ilsecondodasinistra.proportion.core.domain.repository.PreferencesRepository
import com.ilsecondodasinistra.proportion.core.model.AppTheme
import com.ilsecondodasinistra.proportion.core.model.ThemeMode
import com.ilsecondodasinistra.proportion.core.model.UserPreferences
import com.ilsecondodasinistra.proportion.core.transfer.DecodeFailure
import com.ilsecondodasinistra.proportion.core.transfer.ImportMode
import com.ilsecondodasinistra.proportion.core.transfer.ImportOutcome
import com.ilsecondodasinistra.proportion.core.transfer.ImportPreview
import com.ilsecondodasinistra.proportion.core.transfer.TransferRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

private class FakePreferencesRepository : PreferencesRepository {
    val preferences = MutableStateFlow(UserPreferences())
    override fun observePreferences(): Flow<UserPreferences> = preferences
    override suspend fun setThemeMode(mode: ThemeMode) {
        preferences.value = preferences.value.copy(themeMode = mode)
    }
    override suspend fun setDynamicColour(enabled: Boolean) {
        preferences.value = preferences.value.copy(useDynamicColour = enabled)
    }
    override suspend fun setAppTheme(theme: AppTheme) {
        preferences.value = preferences.value.copy(appTheme = theme)
    }
}

private class FakeLocaleController(private var tag: String? = null) : LocaleController {
    override fun currentTag(): String? = tag
    override fun setTag(tag: String?) {
        this.tag = tag
    }
}

private class FakeTransferRepository(
    var preview: ImportPreview = ImportPreview.Ready(total = 3, alreadyPresent = 1),
    var outcome: ImportOutcome = ImportOutcome.Imported(added = 2, skipped = 1, replacedLibrary = false),
) : TransferRepository {

    val importedModes = mutableListOf<ImportMode>()
    var exported = 0

    override suspend fun exportAll(): String {
        exported++
        return """{"format":"proportion","version":1,"recipes":[]}"""
    }

    override suspend fun exportRecipe(recipeId: String): String = exportAll()

    override suspend fun preview(text: String): ImportPreview = preview

    override suspend fun import(text: String, mode: ImportMode): ImportOutcome {
        importedModes += mode
        return outcome
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = FakePreferencesRepository()
    private val transfer = FakeTransferRepository()
    private val localeController = FakeLocaleController()

    private val pendingImport = PendingImport()

    private fun viewModel() = SettingsViewModel(preferences, transfer, pendingImport, localeController)

    @Test
    fun `the current preferences are shown`() = runTest {
        preferences.preferences.value = UserPreferences(
            ThemeMode.DARK,
            useDynamicColour = false,
            appTheme = AppTheme.HIGH_CONTRAST,
        )

        viewModel().uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertThat(state.themeMode).isEqualTo(ThemeMode.DARK)
            assertThat(state.useDynamicColour).isFalse()
            assertThat(state.appTheme).isEqualTo(AppTheme.HIGH_CONTRAST)
        }
    }

    @Test
    fun `changing the theme writes through`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onThemeChange(ThemeMode.LIGHT)
            advanceUntilIdle()

            assertThat(expectMostRecentItem().themeMode).isEqualTo(ThemeMode.LIGHT)
        }
        assertThat(preferences.preferences.value.themeMode).isEqualTo(ThemeMode.LIGHT)
    }

    @Test
    fun `turning off dynamic colour brings back the brand palette`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onDynamicColourChange(false)
            advanceUntilIdle()

            assertThat(expectMostRecentItem().useDynamicColour).isFalse()
        }
    }

    @Test
    fun `changing the app theme writes through`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onAppThemeChange(AppTheme.PLAYFUL)
            advanceUntilIdle()

            assertThat(expectMostRecentItem().appTheme).isEqualTo(AppTheme.PLAYFUL)
        }
        assertThat(preferences.preferences.value.appTheme).isEqualTo(AppTheme.PLAYFUL)
    }

    @Test
    fun `the current app language is read from the locale controller on start`() = runTest {
        localeController.setTag("it")

        viewModel().uiState.test {
            advanceUntilIdle()
            assertThat(expectMostRecentItem().language).isEqualTo(AppLanguage.ITALIAN)
        }
    }

    @Test
    fun `no override reads as following the system`() = runTest {
        viewModel().uiState.test {
            advanceUntilIdle()
            assertThat(expectMostRecentItem().language).isEqualTo(AppLanguage.SYSTEM)
        }
    }

    @Test
    fun `changing the language writes through to the locale controller`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onLanguageChange(AppLanguage.ENGLISH)
            advanceUntilIdle()

            assertThat(expectMostRecentItem().language).isEqualTo(AppLanguage.ENGLISH)
        }
        assertThat(localeController.currentTag()).isEqualTo("en")
    }

    @Test
    fun `choosing system clears the override`() = runTest {
        localeController.setTag("it")
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onLanguageChange(AppLanguage.SYSTEM)
            advanceUntilIdle()

            assertThat(expectMostRecentItem().language).isEqualTo(AppLanguage.SYSTEM)
        }
        assertThat(localeController.currentTag()).isNull()
    }

    @Test
    fun `a backup hands the text to the writer the screen supplied`() = runTest {
        var written: String? = null
        val vm = viewModel()

        vm.uiState.test {
            advanceUntilIdle()
            vm.onBackup { written = it }
            advanceUntilIdle()

            assertThat(expectMostRecentItem().backupSaved).isTrue()
        }

        assertThat(transfer.exported).isEqualTo(1)
        assertThat(written).contains("\"format\":\"proportion\"")
    }

    @Test
    fun `choosing a file previews it without importing`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onRestoreFileChosen("anything")
            advanceUntilIdle()

            val step = expectMostRecentItem().restore as RestoreStep.Confirming
            assertThat(step.total).isEqualTo(3)
            assertThat(step.alreadyPresent).isEqualTo(1)
        }
        assertThat(transfer.importedModes).isEmpty()
    }

    @Test
    fun `merging imports with merge`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onRestoreFileChosen("anything")
            advanceUntilIdle()
            vm.onRestoreConfirmed(ImportMode.MERGE)
            advanceUntilIdle()

            val done = expectMostRecentItem().restore as RestoreStep.Done
            assertThat(done.added).isEqualTo(2)
            assertThat(done.skipped).isEqualTo(1)
        }
        assertThat(transfer.importedModes).containsExactly(ImportMode.MERGE)
    }

    @Test
    fun `replacing everything asks a second time`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onRestoreFileChosen("anything")
            advanceUntilIdle()

            vm.onReplaceRequested()
            advanceUntilIdle()

            assertThat(expectMostRecentItem().restore)
                .isInstanceOf(RestoreStep.ConfirmingReplace::class.java)
        }
        assertThat(transfer.importedModes).isEmpty()
    }

    @Test
    fun `cancelling writes nothing`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onRestoreFileChosen("anything")
            advanceUntilIdle()

            vm.onRestoreDismissed()
            advanceUntilIdle()

            assertThat(expectMostRecentItem().restore).isEqualTo(RestoreStep.Idle)
        }
        assertThat(transfer.importedModes).isEmpty()
    }

    @Test
    fun `an unreadable file reports why instead of doing nothing`() = runTest {
        transfer.preview = ImportPreview.Invalid(DecodeFailure.NotProportionFile)
        val vm = viewModel()

        vm.uiState.test {
            advanceUntilIdle()
            vm.onRestoreFileChosen("nonsense")
            advanceUntilIdle()

            val failed = expectMostRecentItem().restore as RestoreStep.Failed
            assertThat(failed.reason).isEqualTo(DecodeFailure.NotProportionFile)
        }
    }

    @Test
    fun `a file from a newer app names the version it needs`() = runTest {
        transfer.preview = ImportPreview.Invalid(DecodeFailure.FutureVersion(found = 7, supported = 1))
        val vm = viewModel()

        vm.uiState.test {
            advanceUntilIdle()
            vm.onRestoreFileChosen("newer")
            advanceUntilIdle()

            val reason = (expectMostRecentItem().restore as RestoreStep.Failed).reason
            assertThat((reason as DecodeFailure.FutureVersion).found).isEqualTo(7)
        }
    }

    @Test
    fun `a file opened from outside the app is previewed on arrival`() = runTest {
        pendingImport.offer("a file from an email attachment")

        viewModel().uiState.test {
            advanceUntilIdle()

            assertThat(expectMostRecentItem().restore)
                .isInstanceOf(RestoreStep.Confirming::class.java)
        }
    }

    @Test
    fun `a file that could not be read says so`() = runTest {
        pendingImport.offerUnreadable()

        viewModel().uiState.test {
            advanceUntilIdle()

            assertThat(expectMostRecentItem().restore)
                .isInstanceOf(RestoreStep.Failed::class.java)
        }
    }

    @Test
    fun `a pending file is offered only once`() = runTest {
        pendingImport.offer("a file")
        viewModel()

        viewModel().uiState.test {
            advanceUntilIdle()
            assertThat(expectMostRecentItem().restore).isEqualTo(RestoreStep.Idle)
        }
    }

    @Test
    fun `confirming without a chosen file does nothing`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onRestoreConfirmed(ImportMode.REPLACE_ALL)
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(transfer.importedModes).isEmpty()
    }
}
