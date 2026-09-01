package com.ilsecondodasinistra.proportion.core.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** null language means "follow the system". */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColour: Boolean = true,
    val language: String? = null,
)
