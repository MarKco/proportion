package com.ilsecondodasinistra.proportion.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.ilsecondodasinistra.proportion.core.database.entity.RecipeEntity
import com.ilsecondodasinistra.proportion.core.database.entity.RecipeIngredientEntity
import com.ilsecondodasinistra.proportion.core.database.entity.RecipeTagCrossRef
import com.ilsecondodasinistra.proportion.core.database.entity.RecipeWithRelations
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RecipeDao {

    @Transaction
    @Query("SELECT * FROM recipes WHERE deleted_at IS NULL ORDER BY updated_at DESC")
    abstract fun observeAll(): Flow<List<RecipeWithRelations>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id AND deleted_at IS NULL")
    abstract fun observeById(id: String): Flow<RecipeWithRelations?>

    /**
     * Unlike [observeById], sees a soft-deleted row too — folder sync (phase 10) needs this to
     * export a tombstone file for a recipe the UI can no longer see.
     */
    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    abstract suspend fun findByIdIncludingDeleted(id: String): RecipeWithRelations?

    /**
     * The three filters combine with AND. Within the ingredient filter a recipe must contain
     * **every** selected ingredient; within the tag filter it needs **any** of the selected tags,
     * because picking "first course" and "dessert" means either, not both at once.
     *
     * [matchingBuiltInIds] exists because `ingredients.normalised_name` is frozen to a built-in
     * row's raw English key (see `ProPortionDatabase`'s seeding) — this SQL-only `LIKE` can never
     * match a built-in ingredient by its resolved, current-language name. The caller (which has
     * access to the namer) resolves that match in Kotlin and passes the matching ids here; an
     * empty list (the default, and always what's passed when [query] is blank) changes nothing.
     *
     * Prefer the [observeFiltered] extension, which computes [ingredientCount] for you.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM recipes AS r
        WHERE r.deleted_at IS NULL
        AND (
            :query = ''
            OR LOWER(r.title) LIKE '%' || :query || '%'
            OR LOWER(IFNULL(r.notes, '')) LIKE '%' || :query || '%'
            OR EXISTS (
                SELECT 1 FROM recipe_ingredients ri
                JOIN ingredients i ON i.id = ri.ingredient_id
                WHERE ri.recipe_id = r.id
                  AND (i.normalised_name LIKE '%' || :query || '%' OR ri.ingredient_id IN (:matchingBuiltInIds))
            )
        )
        AND (
            :tagCount = 0
            OR EXISTS (
                SELECT 1 FROM recipe_tags rt
                WHERE rt.recipe_id = r.id AND rt.tag_id IN (:tagIds)
            )
        )
        AND (
            :ingredientCount = 0
            OR (
                SELECT COUNT(DISTINCT ri2.ingredient_id) FROM recipe_ingredients ri2
                WHERE ri2.recipe_id = r.id AND ri2.ingredient_id IN (:ingredientIds)
            ) = :ingredientCount
        )
        ORDER BY
            CASE WHEN :sort = 'ALPHABETICAL' THEN LOWER(r.title) END ASC,
            CASE WHEN :sort = 'MOST_COOKED' THEN r.cook_count END DESC,
            r.updated_at DESC
        """,
    )
    abstract fun filtered(
        query: String,
        tagIds: List<String>,
        tagCount: Int,
        ingredientIds: List<String>,
        ingredientCount: Int,
        sort: String,
        matchingBuiltInIds: List<String> = emptyList(),
    ): Flow<List<RecipeWithRelations>>

    @Transaction
    open suspend fun upsertRecipe(
        recipe: RecipeEntity,
        lines: List<RecipeIngredientEntity>,
        tagIds: List<String>,
    ) {
        upsert(recipe)
        deleteLinesOf(recipe.id)
        insertLines(lines)
        deleteTagsOf(recipe.id)
        insertTags(tagIds.map { RecipeTagCrossRef(recipeId = recipe.id, tagId = it) })
    }

    @Upsert
    abstract suspend fun upsert(recipe: RecipeEntity)

    /** Soft-delete: sets the tombstone instead of removing the row — see phase 10 (folder sync). */
    @Query("UPDATE recipes SET deleted_at = :now WHERE id = :id")
    abstract suspend fun softDeleteRecipe(id: String, now: Long)

    /** Actually removes the row. Only the sync cleanup (Task 5) calls this, never the UI. */
    @Query("DELETE FROM recipes WHERE id = :id")
    abstract suspend fun hardDeleteRecipe(id: String)

    @Query("UPDATE recipes SET is_favourite = :favourite, updated_at = :now WHERE id = :id")
    abstract suspend fun setFavourite(id: String, favourite: Boolean, now: Long)

    @Query(
        "UPDATE recipes SET cook_count = cook_count + 1, last_cooked_at = :at WHERE id = :id",
    )
    abstract suspend fun markCooked(id: String, at: Long)

    @Query("SELECT COUNT(*) FROM recipe_ingredients")
    abstract suspend fun countLines(): Int

    /** Which of these ids the library already holds — the basis of the import preview. */
    @Query("SELECT id FROM recipes WHERE id IN (:ids)")
    abstract suspend fun existingIds(ids: List<String>): List<String>

    /** Folder sync (phase 10) only: every recipe, tombstones included — the export-everything pass. */
    @Query("SELECT id FROM recipes")
    abstract suspend fun allIds(): List<String>

    /** Folder sync (phase 10) only: tombstones old enough for the sync cleanup pass to hard-delete. */
    @Query("SELECT id FROM recipes WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff")
    abstract suspend fun tombstonesOlderThan(cutoff: Long): List<String>

    @Query("DELETE FROM recipes")
    abstract suspend fun deleteAllRecipes()

    @Query("SELECT COUNT(*) FROM recipes WHERE deleted_at IS NULL")
    abstract fun observeRecipeCount(): Flow<Int>

    @Query("DELETE FROM recipe_ingredients WHERE recipe_id = :recipeId")
    protected abstract suspend fun deleteLinesOf(recipeId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertLines(lines: List<RecipeIngredientEntity>)

    @Query("DELETE FROM recipe_tags WHERE recipe_id = :recipeId")
    protected abstract suspend fun deleteTagsOf(recipeId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertTags(refs: List<RecipeTagCrossRef>)
}

/** Convenience wrapper: the counts the query needs are derived from the lists themselves. */
fun RecipeDao.observeFiltered(
    query: String = "",
    tagIds: List<String> = emptyList(),
    ingredientIds: List<String> = emptyList(),
    sort: String = "RECENT",
): Flow<List<RecipeWithRelations>> = filtered(
    query = query.lowercase().trim(),
    tagIds = tagIds,
    tagCount = tagIds.size,
    ingredientIds = ingredientIds,
    ingredientCount = ingredientIds.size,
    sort = sort,
)
