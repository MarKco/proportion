package com.ilsecondodasinistra.proportion.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand pastels. Used directly for charts and tag chips.
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

/** Soft, muted hues for a gentle look. Chosen so every text-on-fill pair clears WCAG AA. */
val PastelLightColors = lightColorScheme(
    primary = Color(0xFF4C6E62),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCEAE3),
    onPrimaryContainer = Color(0xFF1C2E28),
    secondary = Color(0xFF8C6E52),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF2E1D2),
    onSecondaryContainer = Color(0xFF2E2013),
    tertiary = Color(0xFF6E6A8C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE6E3F2),
    onTertiaryContainer = Color(0xFF211F33),
    background = Color(0xFFFCFBF8),
    onBackground = Color(0xFF26241F),
    surface = Color(0xFFFCFBF8),
    onSurface = Color(0xFF26241F),
    surfaceVariant = Color(0xFFE8E3DB),
    onSurfaceVariant = Color(0xFF4B473F),
    outline = Color(0xFF7A756B),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

val PastelDarkColors = darkColorScheme(
    primary = Color(0xFFA8C9BC),
    onPrimary = Color(0xFF123027),
    primaryContainer = Color(0xFF33493F),
    onPrimaryContainer = Color(0xFFDCEAE3),
    secondary = Color(0xFFD9B999),
    onSecondary = Color(0xFF3A2A16),
    secondaryContainer = Color(0xFF5A4530),
    onSecondaryContainer = Color(0xFFF2E1D2),
    tertiary = Color(0xFFC6C1E0),
    onTertiary = Color(0xFF34314B),
    tertiaryContainer = Color(0xFF4C4868),
    onTertiaryContainer = Color(0xFFE6E3F2),
    background = Color(0xFF1C1B18),
    onBackground = Color(0xFFE9E6DF),
    surface = Color(0xFF1C1B18),
    onSurface = Color(0xFFE9E6DF),
    surfaceVariant = Color(0xFF4B473F),
    onSurfaceVariant = Color(0xFFCBC5B9),
    outline = Color(0xFF948E82),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

/** Saturated, high-energy hues. */
val VividLightColors = lightColorScheme(
    primary = Color(0xFF006B5A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF4FE8C9),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFFC2410C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD8BF),
    onSecondaryContainer = Color(0xFF3A1300),
    tertiary = Color(0xFF6D28D9),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE9D9FF),
    onTertiaryContainer = Color(0xFF260A5C),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1A1C1B),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1A1C1B),
    surfaceVariant = Color(0xFFDCE5E0),
    onSurfaceVariant = Color(0xFF404944),
    outline = Color(0xFF707974),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

val VividDarkColors = darkColorScheme(
    primary = Color(0xFF52DEC0),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005043),
    onPrimaryContainer = Color(0xFF7BFCE0),
    secondary = Color(0xFFFFB68C),
    onSecondary = Color(0xFF5A2100),
    secondaryContainer = Color(0xFF833200),
    onSecondaryContainer = Color(0xFFFFDBC4),
    tertiary = Color(0xFFD6BBFF),
    onTertiary = Color(0xFF3E1B7A),
    tertiaryContainer = Color(0xFF552F94),
    onTertiaryContainer = Color(0xFFEBDDFF),
    background = Color(0xFF1A1C1B),
    onBackground = Color(0xFFE2E3E0),
    surface = Color(0xFF1A1C1B),
    onSurface = Color(0xFFE2E3E0),
    surfaceVariant = Color(0xFF404944),
    onSurfaceVariant = Color(0xFFC0C9C3),
    outline = Color(0xFF8A938D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

/** Bright, whimsical hues — pink, teal and gold rather than the vivid scheme's deeper tones. */
val PlayfulLightColors = lightColorScheme(
    primary = Color(0xFFB3006B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD8E9),
    onPrimaryContainer = Color(0xFF3A0022),
    secondary = Color(0xFF007A7A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBAF2F0),
    onSecondaryContainer = Color(0xFF002020),
    tertiary = Color(0xFF8A6D00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE699),
    onTertiaryContainer = Color(0xFF2B2000),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF201A1D),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF201A1D),
    surfaceVariant = Color(0xFFF0DDE4),
    onSurfaceVariant = Color(0xFF504349),
    outline = Color(0xFF837377),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

val PlayfulDarkColors = darkColorScheme(
    primary = Color(0xFFFFAFDA),
    onPrimary = Color(0xFF5C0038),
    primaryContainer = Color(0xFF82004F),
    onPrimaryContainer = Color(0xFFFFD8E9),
    secondary = Color(0xFF4CDBDA),
    onSecondary = Color(0xFF003737),
    secondaryContainer = Color(0xFF005656),
    onSecondaryContainer = Color(0xFFBAF2F0),
    tertiary = Color(0xFFF5CB4E),
    onTertiary = Color(0xFF3B2E00),
    tertiaryContainer = Color(0xFF574400),
    onTertiaryContainer = Color(0xFFFFE699),
    background = Color(0xFF201A1D),
    onBackground = Color(0xFFEBE0E3),
    surface = Color(0xFF201A1D),
    onSurface = Color(0xFFEBE0E3),
    surfaceVariant = Color(0xFF504349),
    onSurfaceVariant = Color(0xFFD3C2C8),
    outline = Color(0xFF9C8C91),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

/** Near-black-on-white (and reverse) with a single bold accent. Meets WCAG AAA, not just AA. */
val HighContrastLightColors = lightColorScheme(
    primary = Color(0xFF0033CC),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E0FF),
    onPrimaryContainer = Color(0xFF001548),
    secondary = Color(0xFF7A2E00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC2),
    onSecondaryContainer = Color(0xFF2A0F00),
    tertiary = Color(0xFF6A0080),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6D9FF),
    onTertiaryContainer = Color(0xFF250036),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFF1A1A1A),
    outline = Color(0xFF3B3B3B),
    error = Color(0xFF8C0000),
    onError = Color(0xFFFFFFFF),
)

val HighContrastDarkColors = darkColorScheme(
    primary = Color(0xFFAEC6FF),
    onPrimary = Color(0xFF00174D),
    primaryContainer = Color(0xFF002C8C),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFFFB68C),
    onSecondary = Color(0xFF3D1300),
    secondaryContainer = Color(0xFF5C2200),
    onSecondaryContainer = Color(0xFFFFDCC2),
    tertiary = Color(0xFFEEB2FF),
    onTertiary = Color(0xFF430052),
    tertiaryContainer = Color(0xFF520066),
    onTertiaryContainer = Color(0xFFF6D9FF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFE0E0E0),
    outline = Color(0xFFC7C7C7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF4A0000),
)

/** Chart series, in drawing order. Kept apart from the semantic roles on purpose. */
val ProPortionChartColors = listOf(Apricot, Pistachio, Blueberry, Butter, Rose)
