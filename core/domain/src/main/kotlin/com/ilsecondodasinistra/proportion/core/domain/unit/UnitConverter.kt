package com.ilsecondodasinistra.proportion.core.domain.unit

import com.ilsecondodasinistra.proportion.core.model.Ingredient
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.UnitCategory

/**
 * A lightweight reference to an ingredient, so the converter never has to know about persistence.
 *
 * @param densityGramsPerMl grams per millilitre, needed to cross MASS <-> VOLUME. Null when unknown.
 * @param itemWeightGrams grams per one [defaultUnit], needed to cross COUNT <-> MASS/VOLUME. Only
 * meaningful when [defaultUnit] is a COUNT unit; null when unknown.
 */
data class IngredientRef(
    val id: String,
    val normalisedName: String,
    val defaultUnit: MeasureUnit? = null,
    val densityGramsPerMl: Double? = null,
    val itemWeightGrams: Double? = null,
)

/** The reference a caller already holding a full [Ingredient] passes to [UnitConverter.convert]. */
fun Ingredient.toRef(): IngredientRef = IngredientRef(
    id = id,
    normalisedName = normalisedName,
    defaultUnit = defaultUnit,
    densityGramsPerMl = densityGramsPerMl,
    itemWeightGrams = itemWeightGrams,
)

/** What is missing from an [IngredientRef] to convert between two units, if anything. */
enum class DensityRequirement {
    /** Same category, or one side not scalable: [UnitConverter.convert] already works. */
    NONE,

    /** Crossing MASS <-> VOLUME: needs [IngredientRef.densityGramsPerMl]. */
    DENSITY,

    /** Crossing COUNT <-> MASS or COUNT <-> VOLUME: needs [IngredientRef.itemWeightGrams]. */
    ITEM_WEIGHT,

    /** Crossing COUNT <-> VOLUME: needs both. */
    BOTH,

    /** Structurally impossible regardless of data (e.g. a clove vs a slice, or an approximate unit). */
    UNSUPPORTED,
}

interface UnitConverter {

    /**
     * @param ingredient the ingredient the quantity belongs to. Required to cross a
     * [MeasureUnit.category] boundary (see [DensityRequirement]); ignored otherwise.
     * @return the converted quantity, or null when the conversion is not possible — either because
     * it is structurally unsupported, or because [ingredient] is missing the data it would take.
     */
    fun convert(
        qty: Double,
        from: MeasureUnit,
        to: MeasureUnit,
        ingredient: IngredientRef? = null,
    ): Double?
}

/**
 * Tells the UI what [ingredient] is actually missing before [UnitConverter.convert] can turn
 * [from] into [to] — [DensityRequirement.NONE] when it already carries everything needed (the
 * caller offers no prompt and [UnitConverter.convert] already succeeds), a specific field when it
 * would be enough to ask for it, or [DensityRequirement.UNSUPPORTED] when no answer would help
 * (e.g. a clove is not a slice, or the COUNT unit involved isn't [ingredient]'s own).
 */
fun requirementFor(from: MeasureUnit, to: MeasureUnit, ingredient: IngredientRef?): DensityRequirement {
    if (!from.isScalable || !to.isScalable) return DensityRequirement.UNSUPPORTED
    if (from.category == to.category) {
        return if (from.category == UnitCategory.COUNT && from != to) {
            DensityRequirement.UNSUPPORTED
        } else {
            DensityRequirement.NONE
        }
    }

    val categories = setOf(from.category, to.category)
    val countUnit = listOf(from, to).firstOrNull { it.category == UnitCategory.COUNT }
    if (countUnit != null && countUnit != ingredient?.defaultUnit) return DensityRequirement.UNSUPPORTED

    val needsDensity = UnitCategory.VOLUME in categories &&
        (UnitCategory.MASS in categories || UnitCategory.COUNT in categories)
    val needsItemWeight = UnitCategory.COUNT in categories

    val missingDensity = needsDensity && ingredient?.densityGramsPerMl == null
    val missingItemWeight = needsItemWeight && ingredient?.itemWeightGrams == null

    return when {
        missingDensity && missingItemWeight -> DensityRequirement.BOTH
        missingDensity -> DensityRequirement.DENSITY
        missingItemWeight -> DensityRequirement.ITEM_WEIGHT
        else -> DensityRequirement.NONE
    }
}

/**
 * Converts inside a single category directly; across MASS/VOLUME/COUNT via grams as the hub, using
 * [IngredientRef.densityGramsPerMl] and [IngredientRef.itemWeightGrams] when [ingredient] carries
 * them. Grams to millilitres with no known density is refused rather than guessed: 100 g of flour
 * is not 100 ml, and pretending otherwise is the fastest way to ruin a recipe.
 */
class DefaultUnitConverter : UnitConverter {

    override fun convert(
        qty: Double,
        from: MeasureUnit,
        to: MeasureUnit,
        ingredient: IngredientRef?,
    ): Double? {
        if (!from.isScalable || !to.isScalable) return null
        if (from.category == to.category) {
            // A clove is not a slice, even though both are counted in pieces.
            if (from.category == UnitCategory.COUNT && from != to) return null
            return qty * from.baseFactor / to.baseFactor
        }
        val grams = toGrams(qty, from, ingredient) ?: return null
        return fromGrams(grams, to, ingredient)
    }

    private fun toGrams(qty: Double, unit: MeasureUnit, ingredient: IngredientRef?): Double? =
        when (unit.category) {
            UnitCategory.MASS -> qty * unit.baseFactor
            UnitCategory.VOLUME -> ingredient?.densityGramsPerMl?.let { qty * unit.baseFactor * it }
            UnitCategory.COUNT -> if (unit == ingredient?.defaultUnit) {
                ingredient.itemWeightGrams?.let { qty * it }
            } else {
                null
            }
            UnitCategory.APPROXIMATE -> null
        }

    private fun fromGrams(grams: Double, unit: MeasureUnit, ingredient: IngredientRef?): Double? =
        when (unit.category) {
            UnitCategory.MASS -> grams / unit.baseFactor
            UnitCategory.VOLUME -> ingredient?.densityGramsPerMl?.let { grams / it / unit.baseFactor }
            UnitCategory.COUNT -> if (unit == ingredient?.defaultUnit) {
                ingredient.itemWeightGrams?.let { grams / it }
            } else {
                null
            }
            UnitCategory.APPROXIMATE -> null
        }
}
