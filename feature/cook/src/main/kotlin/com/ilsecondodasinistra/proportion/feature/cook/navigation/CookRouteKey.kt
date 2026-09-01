package com.ilsecondodasinistra.proportion.feature.cook.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ilsecondodasinistra.proportion.feature.cook.CookRoute
import kotlinx.serialization.Serializable

@Serializable
data class CookRouteKey(val recipeId: String)

fun NavController.navigateToCook(recipeId: String) = navigate(CookRouteKey(recipeId))

fun NavGraphBuilder.cookScreen(onBack: () -> Unit) {
    composable<CookRouteKey> { CookRoute(onBack = onBack) }
}
