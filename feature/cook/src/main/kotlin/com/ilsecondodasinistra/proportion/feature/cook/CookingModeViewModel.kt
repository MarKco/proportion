package com.ilsecondodasinistra.proportion.feature.cook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ilsecondodasinistra.proportion.core.domain.TimeProvider
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeRepository
import com.ilsecondodasinistra.proportion.core.domain.scale.RecipeScaler
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaleConstraint
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaleResult
import com.ilsecondodasinistra.proportion.core.domain.unit.QuantityFormatter
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.feature.cook.navigation.CookingModeRouteKey
import com.ilsecondodasinistra.proportion.feature.cook.navigation.decodeConstraint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A read-only run through the recipe at whatever scale it was entered with. The constraint is
 * decoded once from the route and never re-evaluated: this screen is for following the method at
 * the stove, not for adjusting it — going back to [CookViewModel] is how you change the scale.
 */
@HiltViewModel
class CookingModeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recipeRepository: RecipeRepository,
    private val scaler: RecipeScaler,
    private val formatter: QuantityFormatter,
    private val time: TimeProvider,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<CookingModeRouteKey>()
    private val recipeId: String = route.recipeId
    private val constraint: ScaleConstraint =
        route.constraint?.decodeConstraint() ?: ScaleConstraint.ByFactor(1.0)

    private val _uiState = MutableStateFlow(CookingModeUiState())
    val uiState: StateFlow<CookingModeUiState> = _uiState.asStateFlow()

    private var counted = false

    init {
        viewModelScope.launch {
            val recipe = recipeRepository.observeRecipe(recipeId).first() ?: return@launch

            _uiState.update { recipe.scaledOrAsWritten() }

            if (!counted) {
                counted = true
                recipeRepository.markCooked(recipeId, time.now())
            }
        }
    }

    fun onStepChecked(index: Int, done: Boolean) {
        _uiState.update { state ->
            state.copy(
                steps = state.steps.map { step ->
                    if (step.index == index) step.copy(isDone = done) else step
                },
            )
        }
    }

    fun onToggleIngredients(show: Boolean) {
        _uiState.update { it.copy(showIngredients = show) }
    }

    /** Runs [scaler] once with the route's constraint, falling back to the recipe as written. */
    private fun Recipe.scaledOrAsWritten(): CookingModeUiState = when (val result = scaler.scale(this, constraint)) {
        is ScaleResult.Success -> {
            val scaled = result.scaled
            CookingModeUiState(
                isLoading = false,
                title = title,
                factor = scaled.factor,
                servingsText = scaled.servings?.format(),
                steps = steps.toCookingSteps(),
                ingredients = scaled.lines.map { line ->
                    CookingIngredient(name = line.ingredientName, amountText = line.displayText)
                },
            )
        }

        is ScaleResult.Failure -> asWritten()
    }

    /** The recipe untouched: used when scaling is not possible, so the screen never goes blank. */
    private fun Recipe.asWritten(): CookingModeUiState = CookingModeUiState(
        isLoading = false,
        title = title,
        factor = 1.0,
        servingsText = servings?.toDouble()?.format(),
        steps = steps.toCookingSteps(),
        ingredients = ingredients.sortedBy { it.position }.map { line ->
            CookingIngredient(
                name = line.ingredient.name,
                amountText = line.quantity
                    ?.let { qty -> formatter.format(qty, line.unit).text }
                    ?: formatter.format(0.0, line.unit).text,
            )
        },
    )

    private fun List<String>.toCookingSteps(): List<CookingStep> =
        mapIndexed { index, text -> CookingStep(index, text, isDone = false) }
}
