package com.ilsecondodasinistra.proportion.feature.settings

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
    val language: AppLanguage = AppLanguage.SYSTEM,
    val restore: RestoreStep = RestoreStep.Idle,
    val backupSaved: Boolean = false,
    val isWorking: Boolean = false,
)
