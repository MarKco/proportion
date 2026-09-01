package com.ilsecondodasinistra.proportion.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MeasureUnitTest {

    @Test
    fun `mass units are expressed in grams`() {
        assertThat(MeasureUnit.GRAM.baseFactor).isEqualTo(1.0)
        assertThat(MeasureUnit.KILOGRAM.baseFactor).isEqualTo(1000.0)
        assertThat(MeasureUnit.KILOGRAM.category).isEqualTo(UnitCategory.MASS)
    }

    @Test
    fun `domestic units are volume units so cups convert to millilitres`() {
        assertThat(MeasureUnit.CUP.category).isEqualTo(UnitCategory.VOLUME)
        assertThat(MeasureUnit.CUP.baseFactor).isEqualTo(240.0)
        assertThat(MeasureUnit.TABLESPOON.baseFactor).isEqualTo(15.0)
        assertThat(MeasureUnit.TEASPOON.baseFactor).isEqualTo(5.0)
        assertThat(MeasureUnit.GLASS.baseFactor).isEqualTo(200.0)
    }

    @Test
    fun `count units are discrete`() {
        assertThat(MeasureUnit.EGG.category).isEqualTo(UnitCategory.COUNT)
        assertThat(MeasureUnit.EGG.isDiscrete).isTrue()
        assertThat(MeasureUnit.GRAM.isDiscrete).isFalse()
    }

    @Test
    fun `approximate units are never scalable`() {
        assertThat(MeasureUnit.TO_TASTE.isScalable).isFalse()
        assertThat(MeasureUnit.PINCH.isScalable).isFalse()
        assertThat(MeasureUnit.GRAM.isScalable).isTrue()
    }

    @Test
    fun `each continuous category has exactly one base unit`() {
        assertThat(MeasureUnit.entries.filter { it.category == UnitCategory.MASS && it.baseFactor == 1.0 })
            .containsExactly(MeasureUnit.GRAM)
        assertThat(MeasureUnit.entries.filter { it.category == UnitCategory.VOLUME && it.baseFactor == 1.0 })
            .containsExactly(MeasureUnit.MILLILITRE)
    }
}
