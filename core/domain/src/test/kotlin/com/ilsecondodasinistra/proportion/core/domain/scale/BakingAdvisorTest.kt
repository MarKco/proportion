package com.ilsecondodasinistra.proportion.core.domain.scale

import com.google.common.truth.Truth.assertThat
import kotlin.math.sqrt
import org.junit.Test

class BakingAdvisorTest {

    private val advisor = BakingAdvisor()
    private val ovenRecipe = TestRecipes.appleCake.copy(tags = listOf(TestRecipes.ovenTag))

    @Test
    fun `no advisory for a recipe without the oven tag`() {
        assertThat(advisor.advise(TestRecipes.appleCake, 2.0)).isNull()
    }

    @Test
    fun `no advisory inside the safe band`() {
        assertThat(advisor.advise(ovenRecipe, 1.2)).isNull()
        assertThat(advisor.advise(ovenRecipe, 0.8)).isNull()
        assertThat(advisor.advise(ovenRecipe, 1.0)).isNull()
    }

    @Test
    fun `advisory above the safe band`() {
        val warning = advisor.advise(ovenRecipe, 1.5)

        assertThat(warning).isNotNull()
        assertThat(warning!!.factor).isWithin(1e-9).of(1.5)
    }

    @Test
    fun `advisory below the safe band`() {
        assertThat(advisor.advise(ovenRecipe, 0.5)).isNotNull()
    }

    @Test
    fun `tin diameter ratio is the square root of the factor at constant depth`() {
        val warning = advisor.advise(ovenRecipe, 1.5)!!

        assertThat(warning.suggestedTinDiameterRatio).isWithin(1e-9).of(sqrt(1.5))
        // a 24 cm tin becomes roughly 29 cm
        assertThat(24 * warning.suggestedTinDiameterRatio).isWithin(0.5).of(29.4)
    }

    @Test
    fun `the advisory reaches the scaled recipe through the scaler`() {
        val scaler = TestScalerFactory.create()
        val result = scaler.scale(ovenRecipe, ScaleConstraint.ByFactor(2.0))
        val warnings = (result as ScaleResult.Success).scaled.warnings

        assertThat(warnings.filterIsInstance<ScaleWarning.BakingTimeCaution>()).hasSize(1)
    }
}
