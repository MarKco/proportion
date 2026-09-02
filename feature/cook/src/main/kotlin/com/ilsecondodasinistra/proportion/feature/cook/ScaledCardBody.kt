package com.ilsecondodasinistra.proportion.feature.cook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ilsecondodasinistra.proportion.core.designsystem.theme.ProPortionMotion
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
    onStartCooking: () -> Unit,
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

        val ovenAdvisory = state.ovenAdvisory
        val ovenAdvisoryTitle = stringResource(UiR.string.warning_oven_title)
        val ovenAdvisoryText = ovenAdvisory?.let {
            stringResource(UiR.string.warning_oven_message, it.tinDiameterRatio.format())
        }
        var lastOvenAdvisoryText by remember { mutableStateOf(ovenAdvisoryText.orEmpty()) }
        if (ovenAdvisoryText != null) {
            lastOvenAdvisoryText = ovenAdvisoryText
        }
        AnimatedVisibility(
            visible = ovenAdvisory != null,
            enter = fadeIn(tween(ProPortionMotion.BADGE_ENTER_MILLIS)) +
                expandVertically(tween(ProPortionMotion.BADGE_ENTER_MILLIS)),
            exit = fadeOut(tween(ProPortionMotion.BADGE_ENTER_MILLIS)) +
                shrinkVertically(tween(ProPortionMotion.BADGE_ENTER_MILLIS)),
        ) {
            WarningRow(
                title = ovenAdvisoryTitle,
                text = lastOvenAdvisoryText,
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
            OutlinedButton(
                onClick = onSaveRequested,
                modifier = Modifier.weight(1f).testTag("card_save_variant"),
            ) {
                Text(stringResource(R.string.cook_save_variant))
            }
        }

        Button(
            onClick = onStartCooking,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp).testTag("start_cooking_button"),
        ) {
            Text(stringResource(R.string.cooking_mode_start))
        }
    }
}
