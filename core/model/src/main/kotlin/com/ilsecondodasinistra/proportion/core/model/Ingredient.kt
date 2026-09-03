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
 * @param densityGramsPerMl **v2 preparation, unused in v1.** The column exists from schema version
 * one so that adding mass <-> volume conversion later needs no migration. Do not remove it.
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
) {
    init {
        require(isBuiltIn == (key != null)) {
            "a built-in ingredient carries a key and vice versa"
        }
    }
}
