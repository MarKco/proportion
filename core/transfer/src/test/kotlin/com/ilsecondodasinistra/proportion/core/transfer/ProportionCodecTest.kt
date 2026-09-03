package com.ilsecondodasinistra.proportion.core.transfer

import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.model.Ingredient
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.RecipeIngredient
import com.ilsecondodasinistra.proportion.core.model.Tag
import org.junit.Test

class ProportionCodecTest {

    private val flour = Ingredient(
        id = "ing-1", key = null, name = "Farina 00", normalisedName = "farina 00",
        isBuiltIn = false, defaultUnit = MeasureUnit.GRAM,
    )
    private val eggs = Ingredient(
        id = "ing-2", key = null, name = "Uova", normalisedName = "uova",
        isBuiltIn = false, defaultUnit = MeasureUnit.EGG,
    )
    private val salt = Ingredient(
        id = "ing-3", key = null, name = "Sale", normalisedName = "sale",
        isBuiltIn = false, defaultUnit = MeasureUnit.TO_TASTE,
    )

    private val cake = Recipe(
        id = "9f2c-1111",
        title = "Torta di mele",
        servings = 4,
        steps = listOf("Sbatti le uova con lo zucchero.", "Inforna a 180 gradi."),
        ingredients = listOf(
            RecipeIngredient("l-1", flour, 0, 300.0, MeasureUnit.GRAM, note = "setacciata"),
            RecipeIngredient("l-2", eggs, 1, 2.0, MeasureUnit.EGG),
            RecipeIngredient("l-3", salt, 2, null, MeasureUnit.TO_TASTE, displayText = "q.b."),
        ),
        tags = listOf(
            Tag("tag-1", key = "dessert", name = null, isBuiltIn = true),
            Tag("tag-2", key = null, name = "merenda", isBuiltIn = false),
        ),
        notes = "Ricetta della nonna",
    )

    private fun roundTrip(recipe: Recipe): WireRecipe {
        val text = ProportionCodec.encode(listOf(recipe))
        val result = ProportionCodec.decode(text)
        return (result as DecodeResult.Success).recipes.single()
    }

    @Test
    fun `a recipe survives export and import`() {
        val wire = roundTrip(cake)

        assertThat(wire.id).isEqualTo("9f2c-1111")
        assertThat(wire.title).isEqualTo("Torta di mele")
        assertThat(wire.servings).isEqualTo(4)
        assertThat(wire.notes).isEqualTo("Ricetta della nonna")
        assertThat(wire.steps).containsExactly(
            "Sbatti le uova con lo zucchero.",
            "Inforna a 180 gradi.",
        ).inOrder()
    }

    @Test
    fun `ingredient lines keep quantity, unit and note`() {
        val wire = roundTrip(cake)

        assertThat(wire.ingredients.map { it.name })
            .containsExactly("Farina 00", "Uova", "Sale").inOrder()
        assertThat(wire.ingredients.first().qty).isEqualTo(300.0)
        assertThat(wire.ingredients.first().unit).isEqualTo("GRAM")
        assertThat(wire.ingredients.first().note).isEqualTo("setacciata")
    }

    @Test
    fun `an approximate line keeps its wording and no quantity`() {
        val salt = roundTrip(cake).ingredients.last()

        assertThat(salt.qty).isNull()
        assertThat(salt.unit).isEqualTo("TO_TASTE")
        assertThat(salt.display).isEqualTo("q.b.")
    }

    @Test
    fun `built in tags travel by key and user tags by name`() {
        val wire = roundTrip(cake)

        assertThat(wire.tags).containsExactly("builtin:dessert", "merenda")
    }

    @Test
    fun `the envelope names the format and the version`() {
        val text = ProportionCodec.encode(listOf(cake), exportedAt = "2026-09-01T18:20:00Z")

        assertThat(text).contains("\"format\": \"proportion\"")
        assertThat(text).contains("\"version\": 1")
        assertThat(text).contains("2026-09-01T18:20:00Z")
    }

    @Test
    fun `a file from a future version is refused by name`() {
        val text = ProportionCodec.encode(listOf(cake)).replace("\"version\": 1", "\"version\": 99")

        val failure = ProportionCodec.decode(text) as DecodeResult.Failure
        val reason = failure.reason as DecodeFailure.FutureVersion

        assertThat(reason.found).isEqualTo(99)
        assertThat(reason.supported).isEqualTo(1)
    }

    @Test
    fun `fields introduced by a later version are ignored, not fatal`() {
        val text = ProportionCodec.encode(listOf(cake))
            .replace("\"title\"", "\"somethingFromV3\": true,\n    \"title\"")

        val result = ProportionCodec.decode(text)

        assertThat(result).isInstanceOf(DecodeResult.Success::class.java)
    }

