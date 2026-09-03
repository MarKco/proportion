package com.ilsecondodasinistra.proportion.feature.settings

import com.ilsecondodasinistra.proportion.core.domain.repository.SyncResult
import com.ilsecondodasinistra.proportion.core.model.AppTheme
import com.ilsecondodasinistra.proportion.core.model.SyncLogEntry
import com.ilsecondodasinistra.proportion.core.model.ThemeMode
import com.ilsecondodasinistra.proportion.core.transfer.DecodeFailure

/**
 * The app's own language. [SYSTEM] means "follow the device" — the only state with no BCP-47 tag,
 * since it maps to clearing the override rather than choosing one.
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ITALIAN("it"),
    ENGLISH("en"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

/** Where the restore flow has got to. Nothing is written before [Confirming] is answered. */
sealed interface RestoreStep {
    data object Idle : RestoreStep
    data class Confirming(val total: Int, val alreadyPresent: Int) : RestoreStep
    data class ConfirmingReplace(val total: Int, val alreadyPresent: Int) : RestoreStep
    data class Done(val added: Int, val skipped: Int, val replaced: Boolean) : RestoreStep
    data class Failed(val reason: DecodeFailure) : RestoreStep
}

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColour: Boolean = true,
    val appTheme: AppTheme = AppTheme.PASTEL,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val restore: RestoreStep = RestoreStep.Idle,
    val backupSaved: Boolean = false,
    val isWorking: Boolean = false,
    val syncEnabled: Boolean = false,
    val syncFolderUri: String? = null,
    val syncIntervalHours: Int = 4,
    val syncInProgress: Boolean = false,
    val syncLastResult: SyncResult? = null,
    val syncLog: List<SyncLogEntry> = emptyList(),
) {
    /** The most recent failure, if any — what the error banner shows. */
    val syncLastError: SyncLogEntry? get() = syncLog.lastOrNull { it.isError }

    /**
     * The most recent sync of any kind, success or failure — sourced from the persisted log
     * rather than [syncLastResult], so it also reflects a run the background job did while this
     * screen (or the app) wasn't open.
     */
    val lastSyncLogEntry: SyncLogEntry? get() = syncLog.lastOrNull()
}
