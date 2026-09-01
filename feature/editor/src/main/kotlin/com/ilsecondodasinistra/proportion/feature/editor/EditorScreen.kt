package com.ilsecondodasinistra.proportion.feature.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilsecondodasinistra.proportion.core.model.Ingredient
import com.ilsecondodasinistra.proportion.core.ui.component.UnitPicker
import com.ilsecondodasinistra.proportion.core.ui.tagLabel
import com.ilsecondodasinistra.proportion.core.ui.unitLabel

@Composable
fun EditorRoute(
    onDone: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onDone()
    }

    EditorScreen(
        state = state,
        onTitleChange = viewModel::onTitleChange,
        onServingsChange = viewModel::onServingsChange,
        onLineNameChange = viewModel::onLineNameChange,
        onSuggestionPick = viewModel::onSuggestionPick,
        onLineQuantityChange = viewModel::onLineQuantityChange,
        onLineUnitChange = viewModel::onLineUnitChange,
        onAddLine = viewModel::onAddLine,
        onRemoveLine = viewModel::onRemoveLine,
        onStepChange = viewModel::onStepChange,
        onAddStep = viewModel::onAddStep,
        onRemoveStep = viewModel::onRemoveStep,
        onTagToggle = viewModel::onTagToggle,
        onCreateTag = viewModel::onCreateTag,
        onSave = viewModel::onSave,
        onBack = onDone,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: EditorUiState,
    onTitleChange: (String) -> Unit,
    onServingsChange: (Int?) -> Unit,
    onLineNameChange: (Int, String) -> Unit,
    onSuggestionPick: (Int, Ingredient) -> Unit,
    onLineQuantityChange: (Int, String) -> Unit,
    onLineUnitChange: (Int, com.ilsecondodasinistra.proportion.core.model.MeasureUnit) -> Unit,
    onAddLine: () -> Unit,
    onRemoveLine: (Int) -> Unit,
    onStepChange: (Int, String) -> Unit,
    onAddStep: () -> Unit,
    onRemoveStep: (Int) -> Unit,
    onTagToggle: (String) -> Unit,
    onCreateTag: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    var showDiscardDialog by remember { mutableStateOf(false) }
    val leave = { if (state.isDirty) showDiscardDialog = true else onBack() }

    BackHandler(enabled = state.isDirty) { showDiscardDialog = true }

    Scaffold(
        modifier = Modifier.testTag("editor_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.editor_title_edit else R.string.editor_title_new,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = leave, modifier = Modifier.testTag("editor_back")) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.editor_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onSave,
                        enabled = !state.isSaving,
                        modifier = Modifier.testTag("editor_save"),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.editor_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.editor_field_title)) },
                singleLine = true,
                isError = ValidationError.TITLE_REQUIRED in state.errors,
                supportingText = {
                    if (ValidationError.TITLE_REQUIRED in state.errors) {
                        Text(stringResource(R.string.editor_error_title))
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("editor_title_field"),
            )

            ServingsStepper(state.servings, onServingsChange)

            SectionTitle(stringResource(R.string.editor_section_tags))
            TagSection(state, onTagToggle, onCreateTag)

            SectionTitle(stringResource(R.string.editor_section_ingredients))
            if (ValidationError.INGREDIENTS_REQUIRED in state.errors) {
                ErrorText(stringResource(R.string.editor_error_ingredients))
            }
            if (ValidationError.QUANTITY_REQUIRED in state.errors) {
                ErrorText(stringResource(R.string.editor_error_quantity))
            }

            state.lines.forEachIndexed { index, line ->
                IngredientEditorRow(
                    index = index,
                    line = line,
                    suggestions = if (state.suggestionLineIndex == index) state.suggestions else emptyList(),
                    onNameChange = onLineNameChange,
                    onSuggestionPick = onSuggestionPick,
                    onQuantityChange = onLineQuantityChange,
                    onUnitChange = onLineUnitChange,
                    onRemove = { onRemoveLine(index) },
                )
            }

            TextButton(onClick = onAddLine, modifier = Modifier.testTag("editor_add_ingredient")) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.editor_add_ingredient),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            SectionTitle(stringResource(R.string.editor_section_steps))
            state.steps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = step,
                        onValueChange = { onStepChange(index, it) },
                        label = { Text(stringResource(R.string.editor_step_number, index + 1)) },
                        modifier = Modifier.weight(1f).testTag("editor_step_$index"),
                    )
                    IconButton(onClick = { onRemoveStep(index) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.editor_remove),
                        )
                    }
                }
            }

            TextButton(onClick = onAddStep, modifier = Modifier.testTag("editor_add_step")) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.editor_add_step),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Column(modifier = Modifier.padding(bottom = 32.dp)) {}
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBack()
                    },
                    modifier = Modifier.testTag("discard_confirm"),
                ) {
                    Text(stringResource(R.string.editor_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.editor_discard_cancel))
                }
            },
            modifier = Modifier.testTag("discard_dialog"),
        )
    }
}

