package com.ilsecondodasinistra.proportion.feature.settings

import com.ilsecondodasinistra.proportion.core.model.ThemeMode
import com.ilsecondodasinistra.proportion.core.transfer.DecodeFailure

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
    val restore: RestoreStep = RestoreStep.Idle,
    val backupSaved: Boolean = false,
    val isWorking: Boolean = false,
)
