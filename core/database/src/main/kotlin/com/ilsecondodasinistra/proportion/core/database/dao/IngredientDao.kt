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
        WHERE id IN (
            SELECT DISTINCT ri.ingredient_id FROM recipe_ingredients ri
            JOIN recipes r ON r.id = ri.recipe_id
            WHERE r.deleted_at IS NULL
        )
        ORDER BY name
        """,
    )
    fun observeInUse(): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM ingredients WHERE key = :key LIMIT 1")
    suspend fun findByKey(key: String): IngredientEntity?

    @Query("SELECT * FROM ingredients WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): IngredientEntity?

    /** Folder sync (phase 10) only: literal rows are what travels — built-ins never do. */
    @Query("SELECT id, updated_at AS updatedAt FROM ingredients WHERE is_built_in = 0")
    suspend fun allLiteralIdsWithUpdatedAt(): List<SyncableEntityId>

    @Query("SELECT * FROM ingredients WHERE normalised_name = :normalisedName LIMIT 1")
    suspend fun findByNormalisedName(normalisedName: String): IngredientEntity?

    @Query(
        """
        SELECT COUNT(*) FROM ingredients
        WHERE id IN (
            SELECT DISTINCT ri.ingredient_id FROM recipe_ingredients ri
            JOIN recipes r ON r.id = ri.recipe_id
            WHERE r.deleted_at IS NULL
        )
        """,
    )
    fun observeInUseCount(): Flow<Int>

    @Upsert
    suspend fun upsertAll(ingredients: List<IngredientEntity>)

    @Query("UPDATE ingredients SET density_g_per_ml = :density, updated_at = :now WHERE id = :id")
    suspend fun updateDensity(id: String, density: Double, now: Long)

    @Query("UPDATE ingredients SET item_weight_grams = :itemWeightGrams, updated_at = :now WHERE id = :id")
    suspend fun updateItemWeight(id: String, itemWeightGrams: Double, now: Long)

    @Query("DELETE FROM ingredients WHERE id = :id")
    suspend fun delete(id: String)
}
