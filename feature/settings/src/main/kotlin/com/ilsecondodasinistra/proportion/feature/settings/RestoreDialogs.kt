package com.ilsecondodasinistra.proportion.feature.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.ilsecondodasinistra.proportion.core.transfer.DecodeFailure

/**
 * The restore conversation: what the file holds, then what to do about it, and — only for the
 * destructive choice — one more confirmation.
 */
@Composable
fun RestoreDialogs(
    step: RestoreStep,
    onMerge: () -> Unit,
    onReplaceRequested: () -> Unit,
    onReplaceConfirmed: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (step) {
        RestoreStep.Idle -> Unit

        is RestoreStep.Confirming -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.settings_restore_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_restore_counts,
                        step.total,
                        step.alreadyPresent,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onMerge, modifier = Modifier.testTag("restore_merge")) {
                    Text(stringResource(R.string.settings_restore_merge))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onReplaceRequested,
                    modifier = Modifier.testTag("restore_replace"),
                ) {
                    Text(stringResource(R.string.settings_restore_replace))
                }
            },
            modifier = Modifier.testTag("restore_dialog"),
        )

        is RestoreStep.ConfirmingReplace -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.settings_replace_confirm_title)) },
            text = { Text(stringResource(R.string.settings_replace_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = onReplaceConfirmed,
                    modifier = Modifier.testTag("replace_confirm"),
                ) {
                    Text(stringResource(R.string.settings_replace_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.settings_restore_cancel))
                }
            },
            modifier = Modifier.testTag("replace_confirm_dialog"),
        )

        is RestoreStep.Done -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.settings_restore_title)) },
            text = {
                Text(
                    if (step.replaced) {
                        stringResource(R.string.settings_restore_replaced, step.added)
                    } else {
                        stringResource(R.string.settings_restore_done, step.added, step.skipped)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("restore_done_ok")) {
                    Text("OK")
                }
            },
            modifier = Modifier.testTag("restore_done_dialog"),
        )

        is RestoreStep.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.settings_restore_title)) },
            text = { Text(step.reason.message()) },
            confirmButton = {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("restore_failed_ok")) {
                    Text("OK")
                }
            },
            modifier = Modifier.testTag("restore_failed_dialog"),
        )
    }
}

@Composable
private fun DecodeFailure.message(): String = when (this) {
    DecodeFailure.NotProportionFile -> stringResource(R.string.settings_error_not_proportion)
    is DecodeFailure.FutureVersion -> stringResource(R.string.settings_error_future, found)
    is DecodeFailure.Malformed -> stringResource(R.string.settings_error_malformed)
}
