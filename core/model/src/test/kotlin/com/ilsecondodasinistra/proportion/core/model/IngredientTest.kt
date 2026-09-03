package com.ilsecondodasinistra.proportion.core.model

import org.junit.Assert.assertThrows
import org.junit.Test

class IngredientTest {

    @Test
    fun `a built in ingredient carries a key`() {
        Ingredient(id = "i1", key = "flour_00", name = "Farina 00", normalisedName = "farina 00", isBuiltIn = true)
    }

    @Test
    fun `a user ingredient carries no key`() {
        Ingredient(id = "i2", key = null, name = "Farina 00", normalisedName = "farina 00", isBuiltIn = false)
    }

    @Test
    fun `a built in ingredient with no key is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            Ingredient(id = "i3", key = null, name = "x", normalisedName = "x", isBuiltIn = true)
        }
    }

    @Test
    fun `a user ingredient with a key is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            Ingredient(id = "i4", key = "flour_00", name = "x", normalisedName = "x", isBuiltIn = false)
        }
    }
}