@Composable
private fun ServingsStepper(servings: Int?, onChange: (Int?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.editor_field_servings),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onChange(((servings ?: 1) - 1).coerceAtLeast(1)) },
            modifier = Modifier.testTag("servings_minus"),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = null)
        }
        Text(
            text = (servings ?: 1).toString(),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.testTag("servings_value"),
        )
        IconButton(
            onClick = { onChange((servings ?: 1) + 1) },
            modifier = Modifier.testTag("servings_plus"),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
        }
    }
}

@Composable
private fun TagSection(
    state: EditorUiState,
    onTagToggle: (String) -> Unit,
    onCreateTag: (String) -> Unit,
) {
    var newTag by remember { mutableStateOf("") }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        state.availableTags.forEach { tag ->
            FilterChip(
                selected = tag.id in state.selectedTagIds,
                onClick = { onTagToggle(tag.id) },
                label = { Text(tagLabel(tag)) },
                modifier = Modifier.testTag("editor_tag_${tag.key ?: tag.name}"),
            )
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newTag,
            onValueChange = { newTag = it },
            label = { Text(stringResource(R.string.editor_new_tag)) },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("editor_new_tag_field"),
        )
        IconButton(
            onClick = {
                onCreateTag(newTag)
                newTag = ""
            },
            enabled = newTag.isNotBlank(),
            modifier = Modifier.testTag("editor_new_tag_add"),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.editor_new_tag))
        }
    }
}

@Composable
private fun IngredientEditorRow(
    index: Int,
    line: EditorLine,
    suggestions: List<Ingredient>,
    onNameChange: (Int, String) -> Unit,
    onSuggestionPick: (Int, Ingredient) -> Unit,
    onQuantityChange: (Int, String) -> Unit,
    onUnitChange: (Int, com.ilsecondodasinistra.proportion.core.model.MeasureUnit) -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().testTag("editor_line_$index")) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = line.name,
                    onValueChange = { onNameChange(index, it) },
                    label = { Text(stringResource(R.string.editor_ingredient_name)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("editor_line_name_$index"),
                )
                IconButton(onClick = onRemove, modifier = Modifier.testTag("editor_line_remove_$index")) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.editor_remove),
                    )
                }
            }

            if (suggestions.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 4.dp).testTag("editor_suggestions_$index"),
                ) {
                    suggestions.forEach { ingredient ->
                        AssistChip(
                            onClick = { onSuggestionPick(index, ingredient) },
                            label = { Text(ingredient.name) },
                            modifier = Modifier.testTag("suggestion_${ingredient.id}"),
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = line.quantity,
                    onValueChange = { onQuantityChange(index, it) },
                    label = { Text(stringResource(R.string.editor_quantity)) },
                    singleLine = true,
                    enabled = line.unit.isScalable,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(140.dp).testTag("editor_line_qty_$index"),
                )
                UnitPicker(
                    selected = line.unit,
                    unitName = { unit -> unitLabel(unit) },
                    onSelect = { onUnitChange(index, it) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(text = text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}
