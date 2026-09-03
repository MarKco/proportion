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
    /** v2 preparation: created in schema 1, written by nobody in v1. Do not drop. */
    @ColumnInfo(name = "density_g_per_ml") val densityGramsPerMl: Double? = null,
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
