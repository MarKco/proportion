package com.ilsecondodasinistra.proportion.feature.editor.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ilsecondodasinistra.proportion.feature.editor.EditorRoute
import kotlinx.serialization.Serializable

/** One route for both cases: a null id means a new recipe. */
@Serializable
data class EditorRouteKey(val recipeId: String? = null)

fun NavController.navigateToNewRecipe() = navigate(EditorRouteKey())

fun NavController.navigateToEditRecipe(recipeId: String) = navigate(EditorRouteKey(recipeId))

fun NavGraphBuilder.editorScreen(onDone: () -> Unit) {
    composable<EditorRouteKey> { EditorRoute(onDone = onDone) }
}