    @Test
    fun `valid JSON that is not one of ours is rejected as such`() {
        val result = ProportionCodec.decode("""{"format":"cookbook","version":1,"recipes":[]}""")

        assertThat((result as DecodeResult.Failure).reason)
            .isEqualTo(DecodeFailure.NotProportionFile)
    }

    @Test
    fun `truncated json is malformed rather than a crash`() {
        val text = ProportionCodec.encode(listOf(cake)).take(80)

        val result = ProportionCodec.decode(text)

        assertThat((result as DecodeResult.Failure).reason)
            .isInstanceOf(DecodeFailure.Malformed::class.java)
    }

    @Test
    fun `an unknown unit is refused instead of being guessed`() {
        val text = ProportionCodec.encode(listOf(cake)).replace("\"GRAM\"", "\"SPOONFULS\"")

        val result = ProportionCodec.decode(text)

        assertThat((result as DecodeResult.Failure).reason)
            .isInstanceOf(DecodeFailure.Malformed::class.java)
    }

    @Test
    fun `density travels when it is present even though v1 never writes it`() {
        val withDensity = cake.copy(
            ingredients = listOf(
                RecipeIngredient(
                    id = "l-1",
                    ingredient = flour.copy(densityGramsPerMl = 0.55),
                    position = 0,
                    quantity = 300.0,
                    unit = MeasureUnit.GRAM,
                ),
            ),
        )

        assertThat(roundTrip(withDensity).ingredients.single().density).isEqualTo(0.55)
    }

    @Test
    fun `exporting several recipes keeps them all`() {
        val second = cake.copy(id = "second", title = "Risotto")

        val text = ProportionCodec.encode(listOf(cake, second))
        val recipes = (ProportionCodec.decode(text) as DecodeResult.Success).recipes

        assertThat(recipes.map { it.title }).containsExactly("Torta di mele", "Risotto").inOrder()
    }

    @Test
    fun `an empty library exports and imports as an empty file`() {
        val text = ProportionCodec.encode(emptyList())

        assertThat((ProportionCodec.decode(text) as DecodeResult.Success).recipes).isEmpty()
    }

    @Test
    fun `a live recipe round-trips with no tombstone`() {
        assertThat(roundTrip(cake).deletedAt).isNull()
    }

    @Test
    fun `a deleted recipe's tombstone survives the round trip`() {
        val deleted = cake.copy(deletedAt = 12_345L)

        assertThat(roundTrip(deleted).deletedAt).isEqualTo(12_345L)
    }

    @Test
    fun `updatedAt and createdAt survive the round trip`() {
        val stamped = cake.copy(updatedAt = 999L, createdAt = 111L)

        val wire = roundTrip(stamped)

        assertThat(wire.updatedAt).isEqualTo(999L)
        assertThat(wire.createdAt).isEqualTo(111L)
    }

    @Test
    fun `an ingredient catalogue entry survives export and import`() {
        val entry = WireIngredientEntry(
            id = "ing-1",
            name = "Farina 00",
            normalisedName = "farina 00",
            defaultUnit = "GRAM",
            category = "FLOUR_AND_GRAIN",
            densityGramsPerMl = 0.55,
            itemWeightGrams = null,
            updatedAt = 1_000L,
        )

        val decoded = ProportionCodec.decodeIngredientEntry(ProportionCodec.encodeIngredientEntry(entry))

        assertThat(decoded).isEqualTo(entry)
    }

    @Test
    fun `an ingredient entry with every nullable field absent still round-trips`() {
        val entry = WireIngredientEntry(
            id = "ing-2",
            name = "La mia spezia",
            normalisedName = "la mia spezia",
            defaultUnit = "GRAM",
        )

        val decoded = ProportionCodec.decodeIngredientEntry(ProportionCodec.encodeIngredientEntry(entry))

        assertThat(decoded).isEqualTo(entry)
    }

    @Test
    fun `a malformed ingredient entry decodes to null rather than crashing`() {
        assertThat(ProportionCodec.decodeIngredientEntry("not json")).isNull()
    }

    @Test
    fun `a tag catalogue entry survives export and import`() {
        val entry = WireTagEntry(id = "tag-1", name = "Ricette di famiglia", colorIndex = 2, updatedAt = 2_000L)

        val decoded = ProportionCodec.decodeTagEntry(ProportionCodec.encodeTagEntry(entry))

        assertThat(decoded).isEqualTo(entry)
    }

    @Test
    fun `a malformed tag entry decodes to null rather than crashing`() {
        assertThat(ProportionCodec.decodeTagEntry("not json")).isNull()
    }
}
