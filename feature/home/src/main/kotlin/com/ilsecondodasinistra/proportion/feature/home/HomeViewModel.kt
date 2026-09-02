package com.ilsecondodasinistra.proportion.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilsecondodasinistra.proportion.core.domain.dashboard.CourseSlice
import com.ilsecondodasinistra.proportion.core.domain.dashboard.DashboardSummariser
import com.ilsecondodasinistra.proportion.core.domain.dashboard.RecipePicker
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.ScaleVariantRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.TagRepository
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.Tag
import com.ilsecondodasinistra.proportion.feature.home.di.SuggestionRandom
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Feeds every dashboard card from one `combine` of the recipe and tag libraries, run through
 * [DashboardSummariser]. The Continue-cooking variant label is the one field that cannot come from
 * that combine — it lives on a different recipe's variants — so it is layered on separately with
 * `flatMapLatest`, keyed on whichever recipe [DashboardSummariser] currently names.
 *
 * The suggestion (`onReshuffle`, `onSuggestionTagChange`) is deliberately imperative rather than
 * part of the same reactive pipeline: a re-pick should happen when the user asks for one or when the
 * library changes under it, never as a side effect of an unrelated recomposition.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val tagRepository: TagRepository,
    private val variantRepository: ScaleVariantRepository,
    private val summariser: DashboardSummariser,
    private val picker: RecipePicker,
    @param:SuggestionRandom private val random: Random = Random.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** The library as of the last combine emission, so [onReshuffle] and [onSuggestionTagChange]
     * can re-pick without waiting for another flow tick. */
    private var library: List<Recipe> = emptyList()

    init {
        viewModelScope.launch {
            combine(recipeRepository.observeRecipes(), tagRepository.observeAll()) { recipes, tags ->
                recipes to tags
            }.collect { (recipes, tags) -> onLibraryChanged(recipes, tags) }
        }

        viewModelScope.launch {
            _uiState
                .map { it.continueCooking?.recipeId }
                .distinctUntilChanged()
                .flatMapLatest { recipeId ->
                    if (recipeId == null) {
                        flowOf(null)
                    } else {
                        variantRepository.observeForRecipe(recipeId)
                            .map { variants -> variants.firstOrNull { it.isDefault }?.label }
                    }
                }
                .collect { label ->
                    _uiState.update { state ->
                        val current = state.continueCooking ?: return@update state
                        state.copy(continueCooking = current.copy(variantLabel = label))
                    }
                }
        }
    }

    private fun onLibraryChanged(recipes: List<Recipe>, tags: List<Tag>) {
        library = recipes
        val summary = summariser.summarise(recipes)

        val tagId = _uiState.value.suggestionTagId
        val excluding = _uiState.value.suggestion?.recipeId
        val suggestion = picker.pick(recipes, tagId, excluding, random)

        _uiState.update { state ->
            state.copy(
                isLoading = false,
                isEmpty = recipes.isEmpty(),
                recipeCount = summary.recipeCount,
                totalCooks = summary.totalCooks,
                favouriteCount = summary.favouriteCount,
                donutSlices = summary.courseSlices.toDonutSlices(tags),
                uncategorisedCount = summary.uncategorisedCount,
                continueCooking = summary.continueCooking?.toContinueCooking(state.continueCooking),
                mostCooked = summary.mostCooked.map { it.toCardItem() },
                favourites = summary.favourites.map { it.toCardItem() },
                suggestion = suggestion?.toCardItem(),
                suggestionUnavailable = suggestion == null && recipes.isNotEmpty(),
                suggestionTags = tags.map { TagChipItem(it.id, it.key, it.name) },
            )
        }
    }

    /** Re-picks inside the current tag filter, excluding whatever is on screen. */
    fun onReshuffle() {
        val excluding = _uiState.value.suggestion?.recipeId
        repickSuggestion(tagId = _uiState.value.suggestionTagId, excluding = excluding)
    }

    /** [tagId] null means "any course". */
    fun onSuggestionTagChange(tagId: String?) {
        _uiState.update { it.copy(suggestionTagId = tagId) }
        repickSuggestion(tagId = tagId, excluding = null)
    }

    private fun repickSuggestion(tagId: String?, excluding: String?) {
        val suggestion = picker.pick(library, tagId, excluding, random)
        _uiState.update {
            it.copy(
                suggestion = suggestion?.toCardItem(),
                suggestionUnavailable = suggestion == null && library.isNotEmpty(),
            )
        }
    }

    private fun List<CourseSlice>.toDonutSlices(tags: List<Tag>): List<DonutSliceUi> = map { slice ->
        val colorIndex = tags.firstOrNull { it.id == slice.tagId }?.colorIndex ?: 0
        DonutSliceUi(tagKey = slice.tagKey, count = slice.count, colorIndex = colorIndex)
    }

    /** Keeps the previously fetched label when it is still the same recipe, so the field does not
     * flicker back to null while the variant flow above catches up. */
    private fun Recipe.toContinueCooking(previous: ContinueCooking?): ContinueCooking = ContinueCooking(
        recipeId = id,
        title = title,
        variantLabel = previous?.takeIf { it.recipeId == id }?.variantLabel,
    )

    private fun Recipe.toCardItem() = RecipeCardItem(
        recipeId = id,
        title = title,
        cookCount = cookCount,
        isFavourite = isFavourite,
    )
}
