package com.ilsecondodasinistra.proportion.core.transfer

import com.ilsecondodasinistra.proportion.core.domain.scale.ScaledRecipe
import com.ilsecondodasinistra.proportion.core.domain.unit.QuantityFormatter
import com.ilsecondodasinistra.proportion.core.model.Recipe

/**
 * The handful of translated words the formatter needs.
 *
 * Passed in rather than looked up, so the formatter stays free of Android and its output can be
 * asserted exactly in a test.
 */
data class PlainTextStrings(
    val servings: (Int) -> String,
    val scaledFor: (String) -> String,
    val notPerPerson: String,
    val ingredientsTitle: String,
    val methodTitle: String,
    val attribution: String,
)

/**
 * The share text: what lands in a message when someone asks "send me that recipe".
 *
 * Quantities are aligned in a column because a wall of "300 g" mixed into names is hard to shop
 * from, and the scaled version is exported when the user is sharing from a scaling.
 */
object PlainTextFormatter {

    fun format(
        recipe: Recipe,
        strings: PlainTextStrings,
        formatter: QuantityFormatter,
        scaled: ScaledRecipe? = null,
    ): String = buildString {
        appendLine(recipe.title)

        val servingsLine = when {
            scaled?.servings != null -> strings.scaledFor(scaled.servings!!.trimNumber())
            recipe.servings != null -> strings.servings(recipe.servings!!)
            else -> strings.notPerPerson
        }
        appendLine(servingsLine)
        appendLine()

        appendLine(strings.ingredientsTitle)
        val rows = recipe.ingredients.sortedBy { it.position }.mapIndexed { index, line ->
            val quantity = when {
                scaled != null -> scaled.lines.getOrNull(index)?.displayText.orEmpty()
                line.quantity != null -> line.displayText ?: formatter.format(line.quantity!!, line.unit).text
                else -> line.displayText ?: formatter.format(0.0, line.unit).text
            }
            line.ingredient.name to quantity
        }
        val width = rows.maxOfOrNull { it.first.length } ?: 0
        rows.forEach { (name, quantity) ->
            appendLine(alignedRow(name, width, quantity))
        }

        if (recipe.steps.isNotEmpty()) {
            appendLine()
            appendLine(strings.methodTitle)
            recipe.steps.forEachIndexed { index, step ->
                appendLine("${index + 1}. $step")
            }
        }

        recipe.notes?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine(it)
        }

        appendLine()
        append(strings.attribution)
    }

    /** "6" rather than "6.0", "4,3" rather than "4.3". */
    private fun Double.trimNumber(): String =
        if (this % 1.0 == 0.0) toInt().toString()
        else String.format(java.util.Locale.ROOT, "%.1f", this).replace('.', ',')
}
