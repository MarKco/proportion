package com.ilsecondodasinistra.proportion.core.database

import com.ilsecondodasinistra.proportion.core.model.IngredientCategory
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import kotlinx.serialization.Serializable

@Serializable
data class IngredientSeed(
    val key: String,
    val defaultUnit: MeasureUnit,
    val category: IngredientCategory,
    val density: Double? = null,
    val itemWeightGrams: Double? = null,
)
