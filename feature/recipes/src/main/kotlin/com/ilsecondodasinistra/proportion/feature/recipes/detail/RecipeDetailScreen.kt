package com.ilsecondodasinistra.proportion.feature.recipes.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilsecondodasinistra.proportion.core.ui.component.EmptyState
import com.ilsecondodasinistra.proportion.core.ui.component.LoadingState
import com.ilsecondodasinistra.proportion.core.transfer.PlainTextStrings
import com.ilsecondodasinistra.proportion.core.ui.RecipeSharing
import com.ilsecondodasinistra.proportion.core.ui.component.TagChipRow
import com.ilsecondodasinistra.proportion.feature.recipes.R

@Composable
fun RecipeDetailRoute(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onCook: (String) -> Unit,
    viewModel: RecipeDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.recipe_share_chooser)
    val plainStrings = plainTextStrings()

    RecipeDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = onEdit,
        onCook = onCook,
        onShareText = {
            viewModel.onShareText(plainStrings) { text ->
                RecipeSharing.shareText(context, text, chooserTitle)
            }
        },
        onShareFile = { title ->
            viewModel.onShareFile { content ->
                RecipeSharing.shareProportionFile(
                    context = context,
                    fileName = RecipeSharing.fileNameFor(title),
                    content = content,
                    chooserTitle = chooserTitle,
                )
            }
        },
        onFavouriteToggle = viewModel::onFavouriteToggle,
        onDelete = {
            viewModel.onDelete()
            onBack()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    state: RecipeDetailUiState,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onCook: (String) -> Unit,
    onShareText: () -> Unit = {},
    onShareFile: (String) -> Unit = {},
    onFavouriteToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val content = state as? RecipeDetailUiState.Content

    Scaffold(
        modifier = Modifier.testTag("recipe_detail_screen"),
        topBar = {
            TopAppBar(
                title = { Text(content?.recipe?.title.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back")) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.recipe_detail_back),
                        )
                    }
                },
                actions = {
                    if (content != null) {
                        FavouriteAction(content.recipe.isFavourite, onFavouriteToggle)
                        OverflowMenu(
                            onEdit = { onEdit(content.recipe.id) },
                            onShareText = onShareText,
                            onShareFile = { onShareFile(content.recipe.title) },
                            onDelete = onDelete,
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            RecipeDetailUiState.Loading -> LoadingState(modifier = Modifier.padding(padding))

            RecipeDetailUiState.NotFound -> EmptyState(
                title = stringResource(R.string.recipe_detail_not_found),
                message = "",
                modifier = Modifier.padding(padding),
                testTag = "detail_not_found",
            )

            is RecipeDetailUiState.Content -> DetailBody(
                content = state,
                onCook = { onCook(state.recipe.id) },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun FavouriteAction(isFavourite: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle, modifier = Modifier.testTag("detail_favourite")) {
        Icon(
            imageVector = if (isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(
                if (isFavourite) R.string.recipe_detail_favourite_remove
                else R.string.recipe_detail_favourite_add,
            ),
        )
    }
}

@Composable
private fun OverflowMenu(
    onEdit: () -> Unit,
    onShareText: () -> Unit,
    onShareFile: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }, modifier = Modifier.testTag("detail_overflow")) {
        Icon(Icons.Filled.MoreVert, contentDescription = null)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.recipe_detail_edit)) },
            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            onClick = {
                expanded = false
                onEdit()
            },
            modifier = Modifier.testTag("detail_edit"),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.recipe_share_text)) },
            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
            onClick = {
                expanded = false
                onShareText()
            },
            modifier = Modifier.testTag("detail_share_text"),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.recipe_share_file)) },
            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
            onClick = {
                expanded = false
                onShareFile()
            },
            modifier = Modifier.testTag("detail_share_file"),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.recipe_detail_delete)) },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            onClick = {
                expanded = false
                onDelete()
            },
            modifier = Modifier.testTag("detail_delete"),
        )
    }
}

@Composable
private fun DetailBody(
    content: RecipeDetailUiState.Content,
    onCook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recipe = content.recipe

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = recipe.servings?.let {
                pluralStringResource(R.plurals.recipe_servings, it, it)
            } ?: stringResource(R.string.recipe_no_servings),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (recipe.tags.isNotEmpty()) {
            TagChipRow(tags = recipe.tags, modifier = Modifier.padding(top = 12.dp))
        }

        Button(
            onClick = onCook,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .testTag("cook_button"),
        ) {
            Text(stringResource(R.string.recipe_detail_cook))
        }

        SectionTitle(stringResource(R.string.recipe_detail_ingredients))
        content.lines.forEach { line ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("detail_line_${line.id}"),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = line.name, style = MaterialTheme.typography.bodyLarge)
                    line.note?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = line.quantityText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            HorizontalDivider()
        }

        if (recipe.steps.isNotEmpty()) {
            SectionTitle(stringResource(R.string.recipe_detail_steps))
            recipe.steps.forEachIndexed { index, step ->
                Row(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Text(text = step, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        if (content.variants.isNotEmpty()) {
            SectionTitle(stringResource(R.string.recipe_detail_variants))
            content.variants.forEach { variant ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(text = variant.label, modifier = Modifier.padding(16.dp))
                }
            }
        }

        Column(modifier = Modifier.padding(bottom = 32.dp)) {}
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
    )
}

/**
 * The translated words the plain-text share needs.
 *
 * The two with a placeholder are lambdas over the context, because the count is only known when the
 * text is built; the rest resolve here.
 */
@Composable
private fun plainTextStrings(): PlainTextStrings {
    val context = LocalContext.current
    return PlainTextStrings(
        servings = { count -> context.getString(R.string.plain_servings, count) },
        scaledFor = { value -> context.getString(R.string.plain_scaled, value) },
        notPerPerson = stringResource(R.string.plain_not_per_person),
        ingredientsTitle = stringResource(R.string.plain_ingredients),
        methodTitle = stringResource(R.string.plain_method),
        attribution = stringResource(R.string.plain_attribution),
    )
}
