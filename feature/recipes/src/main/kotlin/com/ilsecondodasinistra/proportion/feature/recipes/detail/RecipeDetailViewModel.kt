package com.ilsecondodasinistra.proportion.feature.recipes.detail

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.ilsecondodasinistra.proportion.feature.recipes.navigation.RecipeDetailRouteKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilsecondodasinistra.proportion.core.domain.repository.IngredientRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.ScaleVariantRepository
import com.ilsecondodasinistra.proportion.core.domain.scale.RecipeScaler
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaleResult
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaledRecipe
import com.ilsecondodasinistra.proportion.core.domain.unit.DensityRequirement
import com.ilsecondodasinistra.proportion.core.domain.unit.IngredientRef
import com.ilsecondodasinistra.proportion.core.domain.unit.QuantityFormatter
import com.ilsecondodasinistra.proportion.core.domain.unit.UnitConverter
import com.ilsecondodasinistra.proportion.core.domain.unit.requirementFor
import com.ilsecondodasinistra.proportion.core.domain.unit.toRef
import com.ilsecondodasinistra.proportion.core.transfer.PlainTextFormatter
import com.ilsecondodasinistra.proportion.core.transfer.PlainTextStrings
import com.ilsecondodasinistra.proportion.core.transfer.TransferRepository
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.ScaleVariant
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recipeRepository: RecipeRepository,
    private val variantRepository: ScaleVariantRepository,
    private val transferRepository: TransferRepository,
    private val ingredientRepository: IngredientRepository,
    private val formatter: QuantityFormatter,
    private val converter: UnitConverter,
    private val scaler: RecipeScaler,
) : ViewModel() {

    private val recipeId: String = savedStateHandle.toRoute<RecipeDetailRouteKey>().recipeId

    /**
     * Which scaling the user asked to see. [VariantSelection.Auto] means "let the default variant
     * decide, if there is one" — the state the screen opens in and returns to only implicitly; once
     * the user explicitly asks for the original or a specific variant, that choice sticks.
     */
    private val selection = MutableStateFlow<VariantSelection>(VariantSelection.Auto)

    val uiState: StateFlow<RecipeDetailUiState> = combine(
        recipeRepository.observeRecipe(recipeId),
        variantRepository.observeForRecipe(recipeId),
        selection,
    ) { recipe, variants, currentSelection ->
        when (recipe) {
            null -> RecipeDetailUiState.NotFound
            else -> buildContent(recipe, variants, currentSelection)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = RecipeDetailUiState.Loading,
    )

    /** Back to the recipe as entered, dropping whichever variant (default or chosen) was showing. */
    fun onShowOriginal() {
        selection.value = VariantSelection.Original
    }

    fun onShowVariant(variantId: String) {
        selection.value = VariantSelection.Specific(variantId)
    }

    /**
     * Resolves which variant (if any) should be on screen, scales the recipe against it, and falls
     * back to the recipe as written whenever there is no target variant or its constraint no longer
     * resolves — a recipe edited after a variant was saved must still open cleanly.
     */
    private fun buildContent(
        recipe: Recipe,
        variants: List<ScaleVariant>,
        currentSelection: VariantSelection,
    ): RecipeDetailUiState.Content {
        val target = when (currentSelection) {
            VariantSelection.Auto -> variants.firstOrNull { it.isDefault }
            VariantSelection.Original -> null
            is VariantSelection.Specific -> variants.firstOrNull { it.id == currentSelection.variantId }
        }

        val scaled = target?.let { variant ->
            val constraint = variantRepository.readConstraint(variant)
            when (val result = scaler.scale(recipe, constraint)) {
                is ScaleResult.Success -> result.scaled
                is ScaleResult.Failure -> null
            }
        }

        return RecipeDetailUiState.Content(
            recipe = recipe,
            lines = scaled?.let { recipe.toDetailLines(it) } ?: recipe.toDetailLines(),
            variants = variants,
            showingVariant = target?.takeIf { scaled != null }
                ?.let { ShowingVariant(variantId = it.id, label = it.label) },
            cookCount = recipe.cookCount,
        )
    }

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

    /**
     * Answers to the "density unknown" prompt, applied immediately rather than waited for on
     * [uiState] to reflect them — an ingredient can be shared by rows the current query doesn't
     * recompose promptly for, and this sheet is a poor place to make the user wait and wonder.
     */
    private val densityOverrides = mutableMapOf<String, IngredientRef>()

    /**
     * What line [lineId] (as saved on the recipe, never a currently-showing scaling) equals in
     * [targetUnit] — purely informational, shown in a bottom sheet, never written to the recipe.
     */
    fun tryConvert(lineId: String, targetUnit: MeasureUnit): ConversionResult? {
        val line = ((uiState.value as? RecipeDetailUiState.Content)?.recipe)
            ?.ingredients?.firstOrNull { it.id == lineId } ?: return null
        val qty = line.quantity ?: return null

        val ref = densityOverrides[line.ingredient.id] ?: line.ingredient.toRef()
        converter.convert(qty, line.unit, targetUnit, ref)?.let {
            return ConversionResult.Converted(formatter.format(it, targetUnit))
        }

        val requirement = requirementFor(line.unit, targetUnit, ref)
        return if (requirement == DensityRequirement.NONE || requirement == DensityRequirement.UNSUPPORTED) {
            ConversionResult.Unsupported
        } else {
            ConversionResult.NeedsDensity(
                DensityPromptRequest(
                    ingredientId = line.ingredient.id,
                    ingredientName = line.ingredient.name,
                    requirement = requirement,
                ),
            )
        }
    }

    /**
     * The user just answered the "density unknown" prompt: recorded in [densityOverrides] straight
     * away so the very next [tryConvert] call already sees it, and persisted in the background.
     */
    fun onDensityPromptConfirm(ingredientId: String, densityGramsPerMl: Double?, itemWeightGrams: Double?) {
        val base = (uiState.value as? RecipeDetailUiState.Content)?.recipe?.ingredients
            ?.firstOrNull { it.ingredient.id == ingredientId }
            ?.ingredient?.toRef()
            ?: densityOverrides[ingredientId]
            ?: IngredientRef(id = ingredientId, normalisedName = "")
        densityOverrides[ingredientId] = base.copy(
            densityGramsPerMl = densityGramsPerMl ?: base.densityGramsPerMl,
            itemWeightGrams = itemWeightGrams ?: base.itemWeightGrams,
        )
        viewModelScope.launch {
            ingredientRepository.setDensityData(ingredientId, densityGramsPerMl, itemWeightGrams)
        }
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

    /** The same lines, but with quantities from a resolved scaling instead of the recipe as saved. */
    private fun Recipe.toDetailLines(scaled: ScaledRecipe): List<DetailLine> {
        val notesByLineId = ingredients.associate { it.id to it.note }
        return scaled.lines.map { line ->
            DetailLine(
                id = line.lineId,
                name = line.ingredientName,
                quantityText = line.displayText,
                note = notesByLineId[line.lineId],
            )
        }
    }

    private sealed interface VariantSelection {
        data object Auto : VariantSelection
        data object Original : VariantSelection
        data class Specific(val variantId: String) : VariantSelection
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
