package com.ilsecondodasinistra.proportion.core.domain.scale

import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import org.junit.Test

class RecipeScalerByIngredientTest {

    private val scaler = TestScalerFactory.create()

    private fun succeed(result: ScaleResult): ScaledRecipe = (result as ScaleResult.Success).scaled

    @Test
    fun `fixing two eggs where the recipe wants two keeps the factor at one`() {
        val scaled = succeed(
            scaler.scale(
                TestRecipes.appleCake,
                ScaleConstraint.ByIngredient("line-Uova", 2.0, MeasureUnit.EGG),
            ),
        )
        assertThat(scaled.factor).isWithin(1e-9).of(1.0)
    }

    @Test
    fun `fixing three eggs scales the whole recipe up by one point five`() {
        val scaled = succeed(
            scaler.scale(
                TestRecipes.appleCake,
                ScaleConstraint.ByIngredient("line-Uova", 3.0, MeasureUnit.EGG),
            ),
        )

        assertThat(scaled.factor).isWithin(1e-9).of(1.5)
        assertThat(scaled.lines.first { it.lineId == "line-Farina" }.scaledQty).isWithin(1e-9).of(450.0)
    }

    @Test
    fun `the constraint may be expressed in another unit of the same category`() {
        val scaled = succeed(
            scaler.scale(
                TestRecipes.appleCake,
                ScaleConstraint.ByIngredient("line-Farina", 0.6, MeasureUnit.KILOGRAM),
            ),
        )
        assertThat(scaled.factor).isWithin(1e-9).of(2.0)
    }

    @Test
    fun `constraining an approximate ingredient is rejected`() {
        val result = scaler.scale(
            TestRecipes.appleCake,
            ScaleConstraint.ByIngredient("line-Sale", 2.0, MeasureUnit.PINCH),
        )
        assertThat((result as ScaleResult.Failure).reason)
            .isEqualTo(ScaleError.ConstraintOnApproximateUnit)
    }

    @Test
    fun `constraining in an incompatible unit is rejected`() {
        val result = scaler.scale(
            TestRecipes.appleCake,
            ScaleConstraint.ByIngredient("line-Farina", 300.0, MeasureUnit.MILLILITRE),
        )
        assertThat((result as ScaleResult.Failure).reason).isEqualTo(ScaleError.IncompatibleUnit)
    }

    @Test
    fun `constraining an unknown line is rejected`() {
        val result = scaler.scale(
            TestRecipes.appleCake,
            ScaleConstraint.ByIngredient("line-Nope", 1.0, MeasureUnit.GRAM),
        )
        assertThat((result as ScaleResult.Failure).reason).isEqualTo(ScaleError.UnknownLine)
    }
}
