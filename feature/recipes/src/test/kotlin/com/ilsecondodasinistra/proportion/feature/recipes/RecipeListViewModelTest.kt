package com.ilsecondodasinistra.proportion.feature.recipes

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeSort
import com.ilsecondodasinistra.proportion.feature.recipes.list.RecipeListViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val recipes = FakeRecipeRepository(listOf(TestData.cake, TestData.risotto))
    private val ingredients = FakeIngredientRepository(
        listOf(TestData.flour, TestData.eggs, TestData.rice),
    )
    private val tags = FakeTagRepository(listOf(TestData.dessertTag, TestData.firstCourseTag))

    private fun viewModel() = RecipeListViewModel(recipes, ingredients, tags)

    @Test
    fun `the library is listed with the tags and ingredients available for filtering`() = runTest {
        viewModel().uiState.test {
            awaitItem() // initial, before the repositories emit
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertThat(state.recipes.map { it.id }).containsExactly("r-cake", "r-risotto").inOrder()
            assertThat(state.resultCount).isEqualTo(2)
            assertThat(state.availableTags).hasSize(2)
            assertThat(state.availableIngredients).hasSize(3)
            assertThat(state.libraryIsEmpty).isFalse()
        }
    }

    @Test
    fun `typing narrows the list once the debounce has elapsed`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()

            vm.onQueryChange("risotto")
            advanceTimeBy(RecipeListViewModel.QUERY_DEBOUNCE_MILLIS + 50)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.query).isEqualTo("risotto")
            assertThat(state.recipes.map { it.id }).containsExactly("r-risotto")
        }
    }

    @Test
    fun `rapid typing does not query the repository once per keystroke`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            recipes.requestedFilters.clear()

            listOf("t", "to", "tor", "tort", "torta").forEach {
                vm.onQueryChange(it)
                advanceTimeBy(20)
            }
            advanceTimeBy(RecipeListViewModel.QUERY_DEBOUNCE_MILLIS + 50)
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(recipes.requestedFilters.map { it.query }).containsExactly("torta")
    }

    @Test
    fun `search matches an ingredient name, not only the title`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()

            vm.onQueryChange("farina")
            advanceTimeBy(RecipeListViewModel.QUERY_DEBOUNCE_MILLIS + 50)
            advanceUntilIdle()

            assertThat(expectMostRecentItem().recipes.map { it.id }).containsExactly("r-cake")
        }
    }

    @Test
    fun `a tag can be selected and deselected`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()

            vm.onTagToggle("tag-dessert")
            advanceUntilIdle()
            assertThat(expectMostRecentItem().recipes.map { it.id }).containsExactly("r-cake")

            vm.onTagToggle("tag-dessert")
            advanceUntilIdle()
            val cleared = expectMostRecentItem()
            assertThat(cleared.selectedTagIds).isEmpty()
            assertThat(cleared.recipes).hasSize(2)
        }
    }

    @Test
    fun `an ingredient filter requires every selected ingredient`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()

            vm.onIngredientToggle("ing-farina")
            vm.onIngredientToggle("ing-uova")
            advanceUntilIdle()
            assertThat(expectMostRecentItem().recipes.map { it.id }).containsExactly("r-cake")

            vm.onIngredientToggle("ing-riso")
            advanceUntilIdle()
            assertThat(expectMostRecentItem().recipes).isEmpty()
        }
    }

    @Test
    fun `filters combine with AND`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()

            vm.onTagToggle("tag-dessert")
            vm.onQueryChange("risotto")
            advanceTimeBy(RecipeListViewModel.QUERY_DEBOUNCE_MILLIS + 50)
            advanceUntilIdle()

            assertThat(expectMostRecentItem().recipes).isEmpty()
        }
    }

    @Test
    fun `clearing resets every filter at once`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()

            vm.onQueryChange("torta")
            vm.onTagToggle("tag-dessert")
            vm.onIngredientToggle("ing-farina")
            advanceTimeBy(RecipeListViewModel.QUERY_DEBOUNCE_MILLIS + 50)
            advanceUntilIdle()
            assertThat(expectMostRecentItem().hasActiveFilters).isTrue()

            vm.onClearFilters()
            advanceTimeBy(RecipeListViewModel.QUERY_DEBOUNCE_MILLIS + 50)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.query).isEmpty()
            assertThat(state.selectedTagIds).isEmpty()
            assertThat(state.selectedIngredientIds).isEmpty()
            assertThat(state.hasActiveFilters).isFalse()
            assertThat(state.recipes).hasSize(2)
        }
    }

    @Test
    fun `sorting alphabetically reorders the list`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()

            vm.onSortChange(RecipeSort.ALPHABETICAL)
            advanceUntilIdle()

            assertThat(expectMostRecentItem().recipes.map { it.id })
                .containsExactly("r-risotto", "r-cake").inOrder()
        }
    }

    @Test
    fun `an empty library is not the same as no results`() = runTest {
        val emptyVm = RecipeListViewModel(FakeRecipeRepository(), ingredients, tags)
        emptyVm.uiState.test {
            advanceUntilIdle()
            assertThat(expectMostRecentItem().libraryIsEmpty).isTrue()
        }

        val vm = viewModel()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onQueryChange("zafferano e cioccolato")
            advanceTimeBy(RecipeListViewModel.QUERY_DEBOUNCE_MILLIS + 50)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.recipes).isEmpty()
            assertThat(state.libraryIsEmpty).isFalse()
        }
    }
}
