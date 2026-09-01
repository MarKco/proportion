package com.ilsecondodasinistra.proportion

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.ilsecondodasinistra.proportion.core.designsystem.theme.ProPortionTheme
import com.ilsecondodasinistra.proportion.navigation.ProPortionApp
import com.ilsecondodasinistra.proportion.core.domain.repository.IngredientRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeRepository
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.RecipeIngredient
import dagger.hilt.android.testing.HiltAndroidRule
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the real navigation graph with the real Hilt graph behind it, so a screen whose
 * ViewModel cannot be constructed fails here rather than on a device.
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(RobolectricTestRunner::class)
class NavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Inject
    lateinit var recipeRepository: RecipeRepository

    @Inject
    lateinit var ingredientRepository: IngredientRepository

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun label(resId: Int) = context.getString(resId)

    @Before
    fun setUp() {
        hiltRule.inject()
        seedOneRecipe()
        composeTestRule.setContent {
            ProPortionTheme(dynamicColour = false) { ProPortionApp() }
        }
    }

    /** One real recipe, written through the real repository, so the list has something to open. */
    private fun seedOneRecipe() = runBlocking {
        val flour = ingredientRepository.findOrCreate("Farina 00", MeasureUnit.GRAM)
        recipeRepository.upsert(
            Recipe(
                id = "nav-test-recipe",
                title = "Torta di prova",
                servings = 4,
                steps = listOf("Mescola."),
                ingredients = listOf(RecipeIngredient("nav-line-1", flour, 0, 300.0, MeasureUnit.GRAM)),
                tags = emptyList(),
            ),
        )
    }

    @Test
    fun `the app opens on the home tab`() {
        composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    @Test
    fun `tapping a tab shows that destination`() {
        composeTestRule.onNodeWithText(label(R.string.nav_recipes)).performClick()

        composeTestRule.onNodeWithTag("recipes_screen").assertIsDisplayed()
    }

    @Test
    fun `the add button opens the editor and back returns to the list`() {
        composeTestRule.onNodeWithText(label(R.string.nav_recipes)).performClick()
        composeTestRule.onNodeWithTag("add_recipe_fab").performClick()
        composeTestRule.onNodeWithTag("editor_screen").assertIsDisplayed()

        composeTestRule.onNodeWithTag("editor_back").performClick()
        composeTestRule.onNodeWithTag("recipes_screen").assertIsDisplayed()
    }

    @Test
    fun `a recipe opens its card and the cook screen`() {
        composeTestRule.onNodeWithText(label(R.string.nav_recipes)).performClick()

        // The list arrives from a database flow, so wait for it rather than assuming a frame.
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Torta di prova").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Torta di prova").performClick()
        composeTestRule.onNodeWithTag("recipe_detail_screen").assertIsDisplayed()

        composeTestRule.onNodeWithTag("cook_button").performClick()
        composeTestRule.onNodeWithTag("cook_screen").assertIsDisplayed()

        composeTestRule.onNodeWithTag("cook_back").performClick()
        composeTestRule.onNodeWithTag("recipe_detail_screen").assertIsDisplayed()
    }

    @Test
    fun `every top level destination is reachable`() {
        listOf(
            R.string.nav_recipes to "recipes_screen",
            R.string.nav_shopping to "shopping_screen",
            R.string.nav_settings to "settings_screen",
            R.string.nav_home to "home_screen",
        ).forEach { (labelRes, tag) ->
            composeTestRule.onNodeWithText(label(labelRes)).performClick()
            composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
        }
    }
}
