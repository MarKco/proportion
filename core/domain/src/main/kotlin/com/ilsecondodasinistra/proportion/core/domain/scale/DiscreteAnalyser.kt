package com.ilsecondodasinistra.proportion.core.domain.scale

import com.ilsecondodasinistra.proportion.core.domain.unit.QuantityFormatter
import com.ilsecondodasinistra.proportion.core.model.RecipeIngredient
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Finds scaled quantities a cook cannot actually produce, and proposes practical alternatives.
 *
 * Two rules: a discrete quantity that lands close enough to a whole number is snapped silently,
 * because 2.02 eggs is 2 eggs and nobody needs to be told; one that does not is flagged with the
 * exact value plus a snap option for the whole numbers on either side. Each snap carries the factor
 * it implies, so accepting it rescales the entire recipe rather than one line.
 */
class DiscreteAnalyser(
    private val formatter: QuantityFormatter,
) {

    data class Analysis(
        val lines: List<ScaledLine>,
        val warnings: List<ScaleWarning>,
        val snaps: List<SnapOption>,
    )

    fun analyse(lines: List<ScaledLine>, originals: List<RecipeIngredient>): Analysis {
        val warnings = mutableListOf<ScaleWarning>()
        val snaps = mutableListOf<SnapOption>()
        val originalById = originals.associateBy { it.id }

        val adjusted = lines.map { line ->
            val scaled = line.scaledQty
            if (!line.isScaled || scaled == null) {
                line
            } else if (line.displayUnit.isDiscrete) {
                adjustDiscrete(line, scaled, originalById, warnings, snaps)
            } else {
                if (isBelowMeasurableThreshold(line, scaled)) {
                    warnings += ScaleWarning.TooSmallToMeasure(line.lineId)
                }
                line
            }
        }

        return Analysis(lines = adjusted, warnings = warnings, snaps = snaps)
    }

    private fun adjustDiscrete(
        line: ScaledLine,
        scaled: Double,
        originalById: Map<String, RecipeIngredient>,
        warnings: MutableList<ScaleWarning>,
        snaps: MutableList<SnapOption>,
    ): ScaledLine {
        val nearest = scaled.roundToInt().toDouble()

        if (nearest >= 1.0 && abs(scaled - nearest) <= SNAP_TOLERANCE * nearest) {
            return line.withQuantity(nearest)
        }

        warnings += ScaleWarning.NonIntegerDiscrete(lineId = line.lineId, exact = scaled)

        val originalQty = originalById[line.lineId]?.quantity
        if (originalQty != null && originalQty > 0.0) {
            listOf(floor(scaled), ceil(scaled))
                .filter { it >= 1.0 }
                .distinct()
                .forEach { target ->
                    snaps += SnapOption(
                        lineId = line.lineId,
                        targetQty = target,
                        resultingFactor = target / originalQty,
                    )
                }
        }

        // Half an egg is impossible, but so is none of it: never let a needed ingredient vanish.
        return if (scaled < 1.0) line.withQuantity(1.0) else line
    }

    private fun isBelowMeasurableThreshold(line: ScaledLine, scaled: Double): Boolean =
        line.displayUnit.isScalable &&
            scaled * line.displayUnit.baseFactor < QuantityFormatter.MEASURABLE_THRESHOLD

    private fun ScaledLine.withQuantity(qty: Double): ScaledLine {
        val formatted = formatter.format(qty, displayUnit)
        return copy(
            scaledQty = qty,
            displayUnit = formatted.unit,
            displayText = formatted.text,
        )
    }

    companion object {
        /** Within this relative distance from a whole number, snap silently. */
        const val SNAP_TOLERANCE = 0.05
    }
}
