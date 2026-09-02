package com.ilsecondodasinistra.proportion.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilsecondodasinistra.proportion.core.data.PendingImport
import com.ilsecondodasinistra.proportion.core.domain.LocaleController
import com.ilsecondodasinistra.proportion.core.domain.repository.PreferencesRepository
import com.ilsecondodasinistra.proportion.core.model.ThemeMode
import com.ilsecondodasinistra.proportion.core.transfer.DecodeFailure
import com.ilsecondodasinistra.proportion.core.transfer.ImportMode
import com.ilsecondodasinistra.proportion.core.transfer.ImportOutcome
import com.ilsecondodasinistra.proportion.core.transfer.ImportPreview
import com.ilsecondodasinistra.proportion.core.transfer.TransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Appearance preferences plus the backup and restore flow.
 *
 * Restore is deliberately two steps: the file is read and counted first, and the database is only
 * touched once the user has answered with the counts in front of them.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val transferRepository: TransferRepository,
    private val pendingImport: PendingImport,
    private val localeController: LocaleController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(language = AppLanguage.fromTag(localeController.currentTag())),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Held between the preview and the confirmation, so the file is read only once. */
    private var pendingText: String? = null

    init {
        // A file opened from outside the app waits here until this screen can preview it.
        when (val pending = pendingImport.consume()) {
            is PendingImport.Pending.Content -> onRestoreFileChosen(pending.text)
            PendingImport.Pending.Unreadable -> _uiState.update {
                it.copy(restore = RestoreStep.Failed(DecodeFailure.Malformed("unreadable")))
            }
            null -> Unit
        }

        viewModelScope.launch {
            preferencesRepository.observePreferences().collect { preferences ->
                _uiState.update {
                    it.copy(
                        themeMode = preferences.themeMode,
                        useDynamicColour = preferences.useDynamicColour,
                    )
                }
            }
        }
    }

    fun onThemeChange(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun onDynamicColourChange(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDynamicColour(enabled) }
    }

    /**
     * Takes effect immediately — [LocaleController] is the one thing here that isn't a
     * fire-and-forget preference write, since the running screens need to pick up the new
     * language, so the caller (the screen) also recreates the activity right after this.
     */
    fun onLanguageChange(language: AppLanguage) {
        localeController.setTag(language.tag)
        _uiState.update { it.copy(language = language) }
    }

    /** @param writer writes the backup text wherever the user chose. */
    fun onBackup(writer: suspend (String) -> Unit) {
        _uiState.update { it.copy(isWorking = true, backupSaved = false) }
        viewModelScope.launch {
            writer(transferRepository.exportAll())
            _uiState.update { it.copy(isWorking = false, backupSaved = true) }
        }
    }

    fun onBackupMessageShown() {
        _uiState.update { it.copy(backupSaved = false) }
    }

    fun onRestoreFileChosen(text: String) {
        pendingText = text
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            val step = when (val preview = transferRepository.preview(text)) {
                is ImportPreview.Invalid -> RestoreStep.Failed(preview.reason)
                is ImportPreview.Ready -> RestoreStep.Confirming(
                    total = preview.total,
                    alreadyPresent = preview.alreadyPresent,
                )
            }
            _uiState.update { it.copy(isWorking = false, restore = step) }
        }
    }

    /** Replacing everything is destructive, so it asks a second time. */
    fun onReplaceRequested() {
        val current = _uiState.value.restore as? RestoreStep.Confirming ?: return
        _uiState.update {
            it.copy(
                restore = RestoreStep.ConfirmingReplace(current.total, current.alreadyPresent),
            )
        }
    }

    fun onRestoreConfirmed(mode: ImportMode) {
        val text = pendingText ?: return
        _uiState.update { it.copy(isWorking = true) }

        viewModelScope.launch {
            val step = when (val outcome = transferRepository.import(text, mode)) {
                is ImportOutcome.Failed -> RestoreStep.Failed(outcome.reason)
                is ImportOutcome.Imported -> RestoreStep.Done(
                    added = outcome.added,
                    skipped = outcome.skipped,
                    replaced = outcome.replacedLibrary,
                )
            }
            pendingText = null
            _uiState.update { it.copy(isWorking = false, restore = step) }
        }
    }

    fun onRestoreDismissed() {
        pendingText = null
        _uiState.update { it.copy(restore = RestoreStep.Idle) }
    }
}
