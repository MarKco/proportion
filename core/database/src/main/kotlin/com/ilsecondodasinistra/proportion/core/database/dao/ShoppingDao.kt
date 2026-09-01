package com.ilsecondodasinistra.proportion.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ilsecondodasinistra.proportion.core.database.entity.ShoppingItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {

    @Query("SELECT * FROM shopping_items")
    fun observeItems(): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE ingredient_id = :ingredientId")
    suspend fun itemsFor(ingredientId: String): List<ShoppingItemEntity>

    @Upsert
    suspend fun upsert(item: ShoppingItemEntity)

    @Query("UPDATE shopping_items SET is_checked = :checked WHERE id = :id")
    suspend fun setChecked(id: String, checked: Boolean)

    @Query("DELETE FROM shopping_items WHERE is_checked = 1")
    suspend fun clearChecked()

    @Query("DELETE FROM shopping_items")
    suspend fun clearAll()
}
