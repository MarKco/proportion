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
    fun `refuses mass to volume in v1 because density is unknown`() {
        assertThat(converter.convert(100.0, MeasureUnit.GRAM, MeasureUnit.MILLILITRE)).isNull()
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
    fun `passing an ingredient changes nothing in v1`() {
        val flour = IngredientRef(id = "ing-1", normalisedName = "farina")
        assertThat(converter.convert(100.0, MeasureUnit.GRAM, MeasureUnit.MILLILITRE, flour)).isNull()
        assertThat(converter.convert(1.0, MeasureUnit.KILOGRAM, MeasureUnit.GRAM, flour)).isEqualTo(1000.0)
    }
}
