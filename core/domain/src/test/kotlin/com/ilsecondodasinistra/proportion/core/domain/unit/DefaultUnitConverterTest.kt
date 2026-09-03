package com.ilsecondodasinistra.proportion.core.domain.unit

import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import org.junit.Test

class DefaultUnitConverterTest {

    private val converter = DefaultUnitConverter()

    @Test
    fun `converts within the mass category`() {
        assertThat(converter.convert(1500.0, MeasureUnit.GRAM, MeasureUnit.KILOGRAM)).isEqualTo(1.5)
        assertThat(converter.convert(0.25, MeasureUnit.KILOGRAM, MeasureUnit.GRAM)).isEqualTo(250.0)
    }

    @Test
    fun `converts cups to millilitres because both are volume`() {
        assertThat(converter.convert(2.0, MeasureUnit.CUP, MeasureUnit.MILLILITRE)).isEqualTo(480.0)
        assertThat(converter.convert(45.0, MeasureUnit.MILLILITRE, MeasureUnit.TABLESPOON)).isEqualTo(3.0)
    }

    @Test
    fun `refuses mass to volume when the ingredient has no known density`() {
        assertThat(converter.convert(100.0, MeasureUnit.GRAM, MeasureUnit.MILLILITRE)).isNull()
        val noDensity = IngredientRef(id = "ing-1", normalisedName = "farina")
        assertThat(converter.convert(100.0, MeasureUnit.GRAM, MeasureUnit.MILLILITRE, noDensity)).isNull()
    }

    @Test
    fun `converts mass to volume and back through the ingredient's density`() {
        val sugar = IngredientRef(id = "ing-2", normalisedName = "zucchero", densityGramsPerMl = 0.85)
        // 1 US cup = 240 ml (MeasureUnit.CUP.baseFactor) x 0.85 g/ml = 204 g.
        assertThat(converter.convert(1.0, MeasureUnit.CUP, MeasureUnit.GRAM, sugar)).isEqualTo(204.0)
        assertThat(converter.convert(204.0, MeasureUnit.GRAM, MeasureUnit.CUP, sugar)).isEqualTo(1.0)
    }

    @Test
    fun `converts count to mass and back through the ingredient's item weight`() {
        val bacon = IngredientRef(
            id = "ing-3",
            normalisedName = "pancetta a fette",
            defaultUnit = MeasureUnit.SLICE,
            itemWeightGrams = 12.0,
        )
        assertThat(converter.convert(300.0, MeasureUnit.GRAM, MeasureUnit.SLICE, bacon)).isEqualTo(25.0)
        assertThat(converter.convert(25.0, MeasureUnit.SLICE, MeasureUnit.GRAM, bacon)).isEqualTo(300.0)
    }

    @Test
    fun `refuses count to mass when the count unit is not the ingredient's own default unit`() {
        val bacon = IngredientRef(
            id = "ing-3",
            normalisedName = "pancetta a fette",
            defaultUnit = MeasureUnit.SLICE,
            itemWeightGrams = 12.0,
        )
        assertThat(converter.convert(2.0, MeasureUnit.PIECE, MeasureUnit.GRAM, bacon)).isNull()
    }

    @Test
    fun `converts count to volume by chaining item weight and density`() {
        val egg = IngredientRef(
            id = "ing-4",
            normalisedName = "uovo",
            defaultUnit = MeasureUnit.EGG,
            densityGramsPerMl = 1.03,
            itemWeightGrams = 55.0,
        )
        // 3 eggs x 55 g = 165 g; 165 g / 1.03 g/ml = 160.19... ml.
        assertThat(converter.convert(3.0, MeasureUnit.EGG, MeasureUnit.MILLILITRE, egg))
            .isWithin(0.01).of(165.0 / 1.03)
    }

