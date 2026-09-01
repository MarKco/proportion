package com.ilsecondodasinistra.proportion.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ilsecondodasinistra.proportion.core.domain.unit.QuantityFormatter
import com.ilsecondodasinistra.proportion.core.model.RecipeIngredient

/**
 * One ingredient line: name on the left, quantity on the right, note underneath.
 *
 * The quantity is rendered through [QuantityFormatter] rather than formatted here, so the list and
 * the scaled card always agree on how a number looks.
 */
@Composable
fun IngredientLineRow(
    line: RecipeIngredient,
    formatter: QuantityFormatter,
    modifier: Modifier = Modifier,
) {
    val quantityText = when (val qty = line.quantity) {
        null -> line.displayText ?: formatter.format(0.0, line.unit).text
        else -> line.displayText ?: formatter.format(qty, line.unit).text
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .testTag("ingredient_row_${line.ingredient.name}"),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = line.ingredient.name, style = MaterialTheme.typography.bodyLarge)
            line.note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = quantityText,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
