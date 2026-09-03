package com.ilsecondodasinistra.proportion.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ilsecondodasinistra.proportion.core.model.Tag
import com.ilsecondodasinistra.proportion.core.ui.tagAccentColor
import com.ilsecondodasinistra.proportion.core.ui.tagLabel

/**
 * Read-only chips, as shown on a recipe card.
 *
 * Built from a [Surface] rather than a disabled chip: a greyed-out chip reads as "you cannot use
 * this", when the truth is simply that it is a label.
 */
@Composable
fun TagChipRow(tags: List<Tag>, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(tags, key = { it.id }) { tag ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(color = tagAccentColor(tag), shape = CircleShape),
                    )
                    Text(
                        text = tagLabel(tag),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * Selectable chips for the list filter, on one scrollable line: nine tags stacked into three rows
 * push the results themselves off the screen.
 */
@Composable
fun SelectableTagChipRow(
    tags: List<Tag>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.testTag("tag_filter_row"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(end = 8.dp),
    ) {
        items(tags, key = { it.id }) { tag ->
            val selected = tag.id in selectedIds
            FilterChip(
                selected = selected,
                onClick = { onToggle(tag.id) },
                label = { Text(tagLabel(tag)) },
                leadingIcon = if (selected) {
                    { Icon(Icons.Filled.Check, contentDescription = null) }
                } else {
                    {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(color = tagAccentColor(tag), shape = CircleShape),
                        )
                    }
                },
                modifier = Modifier.testTag("tag_chip_${tag.key ?: tag.name}"),
            )
        }
    }
}
