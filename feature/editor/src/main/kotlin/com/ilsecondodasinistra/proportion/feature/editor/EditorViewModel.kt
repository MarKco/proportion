package com.ilsecondodasinistra.proportion.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.ilsecondodasinistra.proportion.feature.editor.navigation.EditorRouteKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilsecondodasinistra.proportion.core.domain.IngredientNames
import com.ilsecondodasinistra.proportion.core.domain.repository.IngredientRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.TagRepository
import com.ilsecondodasinistra.proportion.core.domain.unit.DensityRequirement
import com.ilsecondodasinistra.proportion.core.domain.unit.IngredientRef
import com.ilsecondodasinistra.proportion.core.domain.unit.UnitConverter
import com.ilsecondodasinistra.proportion.core.domain.unit.requirementFor
import com.ilsecondodasinistra.proportion.core.domain.unit.toRef
import com.ilsecondodasinistra.proportion.core.model.Ingredient
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.RecipeIngredient
import com.ilsecondodasinistra.proportion.core.model.Tag
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the recipe draft.
 *
 * Quantities stay as text while editing and are parsed once, on save: a field that rejects "1," the
 * moment you type the comma is unusable. Ingredient names go through the catalogue on save, so the
 * same ingredient typed three different ways still resolves to one row.
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
    private val tagRepository: TagRepository,
    private val converter: UnitConverter,
) : ViewModel() {

    private val editedRecipeId: String? = savedStateHandle.toRoute<EditorRouteKey>().recipeId

    private val _uiState = MutableStateFlow(
        EditorUiState(lines = listOf(EditorLine(id = newLineId()))),
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var catalogue: List<Ingredient> = emptyList()

    init {
        viewModelScope.launch {
            tagRepository.observeAll().collect { tags ->
                _uiState.update { it.copy(availableTags = tags) }
            }
        }
        viewModelScope.launch {
            ingredientRepository.observeAll().collect { catalogue = it }
        }
        if (editedRecipeId != null) {
            viewModelScope.launch { loadExisting(editedRecipeId) }
        }
    }

    private suspend fun loadExisting(id: String) {
        val recipe = recipeRepository.observeRecipe(id).first() ?: return
        _uiState.update { state ->
            state.copy(
                recipeId = recipe.id,
                title = recipe.title,
                servings = recipe.servings,
                notes = recipe.notes.orEmpty(),
                lines = recipe.ingredients.sortedBy { it.position }.map { line ->
                    EditorLine(
                        id = line.id,
                        name = line.ingredient.name,
                        quantity = line.quantity.toEditableText(),
                        unit = line.unit,
                        // A saved line's unit is the user's own earlier answer, so changing it here
                        // is a real unit change and does re-express the quantity.
                        isUnitChosen = true,
                        note = line.note,
                    )
                },
                steps = recipe.steps.ifEmpty { listOf("") },
                selectedTagIds = recipe.tags.map { it.id }.toSet(),
                isDirty = false,
            )
        }
    }

    fun onTitleChange(title: String) = edit { it.copy(title = title) }

    fun onServingsChange(servings: Int?) = edit { it.copy(servings = servings) }

    fun onNotesChange(notes: String) = edit { it.copy(notes = notes) }

    fun onLineNameChange(index: Int, name: String) = edit { state ->
        val matches = if (name.isBlank()) {
            emptyList()
        } else {
            val needle = IngredientNames.normalise(name)
            catalogue.filter { it.normalisedName.contains(needle) }.take(MAX_SUGGESTIONS)
        }
        state.copy(
            lines = state.lines.mapIndexed { i, line ->
                if (i == index) line.copy(name = name) else line
            },
            suggestions = matches,
            suggestionLineIndex = index.takeIf { matches.isNotEmpty() },
        )
    }

    /**
     * Keeps whatever unit the line already had if it's still compatible with the picked
     * ingredient (same [com.ilsecondodasinistra.proportion.core.model.UnitCategory]) — e.g. the
     * user already chose kilograms and picks a gram-default ingredient, kilograms stay. An empty
     * or incompatible unit gets the ingredient's own default, as a hint only: it does not count as
     * the user having chosen the unit, so the next pick still won't convert the typed quantity.
     */
    fun onSuggestionPick(index: Int, ingredient: Ingredient) = edit { state ->
        state.copy(
            lines = state.lines.mapIndexed { i, line ->
                if (i == index) {
                    val kept = line.unit?.category == ingredient.defaultUnit.category
                    line.copy(
                        name = ingredient.name,
                        unit = if (kept) line.unit else ingredient.defaultUnit,
                        isUnitChosen = kept && line.isUnitChosen,
                    )
                } else {
                    line
                }
            },
            suggestions = emptyList(),
            suggestionLineIndex = null,
        )
    }

    fun onLineQuantityChange(index: Int, quantity: String) = edit { state ->
        state.copy(
            lines = state.lines.mapIndexed { i, line ->
                if (i == index) line.copy(quantity = quantity) else line
            },
        )
    }

    /**
     * Switching a line's unit re-expresses its quantity to stay equivalent (3 cups sugar becomes
     * 300 g), rather than leaving the number unchanged under a new label. When the ingredient
     * (matched live, by name, against the catalogue) is missing the density/item-weight the
     * conversion would need, asks for it via [pendingDensityPrompt] instead of silently giving up —
     * unless the ingredient isn't in the catalogue at all yet (still being typed), in which case
     * there is nowhere to persist an answer and the quantity is simply left as typed.
     *
     * Answering the unit question for the first time is not a switch: the number was typed with no
     * unit chosen (see [EditorLine.isUnitChosen]), so it means what it says and is left alone.
     */
    fun onLineUnitChange(index: Int, unit: MeasureUnit) {
        val line = _uiState.value.lines.getOrNull(index) ?: return
        val ingredient = catalogue.firstOrNull { it.normalisedName == IngredientNames.normalise(line.name) }
        val qty = line.quantity.parseQuantity()
        // Only a unit the user picked themselves is something to convert away from.
        val from = line.unit.takeIf { line.isUnitChosen }

        if (from == null || qty == null || from == unit) {
            setLineUnit(index, unit)
            return
        }

        val converted = converter.convert(qty, from, unit, ingredient?.toRef())
        if (converted != null) {
            setLineUnit(index, unit, quantity = converted.toEditableText())
            return
        }

        val requirement = requirementFor(from, unit, ingredient?.toRef())
        val recoverable = requirement != DensityRequirement.NONE && requirement != DensityRequirement.UNSUPPORTED
        if (ingredient == null || !recoverable) {
            setLineUnit(index, unit)
            return
        }

        edit { state ->
            state.copy(
                lines = state.lines.mapIndexed { i, l ->
                    if (i == index) l.copy(unit = unit, isUnitChosen = true) else l
                },
                pendingDensityPrompt = DensityPromptRequest(
                    lineIndex = index,
                    ingredientId = ingredient.id,
                    ingredientName = ingredient.name,
                    requirement = requirement,
                    qty = qty,
                    fromUnit = from,
                    toUnit = unit,
                ),
            )
        }
    }

    private fun setLineUnit(index: Int, unit: MeasureUnit, quantity: String? = null) = edit { state ->
        state.copy(
            lines = state.lines.mapIndexed { i, line ->
                if (i == index) {
                    line.copy(unit = unit, isUnitChosen = true, quantity = quantity ?: line.quantity)
                } else {
                    line
                }
            },
        )
    }

    /** The user just answered the "density unknown" prompt: persist it, then retry the conversion. */
    fun onDensityPromptConfirm(densityGramsPerMl: Double?, itemWeightGrams: Double?) {
        val request = _uiState.value.pendingDensityPrompt ?: return
        viewModelScope.launch {
            ingredientRepository.setDensityData(request.ingredientId, densityGramsPerMl, itemWeightGrams)
            val ingredient = catalogue.firstOrNull { it.id == request.ingredientId }
            val ref = (ingredient?.toRef() ?: IngredientRef(id = request.ingredientId, normalisedName = "")).copy(
                densityGramsPerMl = densityGramsPerMl ?: ingredient?.densityGramsPerMl,
                itemWeightGrams = itemWeightGrams ?: ingredient?.itemWeightGrams,
            )
            val converted = converter.convert(request.qty, request.fromUnit, request.toUnit, ref)
            edit { state ->
                state.copy(
                    lines = state.lines.mapIndexed { i, line ->
                        if (i == request.lineIndex && converted != null) {
                            line.copy(quantity = converted.toEditableText())
                        } else {
                            line
                        }
                    },
                    pendingDensityPrompt = null,
                )
            }
        }
    }

    fun onDensityPromptDismiss() = edit { it.copy(pendingDensityPrompt = null) }

    fun onAddLine() = edit { state ->
        val newLine = EditorLine(id = newLineId())
        state.copy(lines = state.lines + newLine, justAddedLineId = newLine.id)
    }

    /** Called once the screen has focused/scrolled to the line [onAddLine] just added. */
    fun onNewLineFocusHandled() = edit { it.copy(justAddedLineId = null) }

    fun onRemoveLine(index: Int) = edit { state ->
        val remaining = state.lines.filterIndexed { i, _ -> i != index }
        state.copy(lines = remaining.ifEmpty { listOf(EditorLine(id = newLineId())) })
    }

    fun onMoveLine(from: Int, to: Int) = edit { state ->
        state.copy(lines = state.lines.moved(from, to))
    }

    fun onStepChange(index: Int, text: String) = edit { state ->
        state.copy(steps = state.steps.mapIndexed { i, step -> if (i == index) text else step })
    }

    fun onAddStep() = edit { state ->
        val steps = state.steps + ""
        state.copy(steps = steps, justAddedStepIndex = steps.lastIndex)
    }

    /** Called once the screen has focused the step [onAddStep] just added. */
    fun onNewStepFocusHandled() = edit { it.copy(justAddedStepIndex = null) }

    fun onRemoveStep(index: Int) = edit { state ->
        val remaining = state.steps.filterIndexed { i, _ -> i != index }
        state.copy(steps = remaining.ifEmpty { listOf("") })
    }

    fun onMoveStep(from: Int, to: Int) = edit { state ->
        state.copy(steps = state.steps.moved(from, to))
    }

    fun onTagToggle(tagId: String) = edit { state ->
        state.copy(
            selectedTagIds = if (tagId in state.selectedTagIds) {
                state.selectedTagIds - tagId
            } else {
                state.selectedTagIds + tagId
            },
        )
    }

    fun onCreateTag(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val tag = tagRepository.findOrCreateUserTag(name)
            edit { it.copy(selectedTagIds = it.selectedTagIds + tag.id) }
        }
    }

    fun onSave() {
        val state = _uiState.value
        val errors = state.validate()
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = errors) }
            return
        }

        _uiState.update { it.copy(errors = emptySet(), isSaving = true) }
        viewModelScope.launch {
            val recipe = state.toRecipe()
            recipeRepository.upsert(recipe)
            _uiState.update { it.copy(isSaving = false, isSaved = true, isDirty = false) }
        }
    }

    private fun EditorUiState.validate(): Set<ValidationError> = buildSet {
        if (title.isBlank()) add(ValidationError.TITLE_REQUIRED)

        val named = lines.filter { it.name.isNotBlank() }
        if (named.isEmpty()) add(ValidationError.INGREDIENTS_REQUIRED)

        // A line with no unit yet is treated as scalable: it still needs a number.
        val missingQuantity = named.any { line ->
            line.unit?.isScalable != false && line.quantity.parseQuantity() == null
        }
        if (missingQuantity) add(ValidationError.QUANTITY_REQUIRED)

        if (named.any { it.unit == null }) add(ValidationError.UNIT_REQUIRED)
    }

    private suspend fun EditorUiState.toRecipe(): Recipe {
        val ingredients = lines
            .mapNotNull { line -> line.unit?.takeIf { line.name.isNotBlank() }?.let { line to it } }
            .mapIndexed { position, (line, unit) ->
                val ingredient = ingredientRepository.findOrCreate(line.name, unit)
                RecipeIngredient(
                    id = line.id,
                    ingredient = ingredient,
                    position = position,
                    quantity = if (unit.isScalable) line.quantity.parseQuantity() else null,
                    unit = unit,
                    note = line.note?.takeIf { it.isNotBlank() },
                )
            }

        val selectedTags: List<Tag> = availableTags.filter { it.id in selectedTagIds }

        return Recipe(
            id = recipeId ?: UUID.randomUUID().toString(),
            title = title.trim(),
            servings = servings,
            steps = steps.map { it.trim() }.filter { it.isNotEmpty() },
            ingredients = ingredients,
            tags = selectedTags,
            notes = notes.trim().takeIf { it.isNotEmpty() },
        )
    }

    private fun edit(transform: (EditorUiState) -> EditorUiState) {
        _uiState.update { current ->
            transform(current).copy(isDirty = true, errors = emptySet())
        }
    }

    private companion object {
        const val MAX_SUGGESTIONS = 5
    }
}

private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/** Accepts both decimal separators: an Italian keyboard offers the comma. */
private fun String.parseQuantity(): Double? =
    trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }

private fun Double?.toEditableText(): String = when {
    this == null -> ""
    this % 1.0 == 0.0 -> toInt().toString()
    else -> toString()
}

private fun newLineId(): String = UUID.randomUUID().toString()
