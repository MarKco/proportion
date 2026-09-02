package com.ilsecondodasinistra.proportion.core.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppTheme { PASTEL, VIVID, PLAYFUL, HIGH_CONTRAST }

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColour: Boolean = true,
    val appTheme: AppTheme = AppTheme.PASTEL,
)
