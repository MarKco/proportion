package com.ilsecondodasinistra.proportion.core.database.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class LineWithIngredient(
    @Embedded val line: RecipeIngredientEntity,
    @Relation(parentColumn = "ingredient_id", entityColumn = "id")
    val ingredient: IngredientEntity,
)

data class RecipeWithRelations(
    @Embedded val recipe: RecipeEntity,

    @Relation(entity = RecipeIngredientEntity::class, parentColumn = "id", entityColumn = "recipe_id")
    val lines: List<LineWithIngredient>,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = RecipeTagCrossRef::class,
            parentColumn = "recipe_id",
            entityColumn = "tag_id",
        ),
    )
    val tags: List<TagEntity>,

    @Relation(parentColumn = "id", entityColumn = "recipe_id")
    val variants: List<ScaleVariantEntity> = emptyList(),
)
