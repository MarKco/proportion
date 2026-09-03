package com.ilsecondodasinistra.proportion.core.model

/**
 * An entry in the reusable ingredient catalogue.
 *
 * Built-in ingredients carry a stable [key] resolved through `strings.xml` (mirrors [Tag]), so
 * [name] and [normalisedName] always hold the current app language by the time an `Ingredient`
 * reaches here — a caller never needs to know whether it read a built-in or a user-created row.
 *
 * @param normalisedName lowercase, trimmed and accent-folded; the key used for lookup, filtering
 * and de-duplication on import.
 * @param densityGramsPerMl grams per millilitre, used to convert this ingredient between MASS and
 * VOLUME units. Null when unknown (typically a user-created ingredient that has never needed it).
 * @param itemWeightGrams grams per one [defaultUnit], used to convert a COUNT-category ingredient
 * (e.g. "1 egg", "1 slice") to and from MASS/VOLUME. Only meaningful when [defaultUnit] is a COUNT
 * unit; null when unknown.
 * @param updatedAt only meaningful for a literal (non built-in) row — built-ins are seeded
 * identically everywhere and never sync. Used by folder sync (phase 10) to resolve a conflict
 * between two devices in favour of the more recent write.
 */
data class Ingredient(
    val id: String,
    val key: String?,
    val name: String,
    val normalisedName: String,
    val isBuiltIn: Boolean,
    val defaultUnit: MeasureUnit = MeasureUnit.GRAM,
    val category: IngredientCategory? = null,
    val densityGramsPerMl: Double? = null,
    val itemWeightGrams: Double? = null,
    val updatedAt: Long = 0L,
) {
    init {
        require(isBuiltIn == (key != null)) {
            "a built-in ingredient carries a key and vice versa"
        }
    }
}
