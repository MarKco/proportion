package com.ilsecondodasinistra.proportion.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A quantity read while cooking is read at arm's length, so body text runs a little larger than the
 * Material default and numbers keep generous line height.
 */
val ProPortionTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
    )
}
