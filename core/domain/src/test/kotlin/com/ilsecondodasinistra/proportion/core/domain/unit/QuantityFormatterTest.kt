package com.ilsecondodasinistra.proportion.core.domain.unit

import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import org.junit.Test

/** Italian unit names, standing in for the Android resource lookup used by the UI layer. */
private class FakeItalianUnitNamer : UnitNamer {
    override fun shortName(unit: MeasureUnit, qty: Double): String = when (unit) {
        MeasureUnit.GRAM -> "g"
        MeasureUnit.KILOGRAM -> "kg"
        MeasureUnit.MILLILITRE -> "ml"
        MeasureUnit.LITRE -> "l"
        MeasureUnit.TEASPOON -> "cucchiaino"
        MeasureUnit.TABLESPOON -> "cucchiaio"
        MeasureUnit.CUP -> "tazza"
        MeasureUnit.GLASS -> "bicchiere"
        MeasureUnit.EGG -> if (qty == 1.0) "uovo" else "uova"
        MeasureUnit.SACHET -> if (qty == 1.0) "bustina" else "bustine"
        MeasureUnit.TO_TASTE -> "q.b."
        MeasureUnit.PINCH -> "pizzico"
        else -> unit.name.lowercase()
    }
}

class QuantityFormatterTest {

    private val formatter = QuantityFormatter(DefaultUnitConverter(), FakeItalianUnitNamer())

    @Test
    fun `rounds mass to the nearest gram below one hundred grams`() {
        assertThat(formatter.format(37.4, MeasureUnit.GRAM).text).isEqualTo("37 g")
    }

    @Test
    fun `rounds mass to five grams at or above one hundred grams`() {
        assertThat(formatter.format(453.0, MeasureUnit.GRAM).text).isEqualTo("455 g")
        assertThat(formatter.format(450.0, MeasureUnit.GRAM).text).isEqualTo("450 g")
    }

    @Test
    fun `promotes large masses to kilograms`() {
        val formatted = formatter.format(1500.0, MeasureUnit.GRAM)
        assertThat(formatted.unit).isEqualTo(MeasureUnit.KILOGRAM)
        assertThat(formatted.text).isEqualTo("1,5 kg")
    }

    @Test
    fun `promotes large volumes to litres`() {
        val formatted = formatter.format(2000.0, MeasureUnit.MILLILITRE)
        assertThat(formatted.unit).isEqualTo(MeasureUnit.LITRE)
        assertThat(formatted.text).isEqualTo("2 l")
    }

    @Test
    fun `keeps domestic volumes in the unit the user chose`() {
        assertThat(formatter.format(6.0, MeasureUnit.CUP).unit).isEqualTo(MeasureUnit.CUP)
    }

    @Test
    fun `renders domestic volumes as human fractions`() {
        assertThat(formatter.format(0.5, MeasureUnit.TABLESPOON).text).isEqualTo("½ cucchiaio")
        assertThat(formatter.format(0.25, MeasureUnit.CUP).text).isEqualTo("¼ tazza")
        assertThat(formatter.format(1.5, MeasureUnit.TEASPOON).text).isEqualTo("1 ½ cucchiaino")
    }

    @Test
    fun `renders discrete quantities without decimals when integral`() {
        assertThat(formatter.format(3.0, MeasureUnit.EGG).text).isEqualTo("3 uova")
        assertThat(formatter.format(1.0, MeasureUnit.EGG).text).isEqualTo("1 uovo")
    }

    @Test
    fun `keeps the exact value for non integral discrete quantities`() {
        assertThat(formatter.format(1.5, MeasureUnit.EGG).text).isEqualTo("1 ½ uova")
    }

    @Test
    fun `falls back to one decimal when no human fraction fits`() {
        assertThat(formatter.format(1.9, MeasureUnit.EGG).text).isEqualTo("1,9 uova")
    }

    @Test
    fun `approximate units keep their wording and no number`() {
        val formatted = formatter.format(0.0, MeasureUnit.TO_TASTE)
        assertThat(formatted.text).isEqualTo("q.b.")
        assertThat(formatted.isBelowThreshold).isFalse()
    }

    @Test
    fun `flags quantities below the measurable threshold`() {
        assertThat(formatter.format(0.3, MeasureUnit.GRAM).isBelowThreshold).isTrue()
        assertThat(formatter.format(2.0, MeasureUnit.GRAM).isBelowThreshold).isFalse()
    }

    @Test
    fun `a domestic volume is measured against its millilitre value, not its own number`() {
        // 0.3 cup is 72 ml, which is perfectly measurable even though 0.3 looks small.
        assertThat(formatter.format(0.3, MeasureUnit.CUP).isBelowThreshold).isFalse()
    }
}
