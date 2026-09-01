package com.ilsecondodasinistra.proportion.core.domain.unit

import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.UnitCategory
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A quantity ready to be shown: the value, the unit it should be shown in (which may differ from
 * the one it was computed in) and the rendered text.
 */
data class FormattedQuantity(
    val value: Double,
    val unit: MeasureUnit,
    val text: String,
    val isBelowThreshold: Boolean = false,
)

/**
 * Supplies the short name of a unit for a given quantity, so plurals work ("1 uovo" / "2 uova").
 * The domain never touches Android resources; the UI layer implements this against `strings.xml`.
 */
fun interface UnitNamer {
    fun shortName(unit: MeasureUnit, qty: Double): String
}

/**
 * Turns a raw scaled number into something a cook can read: 455 g rather than 453.27 g,
 * "1 ½ cucchiaino" rather than 1.5, "1,5 kg" rather than 1500 g.
 */
class QuantityFormatter(
    private val converter: UnitConverter,
    private val namer: UnitNamer,
) {

    fun format(qty: Double, unit: MeasureUnit): FormattedQuantity {
        if (!unit.isScalable) {
            return FormattedQuantity(value = qty, unit = unit, text = namer.shortName(unit, qty))
        }

        val belowThreshold = qty * unit.baseFactor < MEASURABLE_THRESHOLD
        val promoted = promote(qty, unit)
        val value = promoted.first
        val displayUnit = promoted.second

        val rendered = when {
            displayUnit.isDiscrete || isDomestic(displayUnit) -> renderWithFractions(value)
            displayUnit == MeasureUnit.GRAM || displayUnit == MeasureUnit.MILLILITRE ->
                renderRoundedBaseUnit(value)
            else -> renderDecimal(value)
        }

        val roundedValue = when {
            displayUnit == MeasureUnit.GRAM || displayUnit == MeasureUnit.MILLILITRE ->
                roundBaseUnit(value)
            else -> value
        }

        return FormattedQuantity(
            value = roundedValue,
            unit = displayUnit,
            text = "$rendered ${namer.shortName(displayUnit, roundedValue)}",
            isBelowThreshold = belowThreshold,
        )
    }

    /** Only base units get promoted: a recipe written in cups stays in cups. */
    private fun promote(qty: Double, unit: MeasureUnit): Pair<Double, MeasureUnit> = when {
        unit == MeasureUnit.GRAM && qty >= PROMOTION_THRESHOLD ->
            (converter.convert(qty, unit, MeasureUnit.KILOGRAM) ?: qty) to MeasureUnit.KILOGRAM

        unit == MeasureUnit.MILLILITRE && qty >= PROMOTION_THRESHOLD ->
            (converter.convert(qty, unit, MeasureUnit.LITRE) ?: qty) to MeasureUnit.LITRE

        else -> qty to unit
    }

    private fun isDomestic(unit: MeasureUnit): Boolean =
        unit.category == UnitCategory.VOLUME &&
            unit != MeasureUnit.MILLILITRE &&
            unit != MeasureUnit.LITRE

    private fun roundBaseUnit(value: Double): Double =
        if (value < COARSE_ROUNDING_FROM) {
            value.roundToInt().toDouble()
        } else {
            (value / COARSE_STEP).roundToInt().toDouble() * COARSE_STEP
        }

    private fun renderRoundedBaseUnit(value: Double): String =
        roundBaseUnit(value).roundToInt().toString()

    private fun renderWithFractions(value: Double): String {
        val whole = floor(value).toInt()
        val remainder = value - whole
        val fraction = FRACTIONS.entries.firstOrNull { abs(remainder - it.key) <= FRACTION_TOLERANCE }

        return when {
            remainder <= FRACTION_TOLERANCE -> whole.toString()
            fraction == null -> renderDecimal(value)
            whole == 0 -> fraction.value
            else -> "$whole ${fraction.value}"
        }
    }

    private fun renderDecimal(value: Double): String {
        val text = String.format(Locale.ROOT, "%.1f", value)
        return text.removeSuffix(".0").replace('.', ',')
    }

    companion object {
        /** Below this many grams or millilitres, no kitchen scale helps. */
        const val MEASURABLE_THRESHOLD = 0.5

        /** From this many grams or millilitres, switch to kilograms or litres. */
        const val PROMOTION_THRESHOLD = 1_000.0

        /** Above this value, rounding to the gram is false precision. */
        const val COARSE_ROUNDING_FROM = 100.0
        const val COARSE_STEP = 5.0

        const val FRACTION_TOLERANCE = 0.03

        val FRACTIONS: Map<Double, String> = linkedMapOf(
            0.25 to "¼",
            1.0 / 3.0 to "⅓",
            0.5 to "½",
            2.0 / 3.0 to "⅔",
            0.75 to "¾",
        )
    }
}
