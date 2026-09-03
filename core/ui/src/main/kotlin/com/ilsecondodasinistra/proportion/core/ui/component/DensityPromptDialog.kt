package com.ilsecondodasinistra.proportion.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ilsecondodasinistra.proportion.core.domain.unit.DensityRequirement
import com.ilsecondodasinistra.proportion.core.ui.R

/**
 * Asked once per ingredient, the first time a conversion needs data [requirement] says is missing.
 * The answer is persisted on the ingredient's catalogue row, so this never asks twice for the same
 * one. Never shown for [DensityRequirement.NONE] or [DensityRequirement.UNSUPPORTED] — the caller
 * guards that.
 */
@Composable
fun DensityPromptDialog(
    ingredientName: String,
    requirement: DensityRequirement,
    onDismiss: () -> Unit,
    onConfirm: (densityGramsPerMl: Double?, itemWeightGrams: Double?) -> Unit,
) {
    val needsDensity = requirement == DensityRequirement.DENSITY || requirement == DensityRequirement.BOTH
    val needsItemWeight = requirement == DensityRequirement.ITEM_WEIGHT || requirement == DensityRequirement.BOTH

    var densityInput by remember { mutableStateOf("") }
    var itemWeightInput by remember { mutableStateOf("") }

    val density = densityInput.parsePositiveAmount()
    val itemWeight = itemWeightInput.parsePositiveAmount()
    val canConfirm = (!needsDensity || density != null) && (!needsItemWeight || itemWeight != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.density_prompt_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.density_prompt_body, ingredientName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (needsDensity) {
                    OutlinedTextField(
                        value = densityInput,
                        onValueChange = { densityInput = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        label = { Text(stringResource(R.string.density_prompt_density_label)) },
                        modifier = Modifier.padding(top = 8.dp).testTag("density_prompt_density_field"),
                    )
                }
                if (needsItemWeight) {
                    OutlinedTextField(
                        value = itemWeightInput,
                        onValueChange = { itemWeightInput = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        label = { Text(stringResource(R.string.density_prompt_item_weight_label)) },
                        modifier = Modifier.padding(top = 8.dp).testTag("density_prompt_item_weight_field"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(density, itemWeight) },
                enabled = canConfirm,
                modifier = Modifier.testTag("density_prompt_confirm"),
            ) {
                Text(stringResource(R.string.density_prompt_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.density_prompt_cancel))
            }
        },
        modifier = Modifier.testTag("density_prompt_dialog"),
    )
}

/** Accepts both decimal separators: an Italian keyboard offers the comma. */
private fun String.parsePositiveAmount(): Double? =
    trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
