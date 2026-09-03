package com.ilsecondodasinistra.proportion.core.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppTheme { PASTEL, VIVID, PLAYFUL, HIGH_CONTRAST }

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColour: Boolean = true,
    val appTheme: AppTheme = AppTheme.PASTEL,
    /** Folder sync (phase 10): off by default, and meaningless without [syncFolderUri]. */
    val syncEnabled: Boolean = false,
    /** The SAF tree URI the user picked as the Syncthing-watched folder, as a string. */
    val syncFolderUri: String? = null,
    /** How often the background sync job runs. Kept coarse on purpose — see the scheduler. */
    val syncIntervalHours: Int = 4,
)
