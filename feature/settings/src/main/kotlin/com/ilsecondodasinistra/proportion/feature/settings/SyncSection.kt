package com.ilsecondodasinistra.proportion.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Presets rather than free entry: keeps the picker a simple radio-row list, no input validation. */
private val SYNC_INTERVAL_PRESET_HOURS = listOf(1, 2, 4, 6, 8, 12, 24)

/** The toggle, folder picker, status and error banner for folder sync (phase 10). */
@Composable
internal fun SyncSection(
    state: SettingsUiState,
    onSyncEnabledChange: (Boolean) -> Unit,
    onChooseFolderClick: () -> Unit,
    onSyncIntervalChange: (Int) -> Unit,
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

    Text(
        text = stringResource(R.string.settings_sync_interval),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
    Column(modifier = Modifier.selectableGroup()) {
        SYNC_INTERVAL_PRESET_HOURS.forEach { hours ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = state.syncIntervalHours == hours,
                        onClick = { onSyncIntervalChange(hours) },
                        role = Role.RadioButton,
                    )
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("sync_interval_${hours}h"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = state.syncIntervalHours == hours, onClick = null)
                Text(
                    text = pluralStringResource(R.plurals.settings_sync_interval_hours, hours, hours),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }

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

    // Sourced from the persisted log (see SettingsUiState.lastSyncLogEntry), so this also shows a
    // run the background job did while this screen wasn't open — unlike syncLastResult above.
    state.lastSyncLogEntry?.let { entry ->
        val timestamp = remember(entry.timestamp) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(entry.timestamp))
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_sync_last_run, timestamp)) },
            supportingContent = { Text(entry.message) },
            modifier = Modifier.testTag("sync_last_run_item"),
        )
    }

    val error = state.syncLastError ?: return
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_sync_error_banner, error.message)) },
        trailingContent = {
            TextButton(onClick = onShareLogClick) { Text(stringResource(R.string.settings_sync_share_log)) }
        },
        modifier = Modifier.testTag("sync_error_banner"),
    )
}
