package com.ilsecondodasinistra.proportion.core.data

import com.ilsecondodasinistra.proportion.core.database.entity.IngredientEntity
import com.ilsecondodasinistra.proportion.core.database.entity.LineWithIngredient
import com.ilsecondodasinistra.proportion.core.database.entity.RecipeEntity
import com.ilsecondodasinistra.proportion.core.database.entity.RecipeIngredientEntity
import com.ilsecondodasinistra.proportion.core.database.entity.RecipeWithRelations
import com.ilsecondodasinistra.proportion.core.database.entity.ScaleVariantEntity
import com.ilsecondodasinistra.proportion.core.database.entity.ShoppingItemEntity
import com.ilsecondodasinistra.proportion.core.database.entity.TagEntity
import com.ilsecondodasinistra.proportion.core.domain.BuiltInIngredientNamer
import com.ilsecondodasinistra.proportion.core.domain.IngredientNames
import com.ilsecondodasinistra.proportion.core.model.Ingredient
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.RecipeIngredient
import com.ilsecondodasinistra.proportion.core.model.ScaleVariant
import com.ilsecondodasinistra.proportion.core.model.ShoppingItem
import com.ilsecondodasinistra.proportion.core.model.Tag

fun IngredientEntity.toDomain(namer: BuiltInIngredientNamer): Ingredient {
    val resolvedName = if (isBuiltIn) namer.name(key!!) else name
    return Ingredient(
        id = id,
        key = key,
        name = resolvedName,
        normalisedName = if (isBuiltIn) IngredientNames.normalise(resolvedName) else normalisedName,
        isBuiltIn = isBuiltIn,
        defaultUnit = defaultUnit,
        category = category,
        densityGramsPerMl = densityGramsPerMl,
        itemWeightGrams = itemWeightGrams,
    )
}

fun Ingredient.toEntity() = IngredientEntity(
    id = id,
    key = key,
    name = name,
    normalisedName = normalisedName,
    isBuiltIn = isBuiltIn,
    defaultUnit = defaultUnit,
    category = category,
    densityGramsPerMl = densityGramsPerMl,
    itemWeightGrams = itemWeightGrams,
)

fun TagEntity.toDomain() = Tag(
    id = id,
    key = key,
    name = name,
    isBuiltIn = isBuiltIn,
    colorIndex = colorIndex,
)

fun Tag.toEntity() = TagEntity(
    id = id,
    key = key,
    name = name,
    isBuiltIn = isBuiltIn,
    colorIndex = colorIndex,
)

fun LineWithIngredient.toDomain(namer: BuiltInIngredientNamer) = RecipeIngredient(
    id = line.id,
    ingredient = ingredient.toDomain(namer),
    position = line.position,
    quantity = line.quantity,
    unit = line.unit,
    displayText = line.displayText,
    note = line.note,
)

fun RecipeIngredient.toEntity(recipeId: String) = RecipeIngredientEntity(
    id = id,
    recipeId = recipeId,
    ingredientId = ingredient.id,
    position = position,
    quantity = quantity,
    unit = unit,
    displayText = displayText,
    note = note,
)

fun RecipeWithRelations.toDomain(namer: BuiltInIngredientNamer) = Recipe(
    id = recipe.id,
    title = recipe.title,
    servings = recipe.servings,
    steps = recipe.steps,
    ingredients = lines.sortedBy { it.line.position }.map { it.toDomain(namer) },
    tags = tags.map { it.toDomain() },
    notes = recipe.notes,
    isFavourite = recipe.isFavourite,
    cookCount = recipe.cookCount,
    lastCookedAt = recipe.lastCookedAt,
    createdAt = recipe.createdAt,
    updatedAt = recipe.updatedAt,
)

fun Recipe.toEntity() = RecipeEntity(
    id = id,
    title = title,
    servings = servings,
    steps = steps,
    notes = notes,
    isFavourite = isFavourite,
    cookCount = cookCount,
    lastCookedAt = lastCookedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ScaleVariantEntity.toDomain() = ScaleVariant(
    id = id,
    recipeId = recipeId,
    label = label,
    constraintPayload = constraintPayload,
    isDefault = isDefault,
    createdAt = createdAt,
)

fun ScaleVariant.toEntity() = ScaleVariantEntity(
    id = id,
    recipeId = recipeId,
    label = label,
    constraintPayload = constraintPayload,
    isDefault = isDefault,
    createdAt = createdAt,
)

fun ShoppingItemEntity.toDomain(ingredient: Ingredient) = ShoppingItem(
    id = id,
    ingredient = ingredient,
    quantity = quantity,
    unit = unit,
    isChecked = isChecked,
    sourceRecipeIds = sourceRecipeIds,
)
