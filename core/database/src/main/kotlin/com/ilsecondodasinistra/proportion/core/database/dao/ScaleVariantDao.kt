package com.ilsecondodasinistra.proportion.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.ilsecondodasinistra.proportion.core.database.entity.ScaleVariantEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ScaleVariantDao {

    @Query("SELECT * FROM scale_variants WHERE recipe_id = :recipeId ORDER BY created_at DESC")
    abstract fun observeForRecipe(recipeId: String): Flow<List<ScaleVariantEntity>>

    /** At most one default per recipe, so setting one clears the others in the same transaction. */
    @Transaction
    open suspend fun upsertVariant(variant: ScaleVariantEntity) {
        if (variant.isDefault) clearDefaults(variant.recipeId)
        upsert(variant)
    }

    @Upsert
    abstract suspend fun upsert(variant: ScaleVariantEntity)

    @Query("UPDATE scale_variants SET is_default = 0 WHERE recipe_id = :recipeId")
    abstract suspend fun clearDefaults(recipeId: String)

    @Query("DELETE FROM scale_variants WHERE id = :id")
    abstract suspend fun delete(id: String)
}
