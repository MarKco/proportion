package com.ilsecondodasinistra.proportion.core.domain.scale

import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import org.junit.Test

class RecipeScalerServingsTest {

    private val scaler = TestScalerFactory.create()

    private fun succeed(result: ScaleResult): ScaledRecipe =
        (result as ScaleResult.Success).scaled

    @Test
    fun `scaling from four to six servings gives a factor of one point five`() {
        val scaled = succeed(scaler.scale(TestRecipes.appleCake, ScaleConstraint.ByServings(6.0)))

        assertThat(scaled.factor).isWithin(1e-9).of(1.5)
        assertThat(scaled.servings).isWithin(1e-9).of(6.0)
        assertThat(scaled.lines.first { it.lineId == "line-Farina" }.scaledQty).isWithin(1e-9).of(450.0)
        assertThat(scaled.lines.first { it.lineId == "line-Burro" }.scaledQty).isWithin(1e-9).of(180.0)
    }

    @Test
    fun `approximate ingredients pass through unscaled`() {
        val scaled = succeed(scaler.scale(TestRecipes.appleCake, ScaleConstraint.ByServings(8.0)))
        val salt = scaled.lines.first { it.lineId == "line-Sale" }

        assertThat(salt.isScaled).isFalse()
        assertThat(salt.scaledQty).isNull()
        assertThat(salt.displayUnit).isEqualTo(MeasureUnit.TO_TASTE)
        assertThat(salt.displayText).isEqualTo("q.b.")
    }

    @Test
    fun `the scaled line keeps what the original said`() {
        val scaled = succeed(scaler.scale(TestRecipes.appleCake, ScaleConstraint.ByServings(6.0)))
        val flour = scaled.lines.first { it.lineId == "line-Farina" }

        assertThat(flour.originalQty).isEqualTo(300.0)
        assertThat(flour.originalUnit).isEqualTo(MeasureUnit.GRAM)
        assertThat(flour.ingredientName).isEqualTo("Farina")
        assertThat(flour.displayText).isEqualTo("450 g")
    }

    @Test
    fun `a plain factor scales every scalable line`() {
        val scaled = succeed(scaler.scale(TestRecipes.appleCake, ScaleConstraint.ByFactor(0.5)))

        assertThat(scaled.factor).isWithin(1e-9).of(0.5)
        assertThat(scaled.servings).isWithin(1e-9).of(2.0)
        assertThat(scaled.lines.first { it.lineId == "line-Farina" }.scaledQty).isWithin(1e-9).of(150.0)
    }

    @Test
    fun `a non positive factor is rejected`() {
        val result = scaler.scale(TestRecipes.appleCake, ScaleConstraint.ByFactor(0.0))
        assertThat((result as ScaleResult.Failure).reason).isEqualTo(ScaleError.NonPositiveFactor)
    }

    @Test
    fun `scaling by servings fails when the recipe has no servings`() {
        val noServings = TestRecipes.appleCake.copy(servings = null)
        val result = scaler.scale(noServings, ScaleConstraint.ByServings(6.0))

        assertThat((result as ScaleResult.Failure).reason).isEqualTo(ScaleError.NoServingsDefined)
    }

    @Test
    fun `an empty recipe is rejected`() {
        val empty = TestRecipes.appleCake.copy(ingredients = emptyList())
        val result = scaler.scale(empty, ScaleConstraint.ByFactor(2.0))

        assertThat((result as ScaleResult.Failure).reason).isEqualTo(ScaleError.EmptyRecipe)
    }

    @Test
    fun `a recipe with no servings still scales by factor`() {
        val jam = TestRecipes.appleCake.copy(servings = null)
        val scaled = succeed(scaler.scale(jam, ScaleConstraint.ByFactor(2.0)))

        assertThat(scaled.servings).isNull()
        assertThat(scaled.lines.first { it.lineId == "line-Farina" }.scaledQty).isWithin(1e-9).of(600.0)
    }
}
