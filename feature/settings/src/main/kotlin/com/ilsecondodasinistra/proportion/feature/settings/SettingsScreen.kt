package com.ilsecondodasinistra.proportion.feature.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilsecondodasinistra.proportion.core.model.AppTheme
import com.ilsecondodasinistra.proportion.core.model.SyncLogEntry
import com.ilsecondodasinistra.proportion.core.model.ThemeMode
import com.ilsecondodasinistra.proportion.core.transfer.ImportMode
import com.ilsecondodasinistra.proportion.core.transfer.ProportionFile
import com.ilsecondodasinistra.proportion.core.ui.RecipeSharing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val backupSavedMessage = stringResource(R.string.settings_backup_done)

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ProportionFile.MIME_TYPE),
    ) { uri ->
        if (uri != null) {
            viewModel.onBackup { text ->
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                }
            }
        }
    }

    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.onRestoreFileChosen(context.readText(uri))
        }
    }

    val chooseSyncFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.onSyncFolderChosen(uri.toString())
        }
    }

    val syncShareLogTitle = stringResource(R.string.settings_sync_share_log_title)

    LaunchedEffect(state.backupSaved) {
        if (state.backupSaved) {
            snackbarHostState.showSnackbar(backupSavedMessage)
            viewModel.onBackupMessageShown()
        }
    }

    SettingsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onThemeChange = viewModel::onThemeChange,
        onDynamicColourChange = viewModel::onDynamicColourChange,
        onAppThemeChange = viewModel::onAppThemeChange,
        onLanguageChange = { language ->
            viewModel.onLanguageChange(language)
            // The running screens are already inflated in the old language; AppCompat's own
            // recreation hook targets AppCompatActivity, which MainActivity isn't, so this asks
            // explicitly rather than assuming it happens on its own.
            (context as? Activity)?.recreate()
        },
        onBackupClick = { createBackup.launch(defaultBackupName()) },
        onRestoreClick = { openBackup.launch(arrayOf("*/*")) },
        onMerge = { viewModel.onRestoreConfirmed(ImportMode.MERGE) },
        onReplaceRequested = viewModel::onReplaceRequested,
        onReplaceConfirmed = { viewModel.onRestoreConfirmed(ImportMode.REPLACE_ALL) },
        onDismissRestore = viewModel::onRestoreDismissed,
        onSyncEnabledChange = viewModel::onSyncEnabledChange,
        onChooseFolderClick = { chooseSyncFolder.launch(null) },
        onSyncNowClick = viewModel::onSyncNowClick,
        onShareLogClick = {
            RecipeSharing.shareText(context, formatSyncLog(state.syncLog), syncShareLogTitle)
        },
    )
}

private fun formatSyncLog(log: List<SyncLogEntry>): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return log.joinToString("\n") { entry ->
        val tag = if (entry.isError) "[ERRORE]" else "[OK]"
        "${formatter.format(Date(entry.timestamp))} $tag ${entry.message}"
    }
}

/** Read on IO: a backup can be large, and this runs from a picker callback on the main thread. */
private fun Context.readText(uri: android.net.Uri): String =
    contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }.orEmpty()

