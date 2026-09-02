package com.ilsecondodasinistra.proportion.feature.shopping

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ShoppingViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeShoppingRepository(ShoppingTestData.items)
    private fun viewModel() = ShoppingViewModel(repository, testFormatter())

    @Test
    fun `an empty list says the list is empty instead of showing an empty column`() = runTest {
        ShoppingViewModel(FakeShoppingRepository(emptyList()), testFormatter()).uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertThat(state.isLoading).isFalse()
            assertThat(state.items).isEmpty()
            assertThat(state.isEmpty).isTrue()
        }
    }

    @Test
    fun `unchecked items are listed before checked ones`() = runTest {
        viewModel().uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()

            val firstChecked = state.items.indexOfFirst { it.isChecked }
            val lastUnchecked = state.items.indexOfLast { !it.isChecked }
            assertThat(firstChecked).isGreaterThan(lastUnchecked)
        }
    }

    @Test
    fun `checking an item writes through to the repository`() = runTest {
        val model = viewModel()
        model.uiState.test { advanceUntilIdle(); expectMostRecentItem() }

        model.onCheckedChange(ShoppingTestData.flourId, true)
        advanceUntilIdle()

        assertThat(repository.checked).containsEntry(ShoppingTestData.flourId, true)
    }

    @Test
    fun `clear checked removes only the checked ones`() = runTest {
        val model = viewModel()
        model.onClearChecked()
        advanceUntilIdle()

        assertThat(repository.clearCheckedCalls).isEqualTo(1)
        assertThat(repository.clearAllCalls).isEqualTo(0)
    }

    @Test
    fun `clear all asks for confirmation before emptying the list`() = runTest {
        val model = viewModel()

        model.onClearAllRequested()
        advanceUntilIdle()
        assertThat(model.uiState.value.confirmClearAll).isTrue()
        assertThat(repository.clearAllCalls).isEqualTo(0)

        model.onClearAllConfirmed()
        advanceUntilIdle()
        assertThat(repository.clearAllCalls).isEqualTo(1)
        assertThat(model.uiState.value.confirmClearAll).isFalse()
    }

    @Test
    fun `the share text lists every item`() = runTest {
        val model = viewModel()
        model.uiState.test { advanceUntilIdle(); expectMostRecentItem() }

        val text = model.shareText(ShoppingTestData.strings)

        assertThat(text).contains("Farina")
        assertThat(text).contains("Uova")
    }

    @Test
    fun `an item shows which recipes put it there`() = runTest {
        viewModel().uiState.test {
            advanceUntilIdle()
            val flour = expectMostRecentItem().items.first { it.id == ShoppingTestData.flourId }

            assertThat(flour.sourceCount).isEqualTo(2)
        }
    }
}
