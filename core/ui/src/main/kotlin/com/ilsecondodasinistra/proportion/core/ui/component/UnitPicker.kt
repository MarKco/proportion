package com.ilsecondodasinistra.proportion.core.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.UnitCategory
import com.ilsecondodasinistra.proportion.core.ui.unitCategoryLabelRes
import com.ilsecondodasinistra.proportion.core.ui.unitsByCategory

/**
 * Units grouped by category, because picking the right kind of unit is the decision that makes the
 * rest of the app work: discrete units warn when they cannot be split, approximate ones never scale.
 */
@Composable
fun UnitPicker(
    selected: MeasureUnit?,
    unitName: @Composable (MeasureUnit) -> String,
    onSelect: (MeasureUnit) -> Unit,
    emptyLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { expanded = true },
        modifier = modifier.testTag("unit_picker"),
    ) {
        // No unit chosen yet: the button asks the question instead of answering it for the user.
        Text(if (selected != null) unitName(selected) else emptyLabel.orEmpty())
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        UnitCategory.entries.forEach { category ->
            Text(
                text = stringResource(unitCategoryLabelRes(category)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            unitsByCategory[category].orEmpty().forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unitName(unit)) },
                    onClick = {
                        onSelect(unit)
                        expanded = false
                    },
                    modifier = Modifier.testTag("unit_option_${unit.name}"),
                )
            }
        }
    }
}
