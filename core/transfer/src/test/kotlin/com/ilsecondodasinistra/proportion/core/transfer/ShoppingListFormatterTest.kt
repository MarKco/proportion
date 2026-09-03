package com.ilsecondodasinistra.proportion.core.transfer

import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.model.Ingredient
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.ShoppingItem
import org.junit.Test

class ShoppingListFormatterTest {

    private val strings = ShoppingListStrings(
        title = "Shopping list",
        checkedTitle = "Already bought",
        attribution = "Made with ProPortion",
    )

    @Test
    fun `unchecked items come first, aligned, with their amounts`() {
        val text = ShoppingListFormatter.format(
            listOf(item("Farina", 500.0, MeasureUnit.GRAM), item("Uova", 3.0, MeasureUnit.PIECE)),
            strings,
            testFormatter(),
        )

        assertThat(text).contains("- Farina  500 g")
        assertThat(text).contains("- Uova    3")
    }

    @Test
    fun `checked items move to their own section rather than disappearing`() {
        val text = ShoppingListFormatter.format(
            listOf(item("Farina", 500.0, MeasureUnit.GRAM), item("Sale", 5.0, MeasureUnit.GRAM, checked = true)),
            strings,
            testFormatter(),
        )

        assertThat(text.indexOf("Already bought")).isGreaterThan(text.indexOf("Farina"))
        assertThat(text).contains("Sale")
    }

    @Test
    fun `an item with no measurable amount still lists its name`() {
        val text = ShoppingListFormatter.format(
            listOf(item("Prezzemolo", null, MeasureUnit.TO_TASTE)),
            strings,
            testFormatter(),
        )

        assertThat(text).contains("Prezzemolo")
    }

    @Test
    fun `an empty list produces the title and nothing that looks like a bullet`() {
        val text = ShoppingListFormatter.format(emptyList(), strings, testFormatter())

        assertThat(text).contains("Shopping list")
        assertThat(text).doesNotContain("- ")
    }

    @Test
    fun `the attribution closes the message`() {
        val text = ShoppingListFormatter.format(
            listOf(item("Farina", 500.0, MeasureUnit.GRAM)),
            strings,
            testFormatter(),
        )

        assertThat(text.trim().endsWith("Made with ProPortion")).isTrue()
    }

    private fun item(
        name: String,
        quantity: Double?,
        unit: MeasureUnit,
        checked: Boolean = false,
    ): ShoppingItem = ShoppingItem(
        id = "item-$name",
        ingredient = Ingredient(
            id = "ing-$name", key = null, name = name, normalisedName = name.lowercase(),
            isBuiltIn = false,
        ),
        quantity = quantity,
        unit = unit,
        isChecked = checked,
    )
}
