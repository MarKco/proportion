package com.ilsecondodasinistra.proportion.feature.home

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.domain.dashboard.DashboardSummariser
import com.ilsecondodasinistra.proportion.core.domain.dashboard.RecipePicker
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.Tag
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        recipes: List<Recipe> = HomeTestData.library,
        tags: List<Tag> = HomeTestData.tags,
    ) = HomeViewModel(
        recipeRepository = FakeRecipeRepository(recipes),
        tagRepository = FakeTagRepository(tags),
        variantRepository = FakeScaleVariantRepository(),
        summariser = DashboardSummariser(),
        picker = RecipePicker(),
        random = Random(1),
    )

    @Test
    fun `an empty library asks for the first recipe instead of drawing empty cards`() = runTest {
        viewModel(recipes = emptyList()).uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertThat(state.isLoading).isFalse()
            assertThat(state.isEmpty).isTrue()
        }
    }

    @Test
    fun `the numbers card reports the library`() = runTest {
        viewModel().uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertThat(state.recipeCount).isEqualTo(HomeTestData.library.size)
            assertThat(state.totalCooks).isEqualTo(HomeTestData.library.sumOf { it.cookCount })
            assertThat(state.donutSlices).isNotEmpty()
        }
    }

    @Test
    fun `continue cooking names the last cooked recipe and its default variant`() = runTest {
        viewModel().uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertThat(state.continueCooking?.recipeId).isEqualTo(HomeTestData.lastCookedId)
            assertThat(state.continueCooking?.variantLabel).isEqualTo("Per 6")
        }
    }

    @Test
    fun `the suggestion changes when the user reshuffles`() = runTest {
        val model = viewModel()
        model.uiState.test {
            advanceUntilIdle()
            val first = expectMostRecentItem().suggestion?.recipeId

            model.onReshuffle()
            advanceUntilIdle()

            assertThat(expectMostRecentItem().suggestion?.recipeId).isNotEqualTo(first)
        }
    }

    @Test
    fun `filtering the suggestion by tag only suggests recipes carrying it`() = runTest {
        val model = viewModel()
        model.onSuggestionTagChange(HomeTestData.dessertTagId)
        model.uiState.test {
            advanceUntilIdle()
            val suggested = expectMostRecentItem().suggestion?.recipeId

            assertThat(HomeTestData.dessertIds).contains(suggested)
        }
    }

    @Test
    fun `a tag with no recipes says so rather than suggesting something else`() = runTest {
        val model = viewModel()
        model.onSuggestionTagChange(HomeTestData.emptyTagId)
        model.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertThat(state.suggestion).isNull()
            assertThat(state.suggestionUnavailable).isTrue()
        }
    }
}
