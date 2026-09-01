package com.ilsecondodasinistra.proportion.core.designsystem

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.designsystem.theme.ProPortionDarkColors
import com.ilsecondodasinistra.proportion.core.designsystem.theme.ProPortionLightColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Test

/**
 * The pastel palette is the fallback whenever Material You is unavailable or switched off, so it
 * has to stand on its own: WCAG AA for body text, in both schemes.
 */
class ColorContrastTest {

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    @Test
    fun `body text meets AA on the light surface`() {
        assertThat(contrastRatio(ProPortionLightColors.onSurface, ProPortionLightColors.surface))
            .isAtLeast(4.5)
        assertThat(contrastRatio(ProPortionLightColors.onSurfaceVariant, ProPortionLightColors.surface))
            .isAtLeast(4.5)
    }

    @Test
    fun `body text meets AA on the dark surface`() {
        assertThat(contrastRatio(ProPortionDarkColors.onSurface, ProPortionDarkColors.surface))
            .isAtLeast(4.5)
        assertThat(contrastRatio(ProPortionDarkColors.onSurfaceVariant, ProPortionDarkColors.surface))
            .isAtLeast(4.5)
    }

    @Test
    fun `text on the brand containers stays readable`() {
        assertThat(
            contrastRatio(
                ProPortionLightColors.onPrimaryContainer,
                ProPortionLightColors.primaryContainer,
            ),
        ).isAtLeast(4.5)
        assertThat(
            contrastRatio(
                ProPortionLightColors.onSecondaryContainer,
                ProPortionLightColors.secondaryContainer,
            ),
        ).isAtLeast(4.5)
        assertThat(
            contrastRatio(
                ProPortionLightColors.onTertiaryContainer,
                ProPortionLightColors.tertiaryContainer,
            ),
        ).isAtLeast(4.5)
    }

    @Test
    fun `primary buttons are readable in both schemes`() {
        assertThat(contrastRatio(ProPortionLightColors.onPrimary, ProPortionLightColors.primary))
            .isAtLeast(4.5)
        assertThat(contrastRatio(ProPortionDarkColors.onPrimary, ProPortionDarkColors.primary))
            .isAtLeast(4.5)
    }
}
