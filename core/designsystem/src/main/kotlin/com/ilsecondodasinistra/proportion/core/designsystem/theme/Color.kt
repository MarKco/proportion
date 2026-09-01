package com.ilsecondodasinistra.proportion.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand pastels. Used directly for charts and tag chips, and as the seed of the fallback schemes.
val Pistachio = Color(0xFFA8D5BA)
val Apricot = Color(0xFFF4B393)
val Butter = Color(0xFFF2D48A)
val Blueberry = Color(0xFFA9BEEA)
val Rose = Color(0xFFEFB0C4)

/** Reserved for impractical-quantity and oven advisories. Never decorative. */
val AmberWarningLight = Color(0xFF8A5514)
val AmberWarningDark = Color(0xFFE0AC63)
val AmberContainerLight = Color(0xFFFBEFDC)
val AmberContainerDark = Color(0xFF2E2418)

/** A green-black rather than a neutral grey, so text sits in the same family as the palette. */
val GreenInk = Color(0xFF1D2621)

val ProPortionLightColors = lightColorScheme(
    primary = Color(0xFF2E6B52),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Pistachio,
    onPrimaryContainer = Color(0xFF0B2318),
    secondary = Color(0xFF97553A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Apricot,
    onSecondaryContainer = Color(0xFF33150A),
    tertiary = Color(0xFF735B1B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Butter,
    onTertiaryContainer = Color(0xFF241A00),
    background = Color(0xFFFBFCF8),
    onBackground = GreenInk,
    surface = Color(0xFFFBFCF8),
    onSurface = GreenInk,
    surfaceVariant = Color(0xFFDEE7DF),
    onSurfaceVariant = Color(0xFF414B45),
    outline = Color(0xFF717B75),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

val ProPortionDarkColors = darkColorScheme(
    primary = Color(0xFF7FBF9C),
    onPrimary = Color(0xFF08301F),
    primaryContainer = Color(0xFF1F5540),
    onPrimaryContainer = Pistachio,
    secondary = Color(0xFFE0A183),
    onSecondary = Color(0xFF44210F),
    secondaryContainer = Color(0xFF6B3B24),
    onSecondaryContainer = Apricot,
    tertiary = Color(0xFFD9BB6B),
    onTertiary = Color(0xFF3B2F00),
    tertiaryContainer = Color(0xFF574503),
    onTertiaryContainer = Butter,
    background = Color(0xFF101614),
    onBackground = Color(0xFFE6EEE8),
    surface = Color(0xFF101614),
    onSurface = Color(0xFFE6EEE8),
    surfaceVariant = Color(0xFF3E4945),
    onSurfaceVariant = Color(0xFFBECAC3),
    outline = Color(0xFF88938D),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

/** Chart series, in drawing order. Kept apart from the semantic roles on purpose. */
val ProPortionChartColors = listOf(Apricot, Pistachio, Blueberry, Butter, Rose)
