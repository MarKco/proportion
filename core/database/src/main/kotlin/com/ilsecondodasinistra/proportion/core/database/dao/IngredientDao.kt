package com.ilsecondodasinistra.proportion.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ilsecondodasinistra.proportion.core.database.entity.IngredientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientDao {

    @Query("SELECT * FROM ingredients ORDER BY name")
    fun observeAll(): Flow<List<IngredientEntity>>

    /**
     * Only ingredients some recipe actually uses. An ingredient nobody references any more stays in
     * the catalogue but must not clutter the filter sheet.
     */
    @Query(
        """
        SELECT * FROM ingredients
        WHERE id IN (SELECT DISTINCT ingredient_id FROM recipe_ingredients)
        ORDER BY name
        """,
    )
    fun observeInUse(): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM ingredients WHERE key = :key LIMIT 1")
    suspend fun findByKey(key: String): IngredientEntity?

    @Query("SELECT * FROM ingredients WHERE normalised_name = :normalisedName LIMIT 1")
    suspend fun findByNormalisedName(normalisedName: String): IngredientEntity?

    @Query("SELECT COUNT(*) FROM ingredients WHERE id IN (SELECT DISTINCT ingredient_id FROM recipe_ingredients)")
    fun observeInUseCount(): Flow<Int>

    @Upsert
    suspend fun upsertAll(ingredients: List<IngredientEntity>)

    @Query("DELETE FROM ingredients WHERE id = :id")
    suspend fun delete(id: String)
}
