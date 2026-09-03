package com.ilsecondodasinistra.proportion.feature.cook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ilsecondodasinistra.proportion.core.domain.repository.IngredientRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.ScaleVariantRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.ShoppingRepository
import com.ilsecondodasinistra.proportion.core.domain.scale.AvailableAmount
import com.ilsecondodasinistra.proportion.core.domain.scale.RecipeScaler
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaleConstraint
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaleError
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaleResult
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaleWarning
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaledLine
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaledRecipe
import com.ilsecondodasinistra.proportion.core.domain.scale.SnapOption
import com.ilsecondodasinistra.proportion.core.domain.unit.DensityRequirement
import com.ilsecondodasinistra.proportion.core.domain.unit.QuantityFormatter
import com.ilsecondodasinistra.proportion.core.domain.unit.requirementFor
import com.ilsecondodasinistra.proportion.core.domain.unit.toRef
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.feature.cook.navigation.CookRouteKey
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the constraint and nothing else.
 *
 * Every edit re-runs [RecipeScaler] over the whole recipe rather than adjusting one line, which is
 * what keeps the result in proportion — including when the user accepts a snap.
 */
@HiltViewModel
class CookViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recipeRepository: RecipeRepository,
    private val variantRepository: ScaleVariantRepository,
    private val shoppingRepository: ShoppingRepository,
    private val ingredientRepository: IngredientRepository,
    private val scaler: RecipeScaler,
    private val formatter: QuantityFormatter,
) : ViewModel() {

    private val recipeId: String = savedStateHandle.toRoute<CookRouteKey>().recipeId

    private val _uiState = MutableStateFlow(CookUiState())
    val uiState: StateFlow<CookUiState> = _uiState.asStateFlow()

    private var recipe: Recipe? = null

    /**
     * The domain-level lines behind the current [CookUiState.lines], kept so "add to shopping"
     * sends what was actually scaled.
     */
    private var lastScaledLines: List<ScaledLine> = emptyList()

    init {
        viewModelScope.launch {
            val loaded = recipeRepository.observeRecipe(recipeId).first() ?: return@launch
            recipe = loaded

            val servings = loaded.servings
            val hasServings = servings != null && servings > 0
            _uiState.update {
                it.copy(
                    isLoading = false,
                    recipe = loaded,
                    mode = if (hasServings) CookMode.SERVINGS else CookMode.FACTOR,
                    servingsModeAvailable = hasServings,
                    servingsInput = loaded.servings ?: 0,
                )
            }
            recompute()
        }
    }

    fun onModeChange(mode: CookMode) {
        val current = _uiState.value
        if (mode == CookMode.SERVINGS && !current.servingsModeAvailable) return

        // Carry the current factor across, so switching mode never silently resets the recipe.
        _uiState.update { state ->
            state.copy(
                mode = mode,
                factorInput = state.factor.toInputText(),
                factorValue = state.factor,
                servingsInput = state.servings?.roundedServings() ?: state.servingsInput,
                ingredientQuantityInput = "",
                ingredientLineId = if (mode == CookMode.INGREDIENT) state.ingredientLineId else null,
                ingredientUnitInput = null,
                pendingDensityPrompt = null,
                pantryInputs = if (mode == CookMode.PANTRY) state.pantryInputs else emptyMap(),
                error = null,
            )
        }
        recompute()
    }

    fun onServingsChange(servings: Int) {
        _uiState.update { it.copy(servingsInput = servings.coerceAtLeast(1), error = null) }
        recompute()
    }

    fun onFactorChange(text: String) {
        _uiState.update { it.copy(factorInput = text, factorValue = text.parseAmount(), error = null) }
        recompute()
    }

    fun onIngredientSelected(lineId: String) {
        val line = recipe?.ingredients?.firstOrNull { it.id == lineId }
        _uiState.update {
            it.copy(
                ingredientLineId = lineId,
                ingredientQuantityInput = line?.quantity?.toInputText().orEmpty(),
                ingredientUnitInput = null,
                pendingDensityPrompt = null,
                error = null,
            )
        }
        recompute()
    }

    fun onIngredientQuantityChange(text: String) {
        _uiState.update { it.copy(ingredientQuantityInput = text, error = null) }
        recompute()
    }

    /**
     * Picking a unit outside the recipe line's own category may need density/item-weight data the
     * ingredient doesn't have yet — [requirementFor] says so, and [pendingDensityPrompt] asks for
     * it. The unit choice itself always applies immediately; [recompute] surfaces the ordinary
     * "incompatible unit" error until the prompt (if any) is answered.
     */
    fun onIngredientUnitChange(unit: MeasureUnit) {
        val line = recipe?.ingredients?.firstOrNull { it.id == _uiState.value.ingredientLineId }

        val prompt = if (line != null && line.unit.category != unit.category) {
            val requirement = requirementFor(line.unit, unit, line.ingredient.toRef())
            if (requirement != DensityRequirement.NONE && requirement != DensityRequirement.UNSUPPORTED) {
                DensityPromptRequest(
                    ingredientId = line.ingredient.id,
                    ingredientName = line.ingredient.name,
                    requirement = requirement,
                    fromUnit = line.unit,
                    toUnit = unit,
                )
            } else {
                null
            }
        } else {
            null
        }

        _uiState.update {
            it.copy(ingredientUnitInput = unit, pendingDensityPrompt = prompt, error = null)
        }
        recompute()
    }

    /** The user just answered the "density unknown" prompt: persist it, then recompute. */
    fun onDensityPromptConfirm(densityGramsPerMl: Double?, itemWeightGrams: Double?) {
        val request = _uiState.value.pendingDensityPrompt ?: return
        viewModelScope.launch {
            ingredientRepository.setDensityData(request.ingredientId, densityGramsPerMl, itemWeightGrams)
            recipe = recipe?.let { current ->
                current.copy(
                    ingredients = current.ingredients.map { line ->
                        if (line.ingredient.id != request.ingredientId) {
                            line
                        } else {
                            line.copy(
                                ingredient = line.ingredient.copy(
                                    densityGramsPerMl = densityGramsPerMl ?: line.ingredient.densityGramsPerMl,
                                    itemWeightGrams = itemWeightGrams ?: line.ingredient.itemWeightGrams,
                                ),
                            )
                        }
                    },
                )
            }
            _uiState.update { it.copy(pendingDensityPrompt = null) }
            recompute()
        }
    }

    fun onDensityPromptDismiss() {
        _uiState.update { it.copy(pendingDensityPrompt = null) }
    }

    fun onPantryAmountChange(lineId: String, text: String) {
        _uiState.update { state ->
            state.copy(
                pantryInputs = state.pantryInputs.toMutableMap().apply {
                    if (text.isBlank()) remove(lineId) else put(lineId, text)
                },
                error = null,
            )
        }
        recompute()
    }

    /** Accepting a snap does not edit one line: it adopts the factor that amount implies. */
    fun onSnapAccept(option: SnapOption) {
        _uiState.update {
            it.copy(
                mode = CookMode.FACTOR,
                factorInput = option.resultingFactor.toInputText(),
                factorValue = option.resultingFactor,
                error = null,
            )
        }
        recompute()
    }

    /**
     * Showing the scaled card does not count as cooking any more — cooking mode is what marks the
     * recipe cooked (§7.5), and counting both meant one session showed as two cooks.
     */
    fun onShowCard(show: Boolean) {
        _uiState.update { it.copy(showCard = show) }
    }

    fun onSaveVariantRequested(visible: Boolean) {
        _uiState.update { it.copy(saveDialogVisible = visible) }
    }

    /**
     * Sends the currently scaled lines, not the recipe's originals. [ShoppingRepository.addScaled]
     * already drops lines with no amount to buy and merges compatible units, so none of that is
     * repeated here — only counted, via [isAddableToShoppingList], to phrase the confirmation.
     */
    fun onAddToShoppingList() {
        val lines = lastScaledLines
        val addableCount = lines.count { it.isAddableToShoppingList() }

        if (addableCount == 0) {
            _uiState.update { it.copy(shoppingMessage = ShoppingMessage.NothingToAdd) }
            return
        }

        viewModelScope.launch {
            shoppingRepository.addScaled(lines, recipeId)
            _uiState.update { it.copy(shoppingMessage = ShoppingMessage.Added(addableCount)) }
        }
    }

    /** Clears the confirmation once the Snackbar has shown it, so rotation cannot replay it. */
    fun onShoppingMessageShown() {
        _uiState.update { it.copy(shoppingMessage = null) }
    }

    fun onSaveVariant(label: String, asDefault: Boolean) {
        val constraint = currentConstraint() ?: return
        viewModelScope.launch {
            variantRepository.save(recipeId, label.trim(), constraint, asDefault)
            _uiState.update { it.copy(saveDialogVisible = false) }
        }
    }

    private fun currentConstraint(): ScaleConstraint? {
        val state = _uiState.value
        return when (state.mode) {
            CookMode.SERVINGS -> ScaleConstraint.ByServings(state.servingsInput.toDouble())

            CookMode.FACTOR -> state.factorValue?.let { ScaleConstraint.ByFactor(it) }

            CookMode.INGREDIENT -> {
                val lineId = state.ingredientLineId ?: return null
                val qty = state.ingredientQuantityInput.parseAmount() ?: return null
                val lineUnit = recipe?.ingredients?.firstOrNull { it.id == lineId }?.unit ?: return null
                ScaleConstraint.ByIngredient(lineId, qty, state.ingredientUnitInput ?: lineUnit)
            }

            CookMode.PANTRY -> {
                val amounts = state.pantryInputs.mapNotNull { (lineId, text) ->
                    val qty = text.parseAmount() ?: return@mapNotNull null
                    val unit = recipe?.ingredients?.firstOrNull { it.id == lineId }?.unit
                        ?: return@mapNotNull null
                    AvailableAmount(lineId, qty, unit)
                }
                if (amounts.isEmpty()) null else ScaleConstraint.ByAvailability(amounts)
            }
        }
    }

    private fun recompute() {
        val current = recipe ?: return
        val constraint = currentConstraint()

        if (constraint == null) {
            // An empty or half-typed input is not an error: keep showing the recipe as it stands.
            lastScaledLines = emptyList()
            _uiState.update {
                it.copy(lines = current.unscaledLines(), suggestedLabel = it.suggestedLabel, currentConstraint = null)
            }
            return
        }

        when (val result = scaler.scale(current, constraint)) {
            is ScaleResult.Failure -> {
                lastScaledLines = emptyList()
                _uiState.update {
                    it.copy(
                        error = result.reason.toCookError(),
                        lines = current.unscaledLines(),
                        currentConstraint = constraint,
                    )
                }
            }

            is ScaleResult.Success -> {
                lastScaledLines = result.scaled.lines
                _uiState.update { state ->
                    state.copy(
                        error = null,
                        factor = result.scaled.factor,
                        servings = result.scaled.servings,
                        lines = result.scaled.toLines(current),
                        ovenAdvisory = result.scaled.ovenAdvisory(),
                        bottleneckLineId = result.scaled.bottleneckLineId,
                        leftovers = result.scaled.leftoverUi(current),
                        servingsInput = if (state.mode == CookMode.SERVINGS) {
                            state.servingsInput
                        } else {
                            result.scaled.servings?.roundedServings() ?: state.servingsInput
                        },
                        suggestedLabel = state.suggestedLabelFor(result.scaled),
                        currentConstraint = constraint,
                    )
                }
            }
        }
    }

    private fun CookUiState.suggestedLabelFor(scaled: ScaledRecipe): SuggestedLabel = when (mode) {
        CookMode.SERVINGS -> SuggestedLabel.Servings(scaled.servings ?: servingsInput.toDouble())
        CookMode.PANTRY -> SuggestedLabel.Pantry
        else -> SuggestedLabel.Factor(scaled.factor)
    }

    private fun ScaledRecipe.toLines(recipe: Recipe): List<CookLine> {
        val warningsByLine = warnings.groupBy { it.lineId() }
        val snapsByLine = snapSuggestions.groupBy { it.lineId }

        return lines.map { line ->
            val original = recipe.ingredients.first { it.id == line.lineId }
            val lineWarnings = warningsByLine[line.lineId].orEmpty()

            CookLine(
                lineId = line.lineId,
                name = line.ingredientName,
                originalText = original.quantity
                    ?.let { formatter.format(it, original.unit).text }
                    ?: formatter.format(0.0, original.unit).text,
                scaledText = line.displayText,
                unit = line.displayUnit,
                isScaled = line.isScaled,
                hasWarning = lineWarnings.isNotEmpty(),
                warningText = lineWarnings.firstOrNull()?.let { warning ->
                    when (warning) {
                        is ScaleWarning.NonIntegerDiscrete ->
                            formatter.format(warning.exact, line.displayUnit).text
                        else -> null
                    }
                },
                snaps = snapsByLine[line.lineId].orEmpty().map { option ->
                    SnapChip(
                        label = formatter.format(option.targetQty, line.displayUnit).text,
                        option = option,
                    )
                },
            )
        }
    }

    private fun ScaleWarning.lineId(): String? = when (this) {
        is ScaleWarning.NonIntegerDiscrete -> lineId
        is ScaleWarning.TooSmallToMeasure -> lineId
        is ScaleWarning.NotScalable -> lineId
        is ScaleWarning.BakingTimeCaution -> null
    }

    private fun ScaledRecipe.ovenAdvisory(): OvenAdvisory? =
        warnings.filterIsInstance<ScaleWarning.BakingTimeCaution>().firstOrNull()?.let {
            OvenAdvisory(factor = it.factor, tinDiameterRatio = it.suggestedTinDiameterRatio)
        }

    private fun ScaledRecipe.leftoverUi(recipe: Recipe): List<LeftoverUi> = leftovers.map { left ->
        LeftoverUi(
            lineId = left.lineId,
            name = recipe.ingredients.first { it.id == left.lineId }.ingredient.name,
            text = formatter.format(left.qty, left.unit).text,
        )
    }

    private fun Recipe.unscaledLines(): List<CookLine> = ingredients.sortedBy { it.position }.map { line ->
        val text = line.quantity
            ?.let { formatter.format(it, line.unit).text }
            ?: formatter.format(0.0, line.unit).text
        CookLine(
            lineId = line.id,
            name = line.ingredient.name,
            originalText = text,
            scaledText = text,
            unit = line.unit,
            isScaled = line.unit.isScalable && line.quantity != null,
        )
    }

    private fun ScaleError.toCookError(): CookError = when (this) {
        ScaleError.NoServingsDefined -> CookError.NO_SERVINGS
        ScaleError.ConstraintOnApproximateUnit -> CookError.APPROXIMATE_INGREDIENT
        ScaleError.IncompatibleUnit, ScaleError.UnknownLine -> CookError.INCOMPATIBLE_UNIT
        ScaleError.NonPositiveFactor, ScaleError.EmptyRecipe -> CookError.NON_POSITIVE
    }

    /**
     * Accepts both decimal separators: an Italian keyboard offers the comma.
     *
     * A zero or a negative number parses successfully on purpose — the engine rejects it with a
     * reason, which is a better answer than silently doing nothing.
     */
    private fun String.parseAmount(): Double? = trim().replace(',', '.').toDoubleOrNull()

    private fun Double.toInputText(): String =
        if (this % 1.0 == 0.0) toInt().toString()
        else String.format(Locale.ROOT, "%.2f", this).trimEnd('0').trimEnd('.').replace('.', ',')

    private fun Double.roundedServings(): Int = kotlin.math.round(this).toInt().coerceAtLeast(1)
}

/**
 * Mirrors the filter `ShoppingRepositoryImpl.addScaled` applies in `core/data` — kept here only to
 * phrase the confirmation, not to decide what is sent. Must be kept in sync if that rule changes.
 */
internal fun ScaledLine.isAddableToShoppingList(): Boolean = isScaled && scaledQty != null
