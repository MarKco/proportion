package com.ilsecondodasinistra.proportion.feature.recipes.detail

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.ui.component.DensityPromptDialog
import com.ilsecondodasinistra.proportion.core.ui.component.EmptyState
import com.ilsecondodasinistra.proportion.core.ui.component.LoadingState
import com.ilsecondodasinistra.proportion.core.ui.component.UnitPicker
import com.ilsecondodasinistra.proportion.core.transfer.PlainTextStrings
import com.ilsecondodasinistra.proportion.core.ui.RecipeSharing
import com.ilsecondodasinistra.proportion.core.ui.component.TagChipRow
import com.ilsecondodasinistra.proportion.core.ui.unitLabel
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
        onShowOriginal = viewModel::onShowOriginal,
        onShowVariant = viewModel::onShowVariant,
        tryConvert = viewModel::tryConvert,
        onDensityPromptConfirm = viewModel::onDensityPromptConfirm,
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
    onShowOriginal: () -> Unit = {},
    onShowVariant: (String) -> Unit = {},
    tryConvert: (String, MeasureUnit) -> ConversionResult? = { _, _ -> null },
    onDensityPromptConfirm: (String, Double?, Double?) -> Unit = { _, _, _ -> },
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
                        IconButton(
                            onClick = { onEdit(content.recipe.id) },
                            modifier = Modifier.testTag("detail_edit"),
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.recipe_detail_edit),
                            )
                        }
                        FavouriteAction(content.recipe.isFavourite, onFavouriteToggle)
                        OverflowMenu(
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
                onShowOriginal = onShowOriginal,
                onShowVariant = onShowVariant,
                tryConvert = tryConvert,
                onDensityPromptConfirm = onDensityPromptConfirm,
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
    onShareText: () -> Unit,
    onShareFile: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }, modifier = Modifier.testTag("detail_overflow")) {
        Icon(
            Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.recipe_detail_more_actions),
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
    onShowOriginal: () -> Unit,
    onShowVariant: (String) -> Unit,
    tryConvert: (String, MeasureUnit) -> ConversionResult?,
    onDensityPromptConfirm: (String, Double?, Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val recipe = content.recipe
    var conversionLineId by remember { mutableStateOf<String?>(null) }

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

        if (content.cookCount > 0) {
            Text(
                text = pluralStringResource(
                    R.plurals.detail_cook_count,
                    content.cookCount,
                    content.cookCount,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("detail_cook_count"),
            )
        }

        if (recipe.tags.isNotEmpty()) {
            TagChipRow(tags = recipe.tags, modifier = Modifier.padding(top = 12.dp))
        }

        content.showingVariant?.let { showing ->
            VariantBanner(
                label = showing.label,
                onShowOriginal = onShowOriginal,
                modifier = Modifier.padding(top = 12.dp),
            )
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
        if (recipe.ingredients.any { it.unit.isScalable && it.quantity != null }) {
            Text(
                text = stringResource(R.string.recipe_detail_ingredients_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        content.lines.forEach { line ->
            // Approximate lines ("q.b.") never scale and never convert: nothing to tap into.
            val rawLine = recipe.ingredients.firstOrNull { it.id == line.id }
            val convertible = rawLine != null && rawLine.unit.isScalable && rawLine.quantity != null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { if (convertible) it.clickable { conversionLineId = line.id } else it }
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onShowVariant(variant.id) }
                        .testTag("detail_variant_${variant.id}"),
                ) {
                    Text(text = variant.label, modifier = Modifier.padding(16.dp))
                }
            }
        }

        Column(modifier = Modifier.padding(bottom = 32.dp)) {}
    }

    conversionLineId?.let { lineId ->
        val line = content.lines.firstOrNull { it.id == lineId }
        val rawLine = recipe.ingredients.firstOrNull { it.id == lineId }
        if (line != null && rawLine != null) {
            ConversionSheet(
                lineName = line.name,
                originalUnit = rawLine.unit,
                tryConvert = { unit -> tryConvert(lineId, unit) },
                onDensityPromptConfirm = onDensityPromptConfirm,
                onDismiss = { conversionLineId = null },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversionSheet(
    lineName: String,
    originalUnit: MeasureUnit,
    tryConvert: (MeasureUnit) -> ConversionResult?,
    onDensityPromptConfirm: (String, Double?, Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    var targetUnit by remember { mutableStateOf(originalUnit) }
    // Reset with the unit, not cached across recompositions: once the user answers it (or a
    // reactive update to the ingredient supplies the data another way), the next result is
    // recomputed fresh and the dialog simply stops being offered.
    var promptDismissed by remember(targetUnit) { mutableStateOf(false) }
    val result = tryConvert(targetUnit)
    // The line's own unit always converts to itself trivially (no density needed), so this is
    // always a real value formatted the same way as the picked-unit result below — never the
    // possibly-scaled text shown in the ingredient list above, which would be a different number.
    val originalResult = tryConvert(originalUnit)

    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("conversion_sheet")) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.detail_conversion_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.detail_conversion_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Text(text = lineName, style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(
                    R.string.detail_conversion_original,
                    (originalResult as? ConversionResult.Converted)?.formatted?.text.orEmpty(),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
            UnitPicker(
                selected = targetUnit,
                unitName = { unit -> unitLabel(unit) },
                onSelect = { targetUnit = it },
            )
            when (result) {
                is ConversionResult.Converted -> Text(
                    text = result.formatted.text,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp).testTag("conversion_result"),
                )
                is ConversionResult.NeedsDensity -> if (!promptDismissed) {
                    DensityPromptDialog(
                        ingredientName = result.prompt.ingredientName,
                        requirement = result.prompt.requirement,
                        onDismiss = { promptDismissed = true },
                        onConfirm = { density, itemWeight ->
                            onDensityPromptConfirm(result.prompt.ingredientId, density, itemWeight)
                            promptDismissed = true
                        },
                    )
                }
                ConversionResult.Unsupported -> Text(
                    text = stringResource(R.string.detail_conversion_unsupported),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp),
                )
                null -> Unit
            }
            Column(modifier = Modifier.padding(bottom = 16.dp)) {}
        }
    }
}

/** A slim assist bar above the ingredients: "Showing: Per 6 · View original". */
@Composable
private fun VariantBanner(
    label: String,
    onShowOriginal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("detail_variant_banner"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.detail_showing_variant, label),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = " · ",
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = stringResource(R.string.detail_view_original),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable(onClick = onShowOriginal)
                .testTag("detail_view_original"),
        )
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
    val resources = LocalResources.current
    return PlainTextStrings(
        servings = { count -> resources.getString(R.string.plain_servings, count) },
        scaledFor = { value -> resources.getString(R.string.plain_scaled, value) },
        notPerPerson = stringResource(R.string.plain_not_per_person),
        ingredientsTitle = stringResource(R.string.plain_ingredients),
        methodTitle = stringResource(R.string.plain_method),
        attribution = stringResource(R.string.plain_attribution),
    )
}
