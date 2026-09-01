package com.ilsecondodasinistra.proportion.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * @param dynamicColour Material You is on by default from Android 12; below that, and whenever the
 * user turns it off in Settings, the brand pastel scheme takes over.
 */
@Composable
fun ProPortionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColour: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColour && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> ProPortionDarkColors
        else -> ProPortionLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ProPortionTypography,
        shapes = ProPortionShapes,
        content = content,
    )
}
