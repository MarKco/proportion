package com.ilsecondodasinistra.proportion.feature.cook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CookingModeRoute(
    onBack: () -> Unit,
    viewModel: CookingModeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    CookingModeScreen(
        state = state,
        onBack = onBack,
        onStepChecked = viewModel::onStepChecked,
        onToggleIngredients = viewModel::onToggleIngredients,
    )
}

/**
 * The at-the-stove screen: large steps, ingredients one tap away, the screen kept awake for as
 * long as it stays composed. Nothing here recomputes the scale — that decision was already made
 * when [CookViewModel] handed off to this route.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookingModeScreen(
    state: CookingModeUiState,
    onBack: () -> Unit,
    onStepChecked: (Int, Boolean) -> Unit,
    onToggleIngredients: (Boolean) -> Unit,
) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        modifier = Modifier.testTag("cooking_mode_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.title.ifBlank { stringResource(R.string.cooking_mode_title) },
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("cooking_mode_close")) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cooking_mode_close),
                        )
                    }
                },
                actions = {
                    if (state.steps.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.cooking_mode_progress,
                                state.doneCount,
                                state.steps.size,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(end = 16.dp).testTag("cooking_mode_progress"),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onToggleIngredients(true) },
                icon = {
                    // On-device TalkBack testing showed this FAB's visible text label is not
                    // reliably exposed to accessibility, so the icon carries the name instead.
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = stringResource(R.string.cooking_mode_ingredients),
                    )
                },
                text = { Text(stringResource(R.string.cooking_mode_ingredients)) },
                modifier = Modifier.testTag("cooking_ingredients_button"),
            )
        },
    ) { padding ->
        if (state.steps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.cooking_mode_no_steps),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(24.dp).testTag("cooking_mode_no_steps"),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 16.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(state.steps, key = { it.index }) { step ->
                    CookingStepRow(step = step, onChecked = { onStepChecked(step.index, it) })
                }
            }
        }
    }

    if (state.showIngredients) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { onToggleIngredients(false) },
            sheetState = sheetState,
            modifier = Modifier.testTag("cooking_ingredients_sheet"),
        ) {
            CookingIngredientsSheet(state = state)
        }
    }
}

@Composable
private fun CookingStepRow(step: CookingStep, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .toggleable(value = step.isDone, onValueChange = onChecked)
            .testTag("cooking_step_${step.index}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = step.isDone, onCheckedChange = null)
        Text(
            text = step.text,
            style = MaterialTheme.typography.headlineSmall,
            textDecoration = if (step.isDone) TextDecoration.LineThrough else null,
            color = if (step.isDone) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun CookingIngredientsSheet(state: CookingModeUiState) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.cooking_mode_ingredients),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        state.servingsText?.let { servings ->
            Text(
                text = stringResource(R.string.cook_achievable_card, servings),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        state.ingredients.forEach { ingredient ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = ingredient.name, style = MaterialTheme.typography.bodyLarge)
                Text(text = ingredient.amountText, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
