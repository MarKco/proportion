package com.ilsecondodasinistra.proportion.core.domain.repository

import com.ilsecondodasinistra.proportion.core.domain.scale.ScaledLine
import com.ilsecondodasinistra.proportion.core.model.ShoppingItem
import kotlinx.coroutines.flow.Flow

interface ShoppingRepository {
    fun observeItems(): Flow<List<ShoppingItem>>

    /**
     * Adds already scaled lines. Amounts merge with what is there when the units share a category,
     * and stay as separate lines when they do not.
     */
    suspend fun addScaled(lines: List<ScaledLine>, recipeId: String)
    suspend fun setChecked(id: String, checked: Boolean)
    suspend fun clearChecked()
    suspend fun clearAll()
}
