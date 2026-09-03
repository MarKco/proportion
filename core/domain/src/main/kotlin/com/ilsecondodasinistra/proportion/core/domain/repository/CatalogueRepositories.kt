package com.ilsecondodasinistra.proportion.core.domain.repository

import com.ilsecondodasinistra.proportion.core.model.Ingredient
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Tag
import kotlinx.coroutines.flow.Flow

interface IngredientRepository {
    fun observeAll(): Flow<List<Ingredient>>

    /** Only the ingredients some recipe actually uses: this is what the filter sheet shows. */
    fun observeInUse(): Flow<List<Ingredient>>

    /** Looks the name up by its normalised form and creates the ingredient when it is new. */
    suspend fun findOrCreate(name: String, defaultUnit: MeasureUnit): Ingredient

    /**
     * Persists a density and/or item weight the user just typed into the "unknown density" prompt,
     * so the same ingredient never asks again. Either argument may be null when that value was not
     * part of what was asked for.
     */
    suspend fun setDensityData(id: String, densityGramsPerMl: Double?, itemWeightGrams: Double?)
}

interface TagRepository {
    fun observeAll(): Flow<List<Tag>>
    suspend fun findOrCreateUserTag(name: String): Tag
    suspend fun deleteUserTag(id: String)
}