private fun defaultBackupName(): String = "proportion-backup.${ProportionFile.EXTENSION}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onThemeChange: (ThemeMode) -> Unit,
    onDynamicColourChange: (Boolean) -> Unit,
    onAppThemeChange: (AppTheme) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onMerge: () -> Unit,
    onReplaceRequested: () -> Unit,
    onReplaceConfirmed: () -> Unit,
    onDismissRestore: () -> Unit,
    onSyncEnabledChange: (Boolean) -> Unit,
    onChooseFolderClick: () -> Unit,
    onSyncNowClick: () -> Unit,
    onShareLogClick: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.testTag("settings_screen"),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle(stringResource(R.string.settings_appearance))

            Column(modifier = Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    // On-device TalkBack testing showed the bare RadioButton announced with no
                    // label: the row itself carries the selectable semantics so the adjacent
                    // text is merged into what gets spoken, and the RadioButton's own click
                    // handler is dropped so the two don't fight over the tap.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.themeMode == mode,
                                onClick = { onThemeChange(mode) },
                                role = Role.RadioButton,
                            )
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag("theme_${mode.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = state.themeMode == mode,
                            onClick = null,
                        )
                        Text(
                            text = stringResource(mode.labelRes()),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            // On-device TalkBack testing showed the bare Switch announced with no label: the
            // row itself carries the toggleable semantics so "Dynamic colour" is merged into
            // what gets spoken, and the Switch's own change handler is dropped so the two
            // don't fight over the tap.
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_dynamic_colour)) },
                supportingContent = { Text(stringResource(R.string.settings_dynamic_colour_off)) },
                trailingContent = {
                    Switch(
                        checked = state.useDynamicColour,
                        onCheckedChange = null,
                    )
                },
                modifier = Modifier
                    .toggleable(
                        value = state.useDynamicColour,
                        onValueChange = onDynamicColourChange,
                        role = Role.Switch,
                    )
                    .testTag("dynamic_colour_switch"),
            )

            Text(
                text = stringResource(R.string.settings_app_theme),
                style = MaterialTheme.typography.labelLarge,
                color = if (state.useDynamicColour) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
            )

            Column(modifier = Modifier.selectableGroup()) {
                AppTheme.entries.forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.appTheme == theme,
                                enabled = !state.useDynamicColour,
                                onClick = { onAppThemeChange(theme) },
                                role = Role.RadioButton,
                            )
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .alpha(if (state.useDynamicColour) 0.38f else 1f)
                            .testTag("app_theme_${theme.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = state.appTheme == theme,
                            enabled = !state.useDynamicColour,
                            onClick = null,
                        )
                        Text(
                            text = stringResource(theme.labelRes()),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_language))

            Column(modifier = Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.language == language,
                                onClick = { onLanguageChange(language) },
                                role = Role.RadioButton,
                            )
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag("language_${language.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = state.language == language,
                            onClick = null,
                        )
                        Text(
                            text = stringResource(language.labelRes()),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_data))

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_backup)) },
                supportingContent = { Text(stringResource(R.string.settings_backup_hint)) },
                leadingContent = { Icon(Icons.Filled.Download, contentDescription = null) },
                modifier = Modifier
                    .clickable(onClick = onBackupClick)
                    .testTag("backup_item"),
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_restore)) },
                supportingContent = { Text(stringResource(R.string.settings_restore_hint)) },
                leadingContent = { Icon(Icons.Filled.Upload, contentDescription = null) },
                modifier = Modifier
                    .clickable(onClick = onRestoreClick)
                    .testTag("restore_item"),
            )

            HorizontalDivider()
            SyncSection(
                state = state,
                onSyncEnabledChange = onSyncEnabledChange,
                onChooseFolderClick = onChooseFolderClick,
                onSyncNowClick = onSyncNowClick,
                onShareLogClick = onShareLogClick,
            )

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about_author)) },
                supportingContent = { Text(stringResource(R.string.settings_version, appVersion())) },
            )
        }
    }

    RestoreDialogs(
        step = state.restore,
        onMerge = onMerge,
        onReplaceRequested = onReplaceRequested,
        onReplaceConfirmed = onReplaceConfirmed,
        onDismiss = onDismissRestore,
    )
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

private fun AppTheme.labelRes(): Int = when (this) {
    AppTheme.PASTEL -> R.string.settings_app_theme_pastel
    AppTheme.VIVID -> R.string.settings_app_theme_vivid
    AppTheme.PLAYFUL -> R.string.settings_app_theme_playful
    AppTheme.HIGH_CONTRAST -> R.string.settings_app_theme_high_contrast
}

private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.settings_language_system
    AppLanguage.ITALIAN -> R.string.settings_language_italian
    AppLanguage.ENGLISH -> R.string.settings_language_english
}
