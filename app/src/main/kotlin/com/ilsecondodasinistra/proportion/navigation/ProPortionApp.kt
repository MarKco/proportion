package com.ilsecondodasinistra.proportion.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ilsecondodasinistra.proportion.feature.cook.navigation.cookScreen
import com.ilsecondodasinistra.proportion.feature.cook.navigation.navigateToCook
import com.ilsecondodasinistra.proportion.feature.editor.navigation.editorScreen
import com.ilsecondodasinistra.proportion.feature.editor.navigation.navigateToEditRecipe
import com.ilsecondodasinistra.proportion.feature.editor.navigation.navigateToNewRecipe
import com.ilsecondodasinistra.proportion.feature.home.HomeRoute
import com.ilsecondodasinistra.proportion.feature.home.homeScreen
import com.ilsecondodasinistra.proportion.feature.recipes.navigation.navigateToRecipeDetail
import com.ilsecondodasinistra.proportion.feature.recipes.navigation.recipeDetailScreen
import com.ilsecondodasinistra.proportion.feature.recipes.navigation.recipesScreen
import com.ilsecondodasinistra.proportion.feature.settings.settingsScreen
import com.ilsecondodasinistra.proportion.feature.shopping.shoppingScreen

@Composable
fun ProPortionApp(
    navController: NavHostController = rememberNavController(),
    startDestination: Any = HomeRoute,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Detail and editor cover the whole screen: the tab bar would be a false affordance there.
    val showBottomBar = TopLevelDestination.entries.any { destination ->
        currentDestination?.hierarchy?.any { it.hasRoute(destination.route) } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected =
                            currentDestination?.hierarchy?.any { it.hasRoute(destination.route) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(destination) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
        ) {
            homeScreen()

            recipesScreen(
                onRecipeClick = navController::navigateToRecipeDetail,
                onAddRecipe = navController::navigateToNewRecipe,
            )

            recipeDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = navController::navigateToEditRecipe,
                onCook = navController::navigateToCook,
            )

            editorScreen(onDone = { navController.popBackStack() })

            cookScreen(onBack = { navController.popBackStack() })

            shoppingScreen()
            settingsScreen()
        }
    }
}

/** Each tab keeps its own back stack, and tapping the current tab does not stack a copy. */
private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.destination) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
