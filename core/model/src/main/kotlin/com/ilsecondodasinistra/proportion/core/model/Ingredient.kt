package com.ilsecondodasinistra.proportion.core.model

/**
 * An entry in the reusable ingredient catalogue.
 *
 * @param normalisedName lowercase, trimmed and accent-folded; the key used for lookup, filtering
 * and de-duplication on import.
 * @param densityGramsPerMl **v2 preparation, unused in v1.** The column exists from schema version
 * one so that adding mass <-> volume conversion later needs no migration. Do not remove it.
 */
data class Ingredient(
    val id: String,
    val name: String,
    val normalisedName: String,
    val defaultUnit: MeasureUnit = MeasureUnit.GRAM,
    val densityGramsPerMl: Double? = null,
)
