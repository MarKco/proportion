package com.ilsecondodasinistra.proportion.feature.recipes.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeSort
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.ui.component.EmptyState
import com.ilsecondodasinistra.proportion.core.ui.component.SelectableTagChipRow
import com.ilsecondodasinistra.proportion.core.ui.component.TagChipRow
import com.ilsecondodasinistra.proportion.core.ui.tagAccentColor
import com.ilsecondodasinistra.proportion.feature.recipes.R

@Composable
fun RecipeListRoute(
    onRecipeClick: (String) -> Unit,
    onAddRecipe: () -> Unit,
    viewModel: RecipeListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    RecipeListScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onTagToggle = viewModel::onTagToggle,
        onIngredientToggle = viewModel::onIngredientToggle,
        onSortChange = viewModel::onSortChange,
        onClearFilters = viewModel::onClearFilters,
        onRecipeClick = onRecipeClick,
        onAddRecipe = onAddRecipe,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    state: RecipeListUiState,
    onQueryChange: (String) -> Unit,
    onTagToggle: (String) -> Unit,
    onIngredientToggle: (String) -> Unit,
    onSortChange: (RecipeSort) -> Unit,
    onClearFilters: () -> Unit,
    onRecipeClick: (String) -> Unit,
    onAddRecipe: () -> Unit,
) {
    var showIngredientSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.testTag("recipes_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipes_title)) },
                actions = { SortMenu(state.sort, onSortChange) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddRecipe,
                modifier = Modifier.testTag("add_recipe_fab"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.recipes_add))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {

            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.recipes_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.recipes_clear_filters),
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("search_field"),
            )

            if (state.availableTags.isNotEmpty()) {
                SelectableTagChipRow(
                    tags = state.availableTags,
                    selectedIds = state.selectedTagIds,
                    onToggle = onTagToggle,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FilledTonalButton(
                    onClick = { showIngredientSheet = true },
                    modifier = Modifier.testTag("ingredient_filter_button"),
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null)
                    Text(
                        text = stringResource(R.string.recipes_filter_ingredients),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                Text(
                    text = pluralStringResource(
                        R.plurals.recipes_result_count,
                        state.resultCount,
                        state.resultCount,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("result_count"),
                )
            }

            when {
                state.libraryIsEmpty -> EmptyState(
                    title = stringResource(R.string.recipes_empty_library_title),
                    message = stringResource(R.string.recipes_empty_library_message),
                    actionLabel = stringResource(R.string.recipes_empty_library_action),
                    onAction = onAddRecipe,
                    testTag = "empty_library_state",
                )

                state.recipes.isEmpty() && !state.isLoading -> EmptyState(
                    title = stringResource(R.string.recipes_no_results_title),
                    message = stringResource(R.string.recipes_no_results_message),
                    actionLabel = stringResource(R.string.recipes_clear_filters),
                    onAction = onClearFilters,
                    testTag = "no_results_state",
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("recipe_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
                ) {
                    items(state.recipes, key = { it.id }) { recipe ->
                        RecipeCard(recipe = recipe, onClick = { onRecipeClick(recipe.id) })
                    }
                }
            }
        }

        if (showIngredientSheet) {
            ModalBottomSheet(onDismissRequest = { showIngredientSheet = false }) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = stringResource(R.string.recipes_filter_ingredients_sheet),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LazyColumn(modifier = Modifier.testTag("ingredient_filter_list")) {
                        items(state.availableIngredients, key = { it.id }) { ingredient ->
                            val checked = ingredient.id in state.selectedIngredientIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = checked,
                                        onValueChange = { onIngredientToggle(ingredient.id) },
                                    )
                                    .padding(vertical = 4.dp)
                                    .testTag("ingredient_filter_${ingredient.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Text(
                                    text = ingredient.name,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = onClearFilters,
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        Text(stringResource(R.string.recipes_clear_filters))
                    }
                }
            }
        }
    }
}

@Composable
private fun SortMenu(current: RecipeSort, onSortChange: (RecipeSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }, modifier = Modifier.testTag("sort_button")) {
        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.recipes_sort))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        RecipeSort.entries.forEach { sort ->
            DropdownMenuItem(
                text = { Text(stringResource(sort.labelRes())) },
                onClick = {
                    onSortChange(sort)
                    expanded = false
                },
                trailingIcon = if (sort == current) {
                    { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) }
                } else {
                    null
                },
                modifier = Modifier.testTag("sort_option_${sort.name}"),
            )
        }
    }
}

private fun RecipeSort.labelRes(): Int = when (this) {
    RecipeSort.RECENT -> R.string.recipes_sort_recent
    RecipeSort.ALPHABETICAL -> R.string.recipes_sort_alphabetical
    RecipeSort.MOST_COOKED -> R.string.recipes_sort_most_cooked
}

@Composable
private fun RecipeCard(recipe: Recipe, onClick: () -> Unit) {
    val accentColor = recipe.tags.firstOrNull()?.let { tagAccentColor(it) }
        ?: MaterialTheme.colorScheme.outlineVariant

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("recipe_card_${recipe.id}"),
    ) {
        // drawBehind (not an accent Box next to the content) because a match-height sibling needs
        // IntrinsicSize.Min, which crashes inside a LazyColumn's SubcomposeLayout.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind { drawRect(color = accentColor, size = Size(4.dp.toPx(), size.height)) }
                .padding(start = 20.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Text(text = recipe.title, style = MaterialTheme.typography.titleMedium)

            Text(
                text = buildString {
                    append(
                        recipe.servings?.let {
                            pluralString(R.plurals.recipe_servings, it)
                        } ?: stringResourceValue(R.string.recipe_no_servings),
                    )
                    append("  ·  ")
                    append(pluralString(R.plurals.recipe_ingredient_count, recipe.ingredients.size))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            if (recipe.tags.isNotEmpty()) {
                TagChipRow(tags = recipe.tags, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

@Composable
private fun pluralString(resId: Int, count: Int): String =
    pluralStringResource(resId, count, count)

@Composable
private fun stringResourceValue(resId: Int): String = stringResource(resId)
