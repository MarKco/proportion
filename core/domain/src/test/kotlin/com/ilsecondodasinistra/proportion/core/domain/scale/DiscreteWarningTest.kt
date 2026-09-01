package com.ilsecondodasinistra.proportion.core.domain.scale

import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import org.junit.Test

class DiscreteWarningTest {

    private val scaler = TestScalerFactory.create()

    /** Serves 2 with 3 eggs: at x1.5 it asks for 4.5 eggs. */
    private val eggRecipe = TestRecipes.appleCake.copy(
        servings = 2,
        ingredients = listOf(TestRecipes.line("Uova", 3.0, MeasureUnit.EGG)),
    )

    private fun succeed(result: ScaleResult): ScaledRecipe = (result as ScaleResult.Success).scaled

    @Test
    fun `a non integer discrete quantity raises a warning`() {
        val scaled = succeed(scaler.scale(eggRecipe, ScaleConstraint.ByFactor(1.5)))
        val warning = scaled.warnings.filterIsInstance<ScaleWarning.NonIntegerDiscrete>().single()

        assertThat(warning.lineId).isEqualTo("line-Uova")
        assertThat(warning.exact).isWithin(1e-9).of(4.5)
    }

    @Test
    fun `snap options offer both the floor and the ceiling with their own factors`() {
        val scaled = succeed(scaler.scale(eggRecipe, ScaleConstraint.ByFactor(1.5)))

        assertThat(scaled.snapSuggestions.map { it.targetQty }).containsExactly(4.0, 5.0)
        assertThat(scaled.snapSuggestions.first { it.targetQty == 4.0 }.resultingFactor)
            .isWithin(1e-9).of(4.0 / 3.0)
        assertThat(scaled.snapSuggestions.first { it.targetQty == 5.0 }.resultingFactor)
            .isWithin(1e-9).of(5.0 / 3.0)
    }

    @Test
    fun `values within five percent of a whole number snap silently`() {
        val scaled = succeed(scaler.scale(eggRecipe, ScaleConstraint.ByFactor(1.01)))

        assertThat(scaled.warnings).isEmpty()
        assertThat(scaled.lines.single().scaledQty).isWithin(1e-9).of(3.0)
        assertThat(scaled.lines.single().displayText).isEqualTo("3 uova")
    }

    @Test
    fun `a discrete quantity never rounds down to zero`() {
        val scaled = succeed(scaler.scale(eggRecipe, ScaleConstraint.ByFactor(0.1)))

        assertThat(scaled.lines.single().scaledQty).isAtLeast(1.0)
        assertThat(scaled.warnings.filterIsInstance<ScaleWarning.NonIntegerDiscrete>()).isNotEmpty()
    }

    @Test
    fun `quantities below the measurable threshold raise TooSmallToMeasure`() {
        val yeast = TestRecipes.appleCake.copy(
            servings = 4,
            ingredients = listOf(TestRecipes.line("Lievito", 4.0, MeasureUnit.GRAM)),
        )
        val scaled = succeed(scaler.scale(yeast, ScaleConstraint.ByFactor(0.1)))

        assertThat(scaled.warnings.filterIsInstance<ScaleWarning.TooSmallToMeasure>()).hasSize(1)
    }

    @Test
    fun `continuous ingredients never produce snap options`() {
        val scaled = succeed(scaler.scale(TestRecipes.appleCake, ScaleConstraint.ByFactor(1.37)))

        assertThat(scaled.snapSuggestions.map { it.lineId }).doesNotContain("line-Farina")
    }
}
