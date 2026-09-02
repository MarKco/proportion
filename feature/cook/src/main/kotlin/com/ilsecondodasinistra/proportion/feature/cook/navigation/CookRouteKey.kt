package com.ilsecondodasinistra.proportion.feature.cook.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaleConstraint
import com.ilsecondodasinistra.proportion.feature.cook.CookRoute
import com.ilsecondodasinistra.proportion.feature.cook.CookingModeRoute
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CookRouteKey(val recipeId: String)

fun NavController.navigateToCook(recipeId: String) = navigate(CookRouteKey(recipeId))

fun NavGraphBuilder.cookScreen(onBack: () -> Unit, onCookingMode: (String, ScaleConstraint?) -> Unit) {
    composable<CookRouteKey> { CookRoute(onBack = onBack, onCookingMode = onCookingMode) }
}

/**
 * The constraint travels as URL-safe Base64 of its JSON: a raw JSON string in a navigation argument
 * is a reliable source of escaping bugs.
 */
@Serializable
data class CookingModeRouteKey(val recipeId: String, val constraint: String? = null)

fun NavController.navigateToCookingMode(recipeId: String, constraint: ScaleConstraint?) =
    navigate(CookingModeRouteKey(recipeId, constraint?.encodeForRoute()))

fun NavGraphBuilder.cookingModeScreen(onBack: () -> Unit) {
    composable<CookingModeRouteKey> { CookingModeRoute(onBack = onBack) }
}

internal fun ScaleConstraint.encodeForRoute(): String =
    Base64.getUrlEncoder().withoutPadding()
        .encodeToString(Json.encodeToString(this).toByteArray())

internal fun String.decodeConstraint(): ScaleConstraint? = runCatching {
    Json.decodeFromString<ScaleConstraint>(String(Base64.getUrlDecoder().decode(this)))
}.getOrNull()
