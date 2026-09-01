package com.ilsecondodasinistra.proportion.feature.cook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ilsecondodasinistra.proportion.core.ui.R as UiR
import com.ilsecondodasinistra.proportion.core.ui.component.TagChipRow
import com.ilsecondodasinistra.proportion.core.ui.component.WarningRow

/**
 * The recipe card with the new quantities: same shape as the detail screen, so following the method
 * feels identical to reading the original. The steps are the recipe's own, untouched.
 */
@Composable
fun ScaledCardBody(
    state: CookUiState,
    onBackToAdjust: () -> Unit,
    onSaveRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recipe = state.recipe ?: return

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .testTag("scaled_card"),
    ) {
        Text(
            text = state.servings?.let {
                stringResource(R.string.cook_achievable_card, it.format())
            } ?: stringResource(R.string.cook_factor_only, state.factor.format()),
            style = MaterialTheme.typography.titleMedium,
        )

        if (recipe.tags.isNotEmpty()) {
            TagChipRow(tags = recipe.tags, modifier = Modifier.padding(top = 10.dp))
        }

        state.ovenAdvisory?.let { advisory ->
            WarningRow(
                title = stringResource(UiR.string.warning_oven_title),
                text = stringResource(
                    UiR.string.warning_oven_message,
                    advisory.tinDiameterRatio.format(),
                ),
                modifier = Modifier.padding(top = 12.dp),
                testTag = "card_oven_advisory",
            )
        }

        Text(
            text = stringResource(R.string.cook_ingredients),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
        )

        state.lines.forEach { line ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("card_${line.lineId}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = line.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = line.scaledText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            HorizontalDivider()
        }

        if (recipe.steps.isNotEmpty()) {
            Text(
                text = stringResource(R.string.cook_steps),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
            )
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

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onBackToAdjust,
                modifier = Modifier.weight(1f).testTag("back_to_adjust"),
            ) {
                Text(stringResource(R.string.cook_show_adjust))
            }
            Button(
                onClick = onSaveRequested,
                modifier = Modifier.weight(1f).testTag("card_save_variant"),
            ) {
                Text(stringResource(R.string.cook_save_variant))
            }
        }
    }
}
