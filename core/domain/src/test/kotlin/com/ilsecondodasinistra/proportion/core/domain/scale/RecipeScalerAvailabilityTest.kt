package com.ilsecondodasinistra.proportion.core.domain.scale

import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import org.junit.Test

class RecipeScalerAvailabilityTest {

    private val scaler = TestScalerFactory.create()

    private fun succeed(result: ScaleResult): ScaledRecipe = (result as ScaleResult.Success).scaled

    /** Cake serves 4 with 300 g flour and 2 eggs. */
    private fun withPantry(vararg have: AvailableAmount) =
        scaler.scale(TestRecipes.appleCake, ScaleConstraint.ByAvailability(have.toList()))

    @Test
    fun `the limiting ingredient decides the factor`() {
        val scaled = succeed(
            withPantry(
                AvailableAmount("line-Uova", 3.0, MeasureUnit.EGG),      // allows x1.5
                AvailableAmount("line-Farina", 400.0, MeasureUnit.GRAM), // allows x1.33
            ),
        )

        assertThat(scaled.factor).isWithin(1e-9).of(400.0 / 300.0)
        assertThat(scaled.bottleneckLineId).isEqualTo("line-Farina")
    }

    @Test
    fun `leftovers are reported for every non limiting ingredient`() {
        val scaled = succeed(
            withPantry(
                AvailableAmount("line-Uova", 3.0, MeasureUnit.EGG),
                AvailableAmount("line-Farina", 400.0, MeasureUnit.GRAM),
            ),
        )
        val leftover = scaled.leftovers.single()

        assertThat(leftover.lineId).isEqualTo("line-Uova")
        assertThat(leftover.qty).isWithin(1e-9).of(3.0 - 2.0 * (400.0 / 300.0))
        assertThat(leftover.unit).isEqualTo(MeasureUnit.EGG)
    }

    @Test
    fun `achievable servings are reported as a fraction`() {
        val scaled = succeed(withPantry(AvailableAmount("line-Farina", 400.0, MeasureUnit.GRAM)))

        assertThat(scaled.servings).isWithin(1e-9).of(4.0 * 400.0 / 300.0)
    }

    @Test
    fun `an amount given in another unit of the same category still counts`() {
        val scaled = succeed(withPantry(AvailableAmount("line-Farina", 0.6, MeasureUnit.KILOGRAM)))

        assertThat(scaled.factor).isWithin(1e-9).of(2.0)
    }

    @Test
    fun `approximate ingredients are ignored when computing the factor`() {
        val scaled = succeed(
            withPantry(
                AvailableAmount("line-Sale", 1.0, MeasureUnit.PINCH),
                AvailableAmount("line-Farina", 600.0, MeasureUnit.GRAM),
            ),
        )

        assertThat(scaled.factor).isWithin(1e-9).of(2.0)
        assertThat(scaled.bottleneckLineId).isEqualTo("line-Farina")
    }

    @Test
    fun `an empty availability list is rejected`() {
        val result = scaler.scale(TestRecipes.appleCake, ScaleConstraint.ByAvailability(emptyList()))

        assertThat((result as ScaleResult.Failure).reason).isEqualTo(ScaleError.NonPositiveFactor)
    }

    @Test
    fun `having more than the recipe needs scales it up rather than capping at one`() {
        val scaled = succeed(withPantry(AvailableAmount("line-Uova", 6.0, MeasureUnit.EGG)))

        assertThat(scaled.factor).isWithin(1e-9).of(3.0)
    }
}
