package com.ilsecondodasinistra.proportion.feature.shopping

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object ShoppingRoute

fun NavGraphBuilder.shoppingScreen() {
    composable<ShoppingRoute> { ShoppingScreen() }
}
