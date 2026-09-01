package com.ilsecondodasinistra.proportion.core.domain.scale

import com.ilsecondodasinistra.proportion.core.domain.unit.DefaultUnitConverter
import com.ilsecondodasinistra.proportion.core.domain.unit.QuantityFormatter
import com.ilsecondodasinistra.proportion.core.domain.unit.UnitNamer
import com.ilsecondodasinistra.proportion.core.model.Ingredient
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.RecipeIngredient
import com.ilsecondodasinistra.proportion.core.model.Tag

internal object TestRecipes {

    fun ingredient(name: String, unit: MeasureUnit = MeasureUnit.GRAM) = Ingredient(
        id = "ing-$name",
        name = name,
        normalisedName = name.lowercase(),
        defaultUnit = unit,
    )

    fun line(name: String, qty: Double?, unit: MeasureUnit, position: Int = 0) = RecipeIngredient(
        id = "line-$name",
        ingredient = ingredient(name, unit),
        position = position,
        quantity = qty,
        unit = unit,
    )

    val ovenTag = Tag(id = "tag-oven", key = "oven", name = null, isBuiltIn = true)

    /** Serves 4: 300 g flour, 2 eggs, 120 g butter, salt to taste. */
    val appleCake = Recipe(
        id = "recipe-cake",
        title = "Torta di mele",
        servings = 4,
        steps = listOf("Sbatti le uova con lo zucchero.", "Inforna a 180 gradi per 40 minuti."),
        ingredients = listOf(
            line("Farina", 300.0, MeasureUnit.GRAM, 0),
            line("Uova", 2.0, MeasureUnit.EGG, 1),
            line("Burro", 120.0, MeasureUnit.GRAM, 2),
            line("Sale", null, MeasureUnit.TO_TASTE, 3),
        ),
        tags = emptyList(),
    )
}

internal class TestUnitNamer : UnitNamer {
    override fun shortName(unit: MeasureUnit, qty: Double): String = when (unit) {
        MeasureUnit.GRAM -> "g"
        MeasureUnit.KILOGRAM -> "kg"
        MeasureUnit.EGG -> if (qty == 1.0) "uovo" else "uova"
        MeasureUnit.SACHET -> if (qty == 1.0) "bustina" else "bustine"
        MeasureUnit.TO_TASTE -> "q.b."
        else -> unit.name.lowercase()
    }
}

internal object TestScalerFactory {
    fun create(): RecipeScaler {
        val converter = DefaultUnitConverter()
        val formatter = QuantityFormatter(converter, TestUnitNamer())
        return DefaultRecipeScaler(
            converter = converter,
            formatter = formatter,
            discreteAnalyser = DiscreteAnalyser(formatter),
            bakingAdvisor = BakingAdvisor(),
        )
    }
}
