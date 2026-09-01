package com.ilsecondodasinistra.proportion.core.domain.repository

import com.ilsecondodasinistra.proportion.core.domain.scale.ScaleConstraint
import com.ilsecondodasinistra.proportion.core.model.ScaleVariant
import kotlinx.coroutines.flow.Flow

interface ScaleVariantRepository {
    fun observeForRecipe(recipeId: String): Flow<List<ScaleVariant>>
    fun readConstraint(variant: ScaleVariant): ScaleConstraint
    suspend fun save(recipeId: String, label: String, constraint: ScaleConstraint, asDefault: Boolean): String
    suspend fun delete(id: String)
}
