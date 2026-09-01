package com.ilsecondodasinistra.proportion.feature.recipes.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilsecondodasinistra.proportion.core.domain.repository.IngredientRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeFilter
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeSort
import com.ilsecondodasinistra.proportion.core.domain.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Holds the three filters, which combine with AND, and turns them into one repository query.
 *
 * Only the text query is debounced: chips and sort changes are deliberate taps and should feel
 * instant, while typing should not fire a query per keystroke.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class RecipeListViewModel @Inject constructor(
    recipeRepository: RecipeRepository,
    ingredientRepository: IngredientRepository,
    tagRepository: TagRepository,
) : ViewModel() {

    private data class FilterState(
        val query: String = "",
        val tagIds: Set<String> = emptySet(),
        val ingredientIds: Set<String> = emptySet(),
        val sort: RecipeSort = RecipeSort.RECENT,
    )

    private val filterState = MutableStateFlow(FilterState())

    private val debouncedFilter = filterState
        .debounce { if (it.query.isBlank()) 0L else QUERY_DEBOUNCE_MILLIS }
        .distinctUntilChanged()
        .map { state ->
            RecipeFilter(
                query = state.query,
                tagIds = state.tagIds.toList(),
                ingredientIds = state.ingredientIds.toList(),
                sort = state.sort,
            )
        }

    val uiState: StateFlow<RecipeListUiState> = combine(
        filterState,
        debouncedFilter.flatMapLatest { recipeRepository.observeRecipes(it) },
        tagRepository.observeAll(),
        ingredientRepository.observeInUse(),
        recipeRepository.observeRecipeCount(),
    ) { filters, recipes, tags, ingredients, totalCount ->
        RecipeListUiState(
            query = filters.query,
            selectedTagIds = filters.tagIds,
            selectedIngredientIds = filters.ingredientIds,
            sort = filters.sort,
            recipes = recipes,
            availableTags = tags,
            availableIngredients = ingredients,
            isLoading = false,
            libraryIsEmpty = totalCount == 0,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = RecipeListUiState(),
    )

    fun onQueryChange(query: String) = filterState.update { it.copy(query = query) }

    fun onTagToggle(tagId: String) = filterState.update { state ->
        state.copy(tagIds = state.tagIds.toggle(tagId))
    }

    fun onIngredientToggle(ingredientId: String) = filterState.update { state ->
        state.copy(ingredientIds = state.ingredientIds.toggle(ingredientId))
    }

    fun onSortChange(sort: RecipeSort) = filterState.update { it.copy(sort = sort) }

    fun onClearFilters() = filterState.update { FilterState(sort = it.sort) }

    private fun Set<String>.toggle(value: String): Set<String> =
        if (value in this) this - value else this + value

    companion object {
        const val QUERY_DEBOUNCE_MILLIS = 200L
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
