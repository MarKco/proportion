package com.ilsecondodasinistra.proportion.core.transfer

import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.domain.scale.BakingAdvisor
import com.ilsecondodasinistra.proportion.core.domain.scale.DefaultRecipeScaler
import com.ilsecondodasinistra.proportion.core.domain.scale.DiscreteAnalyser
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaleConstraint
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaleResult
import com.ilsecondodasinistra.proportion.core.domain.unit.DefaultUnitConverter
import com.ilsecondodasinistra.proportion.core.model.Ingredient
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.RecipeIngredient
import org.junit.Test

class PlainTextFormatterTest {

    private val converter = DefaultUnitConverter()
    private val formatter = testFormatter()

    private val strings = PlainTextStrings(
        servings = { "Per $it persone" },
        scaledFor = { "Riproporzionata per $it persone" },
        notPerPerson = "Non a persona",
        ingredientsTitle = "Ingredienti",
        methodTitle = "Procedimento",
        attribution = "Condivisa con ProPortion",
    )

    private val cake = Recipe(
        id = "r-1",
        title = "Torta di mele",
        servings = 4,
        steps = listOf("Sbatti le uova.", "Inforna."),
        ingredients = listOf(
            RecipeIngredient(
                "l-1",
                Ingredient("i-1", "Farina 00", "farina 00", MeasureUnit.GRAM),
                0,
                300.0,
                MeasureUnit.GRAM,
            ),
            RecipeIngredient(
                "l-2",
                Ingredient("i-2", "Uova", "uova", MeasureUnit.EGG),
                1,
                2.0,
                MeasureUnit.EGG,
            ),
            RecipeIngredient(
                "l-3",
                Ingredient("i-3", "Sale", "sale", MeasureUnit.TO_TASTE),
                2,
                null,
                MeasureUnit.TO_TASTE,
            ),
        ),
        tags = emptyList(),
    )

    @Test
    fun `the text opens with the title and the serving count`() {
        val text = PlainTextFormatter.format(cake, strings, formatter)

        assertThat(text.lineSequence().first()).isEqualTo("Torta di mele")
        assertThat(text).contains("Per 4 persone")
    }

    @Test
    fun `ingredients are listed with their quantities`() {
        val text = PlainTextFormatter.format(cake, strings, formatter)

        assertThat(text).contains("Ingredienti")
        assertThat(text).contains("300 g")
        assertThat(text).contains("2 uova")
    }

    @Test
    fun `an approximate ingredient reads as written`() {
        val text = PlainTextFormatter.format(cake, strings, formatter)

        assertThat(text).contains("q.b.")
    }

    @Test
    fun `quantities line up in a column`() {
        val text = PlainTextFormatter.format(cake, strings, formatter)
        val lines = text.lines().filter { it.startsWith("- ") }

        // Each quantity starts at the same column, whatever the ingredient name's length.
        val columns = lines.map { line -> line.length - line.substringAfterLast("  ").length }
        assertThat(columns.distinct()).hasSize(1)
    }

    @Test
    fun `steps are numbered`() {
        val text = PlainTextFormatter.format(cake, strings, formatter)

        assertThat(text).contains("1. Sbatti le uova.")
        assertThat(text).contains("2. Inforna.")
    }

    @Test
    fun `a scaled recipe exports the new quantities`() {
        val scaler = DefaultRecipeScaler(
            converter,
            formatter,
            DiscreteAnalyser(formatter),
            BakingAdvisor(),
        )
        val scaled = (scaler.scale(cake, ScaleConstraint.ByServings(6.0)) as ScaleResult.Success).scaled

        val text = PlainTextFormatter.format(cake, strings, formatter, scaled)

        assertThat(text).contains("Riproporzionata per 6 persone")
        assertThat(text).contains("450 g")
        assertThat(text).doesNotContain("300 g")
    }

    @Test
    fun `the notes are carried when there are any`() {
        val text = PlainTextFormatter.format(cake.copy(notes = "Meglio con mele renette"), strings, formatter)

        assertThat(text).contains("Meglio con mele renette")
    }

    @Test
    fun `a recipe without servings says so instead of inventing a number`() {
        val text = PlainTextFormatter.format(cake.copy(servings = null), strings, formatter)

        assertThat(text).contains("Non a persona")
    }

    @Test
    fun `the text ends by naming the app`() {
        val text = PlainTextFormatter.format(cake, strings, formatter)

        assertThat(text.trimEnd().lines().last()).isEqualTo("Condivisa con ProPortion")
    }
}
