package com.ilsecondodasinistra.proportion.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ilsecondodasinistra.proportion.core.model.IngredientCategory
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val servings: Int?,
    val steps: List<String>,
    val notes: String? = null,
    @ColumnInfo(name = "is_favourite") val isFavourite: Boolean = false,
    @ColumnInfo(name = "cook_count") val cookCount: Int = 0,
    @ColumnInfo(name = "last_cooked_at") val lastCookedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(
    tableName = "ingredients",
    indices = [Index(value = ["normalised_name"], unique = true)],
)
data class IngredientEntity(
    @PrimaryKey val id: String,
    val key: String?,
    val name: String,
    @ColumnInfo(name = "normalised_name") val normalisedName: String,
    @ColumnInfo(name = "is_built_in") val isBuiltIn: Boolean = false,
    @ColumnInfo(name = "default_unit") val defaultUnit: MeasureUnit = MeasureUnit.GRAM,
    val category: IngredientCategory? = null,
    @ColumnInfo(name = "density_g_per_ml") val densityGramsPerMl: Double? = null,
    @ColumnInfo(name = "item_weight_grams") val itemWeightGrams: Double? = null,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = 0L,
)

@Entity(
    tableName = "recipe_ingredients",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipe_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredient_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("recipe_id"), Index("ingredient_id")],
)
data class RecipeIngredientEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    @ColumnInfo(name = "ingredient_id") val ingredientId: String,
    val position: Int,
    val quantity: Double?,
    val unit: MeasureUnit,
    @ColumnInfo(name = "display_text") val displayText: String? = null,
    val note: String? = null,
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    /** Set for built-in tags; resolved through strings.xml so they follow the app language. */
    val key: String?,
    /** Set for user tags; literal text, never translated. */
    val name: String?,
    @ColumnInfo(name = "is_built_in") val isBuiltIn: Boolean,
    @ColumnInfo(name = "color_index") val colorIndex: Int = 0,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = 0L,
)

@Entity(
    tableName = "recipe_tags",
    primaryKeys = ["recipe_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipe_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipe_id"), Index("tag_id")],
)
data class RecipeTagCrossRef(
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    @ColumnInfo(name = "tag_id") val tagId: String,
)

@Entity(
    tableName = "scale_variants",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipe_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipe_id")],
)
data class ScaleVariantEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    val label: String,
    /** The serialised constraint, not the computed quantities. */
    @ColumnInfo(name = "constraint_payload") val constraintPayload: String,
    @ColumnInfo(name = "is_default") val isDefault: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0L,
)

/**
 * Folder sync (phase 10) dirty-check cache: the `updated_at` a local row had the last time it was
 * successfully written to the sync folder. A push is skipped when the row's current `updated_at`
 * still matches — an exact-equality check, never "newer than", so device clock skew can't make it
 * skip a push that was actually needed.
 */
@Entity(tableName = "sync_export_cache")
data class SyncExportCacheEntity(
    @PrimaryKey @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "exported_updated_at") val exportedUpdatedAt: Long,
)

/**
 * Folder sync (phase 10) dirty-check cache: the SAF `lastModified()` a remote file had the last
 * time this device read and processed it. A pull is skipped when the file's current mtime still
 * matches — never skipped when `lastModified()` is unknown (`0`), which some providers return.
 */
@Entity(tableName = "sync_seen_file")
data class SyncSeenFileEntity(
    @PrimaryKey @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "last_modified") val lastModified: Long,
)

@Entity(
    tableName = "shopping_items",
    foreignKeys = [
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredient_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("ingredient_id")],
)
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "ingredient_id") val ingredientId: String,
    val quantity: Double?,
    val unit: MeasureUnit,
    @ColumnInfo(name = "is_checked") val isChecked: Boolean = false,
    @ColumnInfo(name = "source_recipe_ids") val sourceRecipeIds: List<String> = emptyList(),
)
