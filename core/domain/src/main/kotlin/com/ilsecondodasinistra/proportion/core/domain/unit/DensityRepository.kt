package com.ilsecondodasinistra.proportion.core.domain.unit

/**
 * Supplies the density of an ingredient in grams per millilitre.
 *
 * **v2 preparation.** v1 binds [NoDensityRepository], so mass <-> volume conversion is always
 * refused. When the density table lands, only the Hilt binding changes.
 */
interface DensityRepository {
    suspend fun densityGramsPerMl(ingredient: IngredientRef): Double?
}

/** The v1 binding: no densities are known. */
class NoDensityRepository : DensityRepository {
    override suspend fun densityGramsPerMl(ingredient: IngredientRef): Double? = null
}
