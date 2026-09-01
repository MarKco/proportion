package com.ilsecondodasinistra.proportion.core.domain.unit

import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.UnitCategory

/**
 * A lightweight reference to an ingredient, so the converter never has to know about persistence.
 */
data class IngredientRef(
    val id: String,
    val normalisedName: String,
)

interface UnitConverter {

    /**
     * @param ingredient unused in v1. It is part of the signature from the start so that adding
     * density-based mass <-> volume conversion in v2 changes only the implementation, never the
     * call sites.
     * @return the converted quantity, or null when the conversion is not possible.
     */
    fun convert(
        qty: Double,
        from: MeasureUnit,
        to: MeasureUnit,
        ingredient: IngredientRef? = null,
    ): Double?
}

/**
 * Converts inside a single category only.
 *
 * Grams to millilitres is refused rather than guessed: 100 g of flour is not 100 ml, and pretending
 * otherwise is the fastest way to ruin a recipe.
 */
class DefaultUnitConverter : UnitConverter {

    override fun convert(
        qty: Double,
        from: MeasureUnit,
        to: MeasureUnit,
        ingredient: IngredientRef?,
    ): Double? {
        if (!from.isScalable || !to.isScalable) return null
        if (from.category != to.category) return null
        // A clove is not a slice, even though both are counted in pieces.
        if (from.category == UnitCategory.COUNT && from != to) return null
        return qty * from.baseFactor / to.baseFactor
    }
}
