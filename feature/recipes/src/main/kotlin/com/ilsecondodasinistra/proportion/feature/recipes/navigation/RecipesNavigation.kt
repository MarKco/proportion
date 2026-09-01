package com.ilsecondodasinistra.proportion.feature.recipes.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ilsecondodasinistra.proportion.feature.recipes.detail.RecipeDetailRoute
import com.ilsecondodasinistra.proportion.feature.recipes.list.RecipeListRoute
import kotlinx.serialization.Serializable

/** Type-safe routes: the compiler, not a string, decides what arguments a destination takes. */
@Serializable
data object RecipesRoute

@Serializable
data class RecipeDetailRouteKey(val recipeId: String)

fun NavController.navigateToRecipeDetail(recipeId: String) =
    navigate(RecipeDetailRouteKey(recipeId))

fun NavGraphBuilder.recipesScreen(
    onRecipeClick: (String) -> Unit,
    onAddRecipe: () -> Unit,
) {
    composable<RecipesRoute> {
        RecipeListRoute(onRecipeClick = onRecipeClick, onAddRecipe = onAddRecipe)
    }
}

fun NavGraphBuilder.recipeDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onCook: (String) -> Unit,
) {
    composable<RecipeDetailRouteKey> {
        RecipeDetailRoute(onBack = onBack, onEdit = onEdit, onCook = onCook)
    }
}
