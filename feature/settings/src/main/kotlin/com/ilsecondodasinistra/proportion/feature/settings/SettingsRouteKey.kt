package com.ilsecondodasinistra.proportion.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Named RouteKey so it does not collide with the [SettingsRoute] composable. */
@Serializable
data object SettingsRouteKey

fun NavGraphBuilder.settingsScreen() {
    composable<SettingsRouteKey> { SettingsRoute() }
}
