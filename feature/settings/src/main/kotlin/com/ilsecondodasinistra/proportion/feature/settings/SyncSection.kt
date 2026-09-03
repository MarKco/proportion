package com.ilsecondodasinistra.proportion.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role

/** The toggle, folder picker, status and error banner for folder sync (phase 10). */
@Composable
internal fun SyncSection(
    state: SettingsUiState,
    onSyncEnabledChange: (Boolean) -> Unit,
    onChooseFolderClick: () -> Unit,
    onSyncNowClick: () -> Unit,
    onShareLogClick: () -> Unit,
) {
    SectionTitle(stringResource(R.string.settings_sync))

    // Same TalkBack-driven pattern as the other switches on this screen: the row carries the
    // toggleable semantics, the Switch itself is display-only.
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_sync_enabled)) },
        supportingContent = { Text(stringResource(R.string.settings_sync_enabled_hint)) },
        trailingContent = { Switch(checked = state.syncEnabled, onCheckedChange = null) },
        modifier = Modifier
            .toggleable(
                value = state.syncEnabled,
                onValueChange = onSyncEnabledChange,
                role = Role.Switch,
            )
            .testTag("sync_enabled_switch"),
    )

    if (!state.syncEnabled) return

    ListItem(
        headlineContent = {
            Text(
                if (state.syncFolderUri != null) {
                    stringResource(R.string.settings_sync_folder_chosen)
                } else {
                    stringResource(R.string.settings_sync_no_folder)
                },
            )
        },
        leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
        modifier = Modifier
            .clickable(onClick = onChooseFolderClick)
            .testTag("sync_choose_folder_item"),
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_sync_now)) },
        supportingContent = {
            val result = state.syncLastResult
            when {
                state.syncInProgress -> Text(stringResource(R.string.settings_sync_in_progress))
                result != null -> Text(
                    stringResource(
                        R.string.settings_sync_last_result,
                        result.exported,
                        result.recipesImported,
                        result.recipesDeleted,
                        result.catalogueImported,
                    ),
                )
            }
        },
        leadingContent = { Icon(Icons.Filled.Sync, contentDescription = null) },
        modifier = Modifier
            .clickable(enabled = !state.syncInProgress, onClick = onSyncNowClick)
            .testTag("sync_now_item"),
    )

    val error = state.syncLastError ?: return
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_sync_error_banner, error.message)) },
        trailingContent = {
            TextButton(onClick = onShareLogClick) { Text(stringResource(R.string.settings_sync_share_log)) }
        },
        modifier = Modifier.testTag("sync_error_banner"),
    )
}
