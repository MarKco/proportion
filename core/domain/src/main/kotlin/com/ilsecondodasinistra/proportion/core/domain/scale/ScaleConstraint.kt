package com.ilsecondodasinistra.proportion.core.domain.scale

import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import kotlinx.serialization.Serializable

/**
 * What the user decided to hold fixed. Every constraint resolves to a single factor, after which
 * the rest of the calculation is identical.
 *
 * Serialisable because a saved [com.ilsecondodasinistra.proportion.core.model.ScaleVariant] stores
 * the constraint rather than the computed quantities.
 */
@Serializable
sealed interface ScaleConstraint {

    /** "Cook it for six people." Fractional because the pantry mode reports 4.3 servings. */
    @Serializable
    data class ByServings(val target: Double) : ScaleConstraint

    /** "I only have two eggs." */
    @Serializable
    data class ByIngredient(
        val lineId: String,
        val qty: Double,
        val unit: MeasureUnit,
    ) : ScaleConstraint

    /** "Double it." */
    @Serializable
    data class ByFactor(val factor: Double) : ScaleConstraint

    /** "What can I make with what I have?" — the limiting ingredient decides. */
    @Serializable
    data class ByAvailability(val have: List<AvailableAmount>) : ScaleConstraint
}

@Serializable
data class AvailableAmount(
    val lineId: String,
    val qty: Double,
    val unit: MeasureUnit,
)
