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
     * user already chose kilograms and picks a gram-default ingredient, kilograms stay. Only an
     * incompatible unit (or the untouched default) gets replaced by the ingredient's own default.
     */
    fun onSuggestionPick(index: Int, ingredient: Ingredient) = edit { state ->
        state.copy(
            lines = state.lines.mapIndexed { i, line ->
                if (i == index) {
                    val unit = if (line.unit.category == ingredient.defaultUnit.category) {
                        line.unit
                    } else {
                        ingredient.defaultUnit
                    }
                    line.copy(name = ingredient.name, unit = unit)
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

    fun onLineUnitChange(index: Int, unit: MeasureUnit) = edit { state ->
        state.copy(
            lines = state.lines.mapIndexed { i, line ->
                if (i == index) line.copy(unit = unit) else line
            },
        )
    }

    fun onAddLine() = edit { it.copy(lines = it.lines + EditorLine(id = newLineId())) }

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

    fun onAddStep() = edit { it.copy(steps = it.steps + "") }

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

        val missingQuantity = named.any { line ->
            line.unit.isScalable && line.quantity.parseQuantity() == null
        }
        if (missingQuantity) add(ValidationError.QUANTITY_REQUIRED)
    }

    private suspend fun EditorUiState.toRecipe(): Recipe {
        val ingredients = lines
            .filter { it.name.isNotBlank() }
            .mapIndexed { position, line ->
                val ingredient = ingredientRepository.findOrCreate(line.name, line.unit)
                RecipeIngredient(
                    id = line.id,
                    ingredient = ingredient,
                    position = position,
                    quantity = if (line.unit.isScalable) line.quantity.parseQuantity() else null,
                    unit = line.unit,
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

    private companion object {
        const val MAX_SUGGESTIONS = 5
    }
}
