package com.ilsecondodasinistra.proportion.feature.recipes.detail

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.ilsecondodasinistra.proportion.feature.recipes.navigation.RecipeDetailRouteKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.ScaleVariantRepository
import com.ilsecondodasinistra.proportion.core.domain.unit.QuantityFormatter
import com.ilsecondodasinistra.proportion.core.transfer.PlainTextFormatter
import com.ilsecondodasinistra.proportion.core.transfer.PlainTextStrings
import com.ilsecondodasinistra.proportion.core.transfer.TransferRepository
import com.ilsecondodasinistra.proportion.core.model.Recipe
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recipeRepository: RecipeRepository,
    variantRepository: ScaleVariantRepository,
    private val transferRepository: TransferRepository,
    private val formatter: QuantityFormatter,
) : ViewModel() {

    private val recipeId: String = savedStateHandle.toRoute<RecipeDetailRouteKey>().recipeId

    val uiState: StateFlow<RecipeDetailUiState> = combine(
        recipeRepository.observeRecipe(recipeId),
        variantRepository.observeForRecipe(recipeId),
    ) { recipe, variants ->
        when (recipe) {
            null -> RecipeDetailUiState.NotFound
            else -> RecipeDetailUiState.Content(
                recipe = recipe,
                lines = recipe.toDetailLines(),
                variants = variants,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = RecipeDetailUiState.Loading,
    )

    fun onFavouriteToggle() {
        val current = uiState.value as? RecipeDetailUiState.Content ?: return
        viewModelScope.launch {
            recipeRepository.setFavourite(recipeId, !current.recipe.isFavourite)
        }
    }

    /**
     * @param strings the few translated words the formatter needs; the domain never reads
     * resources.
     */
    fun onShareText(strings: PlainTextStrings, onReady: (String) -> Unit) {
        val recipe = (uiState.value as? RecipeDetailUiState.Content)?.recipe ?: return
        onReady(PlainTextFormatter.format(recipe, strings, formatter))
    }

    fun onShareFile(onReady: (String) -> Unit) {
        viewModelScope.launch {
            transferRepository.exportRecipe(recipeId)?.let(onReady)
        }
    }

    fun onDelete() {
        viewModelScope.launch { recipeRepository.delete(recipeId) }
    }

    private fun Recipe.toDetailLines(): List<DetailLine> =
        ingredients.sortedBy { it.position }.map { line ->
            val quantity = line.quantity
            DetailLine(
                id = line.id,
                name = line.ingredient.name,
                quantityText = line.displayText
                    ?: formatter.format(quantity ?: 0.0, line.unit).text,
                note = line.note,
            )
        }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
