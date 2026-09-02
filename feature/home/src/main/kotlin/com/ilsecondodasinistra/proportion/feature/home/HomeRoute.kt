package com.ilsecondodasinistra.proportion.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

fun NavGraphBuilder.homeScreen(
    onRecipeClick: (String) -> Unit,
    onCook: (String) -> Unit,
    onAddRecipe: () -> Unit,
) {
    composable<HomeRoute> {
        HomeRoute(onRecipeClick = onRecipeClick, onCook = onCook, onAddRecipe = onAddRecipe)
    }
}
