package com.ilsecondodasinistra.proportion.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ilsecondodasinistra.proportion.core.designsystem.component.DonutChart
import com.ilsecondodasinistra.proportion.core.designsystem.component.DonutSlice
import com.ilsecondodasinistra.proportion.core.designsystem.theme.ProPortionChartColors
import com.ilsecondodasinistra.proportion.core.ui.builtInTagLabelRes

@Composable
fun NumbersCard(
    recipeCount: Int,
    totalCooks: Int,
    favouriteCount: Int,
    donutSlices: List<DonutSliceUi>,
    uncategorisedCount: Int,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier.fillMaxWidth().testTag("numbers_card"),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.home_numbers_title), style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DonutChart(
                    slices = donutSlices.map { it.toDonutSlice() },
                    centreLabel = recipeCount.toString(),
                    diameter = 120.dp,
                    thickness = 16.dp,
                )
                Column(
                    modifier = Modifier.testTag("course_legend"),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    donutSlices.forEach { slice -> LegendRow(slice) }
                    if (uncategorisedCount > 0) {
                        Text(
                            text = stringResource(R.string.home_uncategorised, uncategorisedCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = pluralStringResource(R.plurals.home_recipes_count, recipeCount, recipeCount),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("recipe_count"),
                )
                Text(
                    text = pluralStringResource(R.plurals.home_cooks_count, totalCooks, totalCooks),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("total_cooks"),
                )
                Text(
                    text = pluralStringResource(R.plurals.home_favourites_count, favouriteCount, favouriteCount),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("favourite_count"),
                )
            }
        }
    }
}

@Composable
private fun LegendRow(slice: DonutSliceUi) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            modifier = Modifier
                .size(10.dp)
                .background(color = slice.color(), shape = CircleShape),
        )
        Text(
            text = "${slice.label()} · ${slice.count}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun DonutSliceUi.label(): String {
    val res = builtInTagLabelRes(tagKey)
    return if (res != null) stringResource(res) else tagKey
}

private fun DonutSliceUi.color() = ProPortionChartColors[colorIndex.mod(ProPortionChartColors.size)]

private fun DonutSliceUi.toDonutSlice() = DonutSlice(value = count, color = color(), label = tagKey)

@Composable
fun ContinueCookingCard(
    continueCooking: ContinueCooking,
    onCook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        modifier = modifier.fillMaxWidth().testTag("continue_cooking_card"),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.home_continue_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = continueCooking.title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp).testTag("continue_cooking_title"),
            )
            continueCooking.variantLabel?.let { label ->
                Text(
                    text = stringResource(R.string.home_continue_showing, label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = 0.75f),
                )
            }
            Button(
                onClick = { onCook(continueCooking.recipeId) },
                modifier = Modifier.padding(top = 12.dp).testTag("continue_cooking_button"),
            ) {
                Text(stringResource(R.string.home_cook_action))
            }
        }
    }
}

@Composable
fun MostCookedCard(
    mostCooked: List<RecipeCardItem>,
    favourites: List<RecipeCardItem>,
    onRecipeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = modifier.fillMaxWidth().testTag("most_cooked_card"),
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.home_most_cooked_title), style = MaterialTheme.typography.titleSmall)
                mostCooked.forEach { item -> RecipeLine(item, onRecipeClick) }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.home_favourites_title), style = MaterialTheme.typography.titleSmall)
                favourites.forEach { item -> RecipeLine(item, onRecipeClick) }
            }
        }
    }
}

@Composable
private fun RecipeLine(item: RecipeCardItem, onClick: (String) -> Unit) {
    Text(
        text = item.title,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(item.recipeId) }
            .padding(vertical = 6.dp)
            .testTag("recipe_line_${item.recipeId}"),
    )
}

@Composable
fun SuggestionCard(
    suggestion: RecipeCardItem?,
    suggestionUnavailable: Boolean,
    tags: List<TagChipItem>,
    selectedTagId: String?,
    onTagChange: (String?) -> Unit,
    onReshuffle: () -> Unit,
    onRecipeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gradient = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer),
    )

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = modifier.fillMaxWidth().testTag("suggestion_card"),
    ) {
        Column(modifier = Modifier.background(gradient).fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.home_suggestion_title), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onReshuffle, modifier = Modifier.testTag("reshuffle_button")) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = stringResource(R.string.home_suggestion_reshuffle),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedTagId == null,
                    onClick = { onTagChange(null) },
                    label = { Text(stringResource(R.string.home_suggestion_all_tags)) },
                    modifier = Modifier.testTag("suggestion_tag_all"),
                )
                tags.forEach { tag ->
                    FilterChip(
                        selected = selectedTagId == tag.id,
                        onClick = { onTagChange(tag.id) },
                        label = { Text(tag.label()) },
                        modifier = Modifier.testTag("suggestion_tag_${tag.id}"),
                    )
                }
            }

            AnimatedContent(targetState = suggestion, label = "suggestion") { current ->
                when {
                    current != null -> Text(
                        text = current.title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRecipeClick(current.recipeId) }
                            .padding(top = 16.dp)
                            .testTag("suggestion_title"),
                    )

                    suggestionUnavailable -> Text(
                        text = stringResource(R.string.home_suggestion_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = 0.75f),
                        modifier = Modifier.padding(top = 16.dp).testTag("suggestion_none"),
                    )

                    else -> Spacer(modifier = Modifier.height(1.dp))
                }
            }
        }
    }
}

@Composable
private fun TagChipItem.label(): String {
    val res = key?.let { builtInTagLabelRes(it) }
    return if (res != null) stringResource(res) else name.orEmpty()
}
