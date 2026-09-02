package com.ilsecondodasinistra.proportion.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.designsystem.theme.HighContrastDarkColors
import com.ilsecondodasinistra.proportion.core.designsystem.theme.HighContrastLightColors
import com.ilsecondodasinistra.proportion.core.designsystem.theme.PastelDarkColors
import com.ilsecondodasinistra.proportion.core.designsystem.theme.PastelLightColors
import com.ilsecondodasinistra.proportion.core.designsystem.theme.PlayfulDarkColors
import com.ilsecondodasinistra.proportion.core.designsystem.theme.PlayfulLightColors
import com.ilsecondodasinistra.proportion.core.designsystem.theme.VividDarkColors
import com.ilsecondodasinistra.proportion.core.designsystem.theme.VividLightColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Test

/**
 * Every static scheme is a fallback whenever Material You is unavailable or switched off, so each
 * has to stand on its own: WCAG AA for body text and brand containers. The high-contrast theme is
 * held to the stricter AAA bar, since accessibility is its entire reason to exist.
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

    private fun ColorScheme.assertMeetsAa() {
        assertThat(contrastRatio(onSurface, surface)).isAtLeast(4.5)
        assertThat(contrastRatio(onSurfaceVariant, surface)).isAtLeast(4.5)
        assertThat(contrastRatio(onPrimary, primary)).isAtLeast(4.5)
        assertThat(contrastRatio(onPrimaryContainer, primaryContainer)).isAtLeast(4.5)
        assertThat(contrastRatio(onSecondary, secondary)).isAtLeast(4.5)
        assertThat(contrastRatio(onSecondaryContainer, secondaryContainer)).isAtLeast(4.5)
        assertThat(contrastRatio(onTertiary, tertiary)).isAtLeast(4.5)
        assertThat(contrastRatio(onTertiaryContainer, tertiaryContainer)).isAtLeast(4.5)
    }

    private fun ColorScheme.assertMeetsAaa() {
        assertThat(contrastRatio(onSurface, surface)).isAtLeast(7.0)
        assertThat(contrastRatio(onPrimary, primary)).isAtLeast(7.0)
    }

    @Test
    fun `pastel theme meets AA in both schemes`() {
        PastelLightColors.assertMeetsAa()
        PastelDarkColors.assertMeetsAa()
    }

    @Test
    fun `vivid theme meets AA in both schemes`() {
        VividLightColors.assertMeetsAa()
        VividDarkColors.assertMeetsAa()
    }

    @Test
    fun `playful theme meets AA in both schemes`() {
        PlayfulLightColors.assertMeetsAa()
        PlayfulDarkColors.assertMeetsAa()
    }

    @Test
    fun `high contrast theme meets AA in both schemes`() {
        HighContrastLightColors.assertMeetsAa()
        HighContrastDarkColors.assertMeetsAa()
    }

    @Test
    fun `high contrast theme also clears the stricter AAA bar`() {
        HighContrastLightColors.assertMeetsAaa()
        HighContrastDarkColors.assertMeetsAaa()
    }
}
