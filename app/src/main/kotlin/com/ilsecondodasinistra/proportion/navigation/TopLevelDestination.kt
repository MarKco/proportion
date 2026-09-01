package com.ilsecondodasinistra.proportion.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import com.ilsecondodasinistra.proportion.R
import com.ilsecondodasinistra.proportion.feature.home.HomeRoute
import com.ilsecondodasinistra.proportion.feature.recipes.navigation.RecipesRoute
import com.ilsecondodasinistra.proportion.feature.settings.SettingsRouteKey
import com.ilsecondodasinistra.proportion.feature.shopping.ShoppingRoute
import kotlin.reflect.KClass

enum class TopLevelDestination(
    val route: KClass<*>,
    val destination: Any,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(HomeRoute::class, HomeRoute, R.string.nav_home, Icons.Outlined.PieChart),
    RECIPES(RecipesRoute::class, RecipesRoute, R.string.nav_recipes, Icons.Outlined.MenuBook),
    SHOPPING(ShoppingRoute::class, ShoppingRoute, R.string.nav_shopping, Icons.Outlined.ShoppingCart),
    SETTINGS(SettingsRouteKey::class, SettingsRouteKey, R.string.nav_settings, Icons.Outlined.Settings),
}
