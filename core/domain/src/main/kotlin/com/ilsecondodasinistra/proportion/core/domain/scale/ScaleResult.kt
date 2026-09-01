package com.ilsecondodasinistra.proportion.core.domain.scale

import com.ilsecondodasinistra.proportion.core.model.MeasureUnit

sealed interface ScaleResult {
    data class Success(val scaled: ScaledRecipe) : ScaleResult
    data class Failure(val reason: ScaleError) : ScaleResult
}

/**
 * The whole outcome of a rescale, as an immutable value: the UI draws this and computes nothing
 * itself, which is what lets the list refresh on every keystroke without inconsistent states.
 */
data class ScaledRecipe(
    val factor: Double,
    val servings: Double?,
    val lines: List<ScaledLine>,
    val warnings: List<ScaleWarning> = emptyList(),
    val bottleneckLineId: String? = null,
    val leftovers: List<Leftover> = emptyList(),
    val snapSuggestions: List<SnapOption> = emptyList(),
)

data class ScaledLine(
    val lineId: String,
    val ingredientId: String,
    val ingredientName: String,
    val originalQty: Double?,
    val originalUnit: MeasureUnit,
    val scaledQty: Double?,
    val displayUnit: MeasureUnit,
    val displayText: String,
    val isScaled: Boolean,
)

/** What is left over of an ingredient the user said they had. Pantry mode only. */
data class Leftover(
    val lineId: String,
    val qty: Double,
    val unit: MeasureUnit,
)

sealed interface ScaleWarning {

    /** 1.5 eggs. The exact value is kept so the UI can show what the maths actually said. */
    data class NonIntegerDiscrete(val lineId: String, val exact: Double) : ScaleWarning

    /** Less than half a gram: no scale in a kitchen measures that. */
    data class TooSmallToMeasure(val lineId: String) : ScaleWarning

    /** The user tried to constrain on "to taste". */
    data class NotScalable(val lineId: String) : ScaleWarning

    /**
     * Baking does not scale proportionally. [suggestedTinDiameterRatio] keeps the batter depth
     * constant, so a 24 cm tin at x1.5 becomes roughly 29 cm.
     */
    data class BakingTimeCaution(
        val factor: Double,
        val suggestedTinDiameterRatio: Double,
    ) : ScaleWarning
}

/**
 * A practical quantity the user can snap to. It carries the **factor** that quantity implies, not a
 * per-line override: accepting a snap re-runs the whole calculation so the recipe stays in
 * proportion.
 */
data class SnapOption(
    val lineId: String,
    val targetQty: Double,
    val resultingFactor: Double,
)

sealed interface ScaleError {
    data object NonPositiveFactor : ScaleError
    data object ConstraintOnApproximateUnit : ScaleError
    data object IncompatibleUnit : ScaleError
    data object NoServingsDefined : ScaleError
    data object EmptyRecipe : ScaleError
    data object UnknownLine : ScaleError
}
