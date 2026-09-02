package com.ilsecondodasinistra.proportion.core.transfer

import com.ilsecondodasinistra.proportion.core.domain.unit.QuantityFormatter
import com.ilsecondodasinistra.proportion.core.model.ShoppingItem

/**
 * The handful of translated words the shopping list formatter needs.
 *
 * Passed in rather than looked up, so the formatter stays free of Android and its output can be
 * asserted exactly in a test.
 */
data class ShoppingListStrings(
    val title: String,
    val checkedTitle: String,
    val attribution: String,
)

/**
 * The shopping list as shareable text: what lands in a message when someone asks "send me the
 * shopping list". Checked items move to their own section instead of disappearing, because a
 * message read later — in the shop, or by whoever else is doing the errand — should still show
 * what has already been picked up.
 *
 * Quantities are aligned in a column, following [PlainTextFormatter]'s convention exactly: a wall
 * of "500 g" mixed into ingredient names is hard to shop from.
 */
object ShoppingListFormatter {

    fun format(
        items: List<ShoppingItem>,
        strings: ShoppingListStrings,
        formatter: QuantityFormatter,
    ): String = buildString {
        appendLine(strings.title)

        val (checked, unchecked) = items.partition { it.isChecked }
        val width = items.maxOfOrNull { it.ingredient.name.length } ?: 0

        if (unchecked.isNotEmpty()) {
            appendLine()
            unchecked.forEach { appendLine(it.toRow(width, formatter)) }
        }

        if (checked.isNotEmpty()) {
            appendLine()
            appendLine(strings.checkedTitle)
            checked.forEach { appendLine(it.toRow(width, formatter)) }
        }

        appendLine()
        append(strings.attribution)
    }

    private fun ShoppingItem.toRow(width: Int, formatter: QuantityFormatter): String {
        val quantityText = formatter.format(quantity ?: 0.0, unit).text
        return alignedRow(ingredient.name, width, quantityText)
    }
}
