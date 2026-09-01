package com.ilsecondodasinistra.proportion.core.ui.component

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ilsecondodasinistra.proportion.core.designsystem.theme.AmberContainerDark
import com.ilsecondodasinistra.proportion.core.designsystem.theme.AmberContainerLight
import com.ilsecondodasinistra.proportion.core.designsystem.theme.AmberWarningDark
import com.ilsecondodasinistra.proportion.core.designsystem.theme.AmberWarningLight
import com.ilsecondodasinistra.proportion.core.ui.R

/**
 * A non-blocking advisory: an amber row that says what is impractical and, where there is one,
 * offers the single action that fixes it.
 *
 * Amber is reserved for exactly this. It is never used decoratively, so its presence always means
 * the same thing.
 */
@Composable
fun WarningRow(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    actions: List<WarningAction> = emptyList(),
    testTag: String = "warning_row",
) {
    val dark = isSystemInDarkTheme()
    val container = if (dark) AmberContainerDark else AmberContainerLight
    val content = if (dark) AmberWarningDark else AmberWarningLight

    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().testTag(testTag),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = stringResource(R.string.warning_label),
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                if (title != null) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall)
                }
                Text(text = text, style = MaterialTheme.typography.bodyMedium)

                if (actions.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        actions.forEach { action ->
                            AssistChip(
                                onClick = action.onClick,
                                label = { Text(action.label) },
                                modifier = Modifier.testTag(action.testTag),
                            )
                        }
                    }
                }
            }
        }
    }
}

data class WarningAction(
    val label: String,
    val testTag: String,
    val onClick: () -> Unit,
)