    @Test
    fun `requirementFor reports NONE when the ingredient already carries what convert needs`() {
        assertThat(requirementFor(MeasureUnit.GRAM, MeasureUnit.KILOGRAM, null)).isEqualTo(DensityRequirement.NONE)

        val sugar = IngredientRef(id = "ing-2", normalisedName = "zucchero", densityGramsPerMl = 0.85)
        assertThat(requirementFor(MeasureUnit.GRAM, MeasureUnit.CUP, sugar)).isEqualTo(DensityRequirement.NONE)

        val bacon = IngredientRef(
            id = "ing-3",
            normalisedName = "pancetta a fette",
            defaultUnit = MeasureUnit.SLICE,
            itemWeightGrams = 12.0,
        )
        assertThat(requirementFor(MeasureUnit.GRAM, MeasureUnit.SLICE, bacon)).isEqualTo(DensityRequirement.NONE)
    }

    @Test
    fun `requirementFor reports what is missing to cross a category`() {
        assertThat(requirementFor(MeasureUnit.GRAM, MeasureUnit.MILLILITRE, null))
            .isEqualTo(DensityRequirement.DENSITY)

        val noDensity = IngredientRef(id = "ing-1", normalisedName = "farina")
        assertThat(requirementFor(MeasureUnit.GRAM, MeasureUnit.MILLILITRE, noDensity))
            .isEqualTo(DensityRequirement.DENSITY)

        val bacon = IngredientRef(id = "ing-3", normalisedName = "pancetta", defaultUnit = MeasureUnit.SLICE)
        assertThat(requirementFor(MeasureUnit.GRAM, MeasureUnit.SLICE, bacon))
            .isEqualTo(DensityRequirement.ITEM_WEIGHT)

        val egg = IngredientRef(id = "ing-4", normalisedName = "uovo", defaultUnit = MeasureUnit.EGG)
        assertThat(requirementFor(MeasureUnit.MILLILITRE, MeasureUnit.EGG, egg)).isEqualTo(DensityRequirement.BOTH)

        val eggWithDensity = egg.copy(densityGramsPerMl = 1.03)
        assertThat(requirementFor(MeasureUnit.MILLILITRE, MeasureUnit.EGG, eggWithDensity))
            .isEqualTo(DensityRequirement.ITEM_WEIGHT)
    }

    @Test
    fun `requirementFor reports UNSUPPORTED regardless of data when no answer would help`() {
        assertThat(requirementFor(MeasureUnit.SLICE, MeasureUnit.PIECE, null))
            .isEqualTo(DensityRequirement.UNSUPPORTED)
        assertThat(requirementFor(MeasureUnit.PINCH, MeasureUnit.GRAM, null))
            .isEqualTo(DensityRequirement.UNSUPPORTED)

        // The COUNT unit asked for isn't this ingredient's own — no item weight would fix that.
        val bacon = IngredientRef(id = "ing-3", normalisedName = "pancetta", defaultUnit = MeasureUnit.SLICE)
        assertThat(requirementFor(MeasureUnit.GRAM, MeasureUnit.PIECE, bacon))
            .isEqualTo(DensityRequirement.UNSUPPORTED)
        assertThat(requirementFor(MeasureUnit.GRAM, MeasureUnit.SLICE, null))
            .isEqualTo(DensityRequirement.UNSUPPORTED)
    }

    @Test
    fun `refuses conversion involving an approximate unit`() {
        assertThat(converter.convert(1.0, MeasureUnit.PINCH, MeasureUnit.GRAM)).isNull()
        assertThat(converter.convert(1.0, MeasureUnit.GRAM, MeasureUnit.TO_TASTE)).isNull()
    }

    @Test
    fun `count units convert only to themselves`() {
        assertThat(converter.convert(3.0, MeasureUnit.EGG, MeasureUnit.EGG)).isEqualTo(3.0)
        assertThat(converter.convert(3.0, MeasureUnit.EGG, MeasureUnit.SLICE)).isNull()
    }

    @Test
    fun `same-category conversion ignores the ingredient argument`() {
        val flour = IngredientRef(id = "ing-1", normalisedName = "farina")
        assertThat(converter.convert(1.0, MeasureUnit.KILOGRAM, MeasureUnit.GRAM, flour)).isEqualTo(1000.0)
    }
}
