package com.ilsecondodasinistra.proportion.feature.shopping

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilsecondodasinistra.proportion.core.transfer.ShoppingListStrings
import com.ilsecondodasinistra.proportion.core.ui.RecipeSharing
import com.ilsecondodasinistra.proportion.core.ui.component.EmptyState

@Composable
fun ShoppingListRoute(viewModel: ShoppingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.shopping_share_chooser)
    val listStrings = shoppingListStrings()

    ShoppingScreen(
        state = state,
        onCheckedChange = viewModel::onCheckedChange,
        onClearChecked = viewModel::onClearChecked,
        onClearAllRequested = viewModel::onClearAllRequested,
        onClearAllDismissed = viewModel::onClearAllDismissed,
        onClearAllConfirmed = viewModel::onClearAllConfirmed,
        onShare = { RecipeSharing.shareText(context, viewModel.shareText(listStrings), chooserTitle) },
    )
}

@Composable
private fun shoppingListStrings(): ShoppingListStrings = ShoppingListStrings(
    title = stringResource(R.string.shopping_list_title),
    checkedTitle = stringResource(R.string.shopping_list_checked_title),
    attribution = stringResource(R.string.shopping_attribution),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    state: ShoppingUiState,
    onCheckedChange: (String, Boolean) -> Unit,
    onClearChecked: () -> Unit,
    onClearAllRequested: () -> Unit,
    onClearAllDismissed: () -> Unit,
    onClearAllConfirmed: () -> Unit,
    onShare: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.testTag("shopping_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shopping_title)) },
                actions = {
                    OverflowMenu(
                        clearCheckedEnabled = state.checkedCount > 0,
                        onShare = onShare,
                        onClearChecked = onClearChecked,
                        onClearAllRequested = onClearAllRequested,
                    )
                },
            )
        },
    ) { padding ->
        if (state.isEmpty) {
            EmptyState(
                title = stringResource(R.string.shopping_empty_title),
                message = stringResource(R.string.shopping_empty_body),
                modifier = Modifier.padding(padding),
                testTag = "shopping_empty_state",
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("shopping_list"),
            ) {
                items(state.items, key = { it.id }) { row ->
                    ShoppingRowItem(
                        row = row,
                        onCheckedChange = { checked -> onCheckedChange(row.id, checked) },
                    )
                }
            }
        }
    }

    if (state.confirmClearAll) {
        AlertDialog(
            onDismissRequest = onClearAllDismissed,
            title = { Text(stringResource(R.string.shopping_clear_all_confirm_title)) },
            text = { Text(stringResource(R.string.shopping_clear_all_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = onClearAllConfirmed,
                    modifier = Modifier.testTag("clear_all_confirm"),
                ) {
                    Text(stringResource(R.string.shopping_clear_all_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onClearAllDismissed,
                    modifier = Modifier.testTag("clear_all_cancel"),
                ) {
                    Text(stringResource(R.string.shopping_clear_all_cancel))
                }
            },
            modifier = Modifier.testTag("clear_all_dialog"),
        )
    }
}

@Composable
private fun ShoppingRowItem(row: ShoppingRow, onCheckedChange: (Boolean) -> Unit) {
    val decoration = if (row.isChecked) TextDecoration.LineThrough else TextDecoration.None
    val contentColor = if (row.isChecked) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = row.isChecked, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("shopping_row_${row.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = row.isChecked,
            onCheckedChange = null,
            modifier = Modifier.testTag("shopping_checkbox_${row.id}"),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = row.name, textDecoration = decoration, color = contentColor)
            if (row.sourceCount > 1) {
                Text(
                    text = pluralStringResource(
                        R.plurals.shopping_from_recipes,
                        row.sourceCount,
                        row.sourceCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (row.amountText.isNotEmpty()) {
            Text(text = row.amountText, textDecoration = decoration, color = contentColor)
        }
    }
}

@Composable
private fun OverflowMenu(
    clearCheckedEnabled: Boolean,
    onShare: () -> Unit,
    onClearChecked: () -> Unit,
    onClearAllRequested: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }, modifier = Modifier.testTag("shopping_overflow")) {
        Icon(
            Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.shopping_more_actions),
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.shopping_share)) },
            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
            onClick = {
                expanded = false
                onShare()
            },
            modifier = Modifier.testTag("shopping_share_item"),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.shopping_clear_checked)) },
            leadingIcon = { Icon(Icons.Filled.PlaylistRemove, contentDescription = null) },
            enabled = clearCheckedEnabled,
            onClick = {
                expanded = false
                onClearChecked()
            },
            modifier = Modifier.testTag("shopping_clear_checked_item"),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.shopping_clear_all)) },
            leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
            onClick = {
                expanded = false
                onClearAllRequested()
            },
            modifier = Modifier.testTag("shopping_clear_all_item"),
        )
    }
}
