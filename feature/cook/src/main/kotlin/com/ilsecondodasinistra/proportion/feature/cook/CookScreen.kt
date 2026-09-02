package com.ilsecondodasinistra.proportion.feature.cook

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilsecondodasinistra.proportion.core.designsystem.theme.ProPortionMotion
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaleConstraint
import com.ilsecondodasinistra.proportion.core.ui.R as UiR
import com.ilsecondodasinistra.proportion.core.ui.component.LoadingState
import com.ilsecondodasinistra.proportion.core.ui.component.WarningAction
import com.ilsecondodasinistra.proportion.core.ui.component.WarningRow
import java.util.Locale

@Composable
fun CookRoute(
    onBack: () -> Unit,
    onCookingMode: (String, ScaleConstraint?) -> Unit,
    viewModel: CookViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val shoppingMessage = state.shoppingMessage
    val nothingToAddText = stringResource(R.string.cook_nothing_to_add)
    val addedText = (shoppingMessage as? ShoppingMessage.Added)?.let {
        pluralStringResource(R.plurals.cook_added_to_shopping, it.count, it.count)
    }

    LaunchedEffect(shoppingMessage) {
        if (shoppingMessage != null) {
            snackbarHostState.showSnackbar(
                when (shoppingMessage) {
                    is ShoppingMessage.Added -> addedText.orEmpty()
                    ShoppingMessage.NothingToAdd -> nothingToAddText
                },
            )
            viewModel.onShoppingMessageShown()
        }
    }

    CookScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onModeChange = viewModel::onModeChange,
        onServingsChange = viewModel::onServingsChange,
        onFactorChange = viewModel::onFactorChange,
        onIngredientSelected = viewModel::onIngredientSelected,
        onIngredientQuantityChange = viewModel::onIngredientQuantityChange,
        onPantryAmountChange = viewModel::onPantryAmountChange,
        onSnapAccept = { viewModel.onSnapAccept(it) },
        onShowCard = viewModel::onShowCard,
        onSaveRequested = viewModel::onSaveVariantRequested,
        onSaveVariant = viewModel::onSaveVariant,
        onAddToShoppingList = viewModel::onAddToShoppingList,
        onStartCooking = { state.recipe?.let { onCookingMode(it.id, state.currentConstraint) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookScreen(
    state: CookUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onModeChange: (CookMode) -> Unit,
    onServingsChange: (Int) -> Unit,
    onFactorChange: (String) -> Unit,
    onIngredientSelected: (String) -> Unit,
    onIngredientQuantityChange: (String) -> Unit,
    onPantryAmountChange: (String, String) -> Unit,
    onSnapAccept: (com.ilsecondodasinistra.proportion.core.domain.scale.SnapOption) -> Unit,
    onShowCard: (Boolean) -> Unit,
    onSaveRequested: (Boolean) -> Unit,
    onSaveVariant: (String, Boolean) -> Unit,
    onAddToShoppingList: () -> Unit,
    onStartCooking: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.testTag("cook_screen"),
        topBar = {
            TopAppBar(
                title = { Text(state.recipe?.title ?: stringResource(R.string.cook_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("cook_back")) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cook_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(modifier = Modifier.padding(padding))

            state.showCard -> ScaledCardBody(
                state = state,
                onBackToAdjust = { onShowCard(false) },
                onSaveRequested = { onSaveRequested(true) },
                onStartCooking = onStartCooking,
                modifier = Modifier.padding(padding),
            )

            else -> AdjustBody(
                state = state,
                onModeChange = onModeChange,
                onServingsChange = onServingsChange,
                onFactorChange = onFactorChange,
                onIngredientSelected = onIngredientSelected,
                onIngredientQuantityChange = onIngredientQuantityChange,
                onPantryAmountChange = onPantryAmountChange,
                onSnapAccept = onSnapAccept,
                onShowCard = { onShowCard(true) },
                onSaveRequested = { onSaveRequested(true) },
                onAddToShoppingList = onAddToShoppingList,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (state.saveDialogVisible) {
        SaveVariantDialog(
            suggested = state.suggestedLabel.text(),
            onDismiss = { onSaveRequested(false) },
            onConfirm = { label, asDefault -> onSaveVariant(label, asDefault) },
        )
    }
}

@Composable
private fun AdjustBody(
    state: CookUiState,
    onModeChange: (CookMode) -> Unit,
    onServingsChange: (Int) -> Unit,
    onFactorChange: (String) -> Unit,
    onIngredientSelected: (String) -> Unit,
    onIngredientQuantityChange: (String) -> Unit,
    onPantryAmountChange: (String, String) -> Unit,
    onSnapAccept: (com.ilsecondodasinistra.proportion.core.domain.scale.SnapOption) -> Unit,
    onShowCard: () -> Unit,
    onSaveRequested: () -> Unit,
    onAddToShoppingList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ModeChips(state, onModeChange)

        when (state.mode) {
            CookMode.SERVINGS -> ServingsInput(state, onServingsChange)
            CookMode.FACTOR -> FactorInput(state, onFactorChange)
            CookMode.INGREDIENT -> IngredientConstraintInput(
                state = state,
                onIngredientSelected = onIngredientSelected,
                onQuantityChange = onIngredientQuantityChange,
            )
            CookMode.PANTRY -> PantryInput(state, onPantryAmountChange)
        }

        state.error?.let { error ->
            Text(
                text = stringResource(error.messageRes()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("cook_error"),
            )
        }

        val ovenAdvisory = state.ovenAdvisory
        val ovenAdvisoryTitle = stringResource(UiR.string.warning_oven_title)
        val ovenAdvisoryText = ovenAdvisory?.let {
            stringResource(UiR.string.warning_oven_message, it.tinDiameterRatio.format())
        }
        var lastOvenAdvisoryText by remember { mutableStateOf(ovenAdvisoryText.orEmpty()) }
        if (ovenAdvisoryText != null) {
            lastOvenAdvisoryText = ovenAdvisoryText
        }
        AnimatedVisibility(
            visible = ovenAdvisory != null,
            enter = fadeIn(tween(ProPortionMotion.BADGE_ENTER_MILLIS)) +
                expandVertically(tween(ProPortionMotion.BADGE_ENTER_MILLIS)),
            exit = fadeOut(tween(ProPortionMotion.BADGE_ENTER_MILLIS)) +
                shrinkVertically(tween(ProPortionMotion.BADGE_ENTER_MILLIS)),
        ) {
            WarningRow(
                title = ovenAdvisoryTitle,
                text = lastOvenAdvisoryText,
                testTag = "oven_advisory",
            )
        }

        HorizontalDivider()

        state.lines.forEach { line ->
            CookLineRow(line = line, isBottleneck = line.lineId == state.bottleneckLineId)

            val warningText = line.warningText?.let { stringResource(UiR.string.warning_non_integer, it) }
                ?: stringResource(UiR.string.warning_too_small)
            val warningActions = line.snaps.map { snap ->
                WarningAction(
                    label = stringResource(UiR.string.warning_snap_to, snap.label),
                    testTag = "snap_${snap.option.targetQty.toInt()}",
                    onClick = { onSnapAccept(snap.option) },
                )
            }
            var lastWarningText by remember(line.lineId) { mutableStateOf(warningText) }
            var lastWarningActions by remember(line.lineId) { mutableStateOf(warningActions) }
            if (line.hasWarning) {
                lastWarningText = warningText
                lastWarningActions = warningActions
            }
            AnimatedVisibility(
                visible = line.hasWarning,
                enter = fadeIn(tween(ProPortionMotion.BADGE_ENTER_MILLIS)) +
                    expandVertically(tween(ProPortionMotion.BADGE_ENTER_MILLIS)),
                exit = fadeOut(tween(ProPortionMotion.BADGE_ENTER_MILLIS)) +
                    shrinkVertically(tween(ProPortionMotion.BADGE_ENTER_MILLIS)),
            ) {
                WarningRow(
                    text = lastWarningText,
                    actions = lastWarningActions,
                    testTag = "warning_${line.lineId}",
                )
            }
        }

        if (state.leftovers.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.cook_leftovers,
                    state.leftovers.joinToString(", ") { "${it.name} ${it.text}" },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("leftovers"),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onSaveRequested,
                modifier = Modifier.weight(1f).testTag("save_variant_button"),
            ) {
                Text(stringResource(R.string.cook_save_variant))
            }
            OutlinedButton(
                onClick = onAddToShoppingList,
                modifier = Modifier.weight(1f).testTag("add_to_shopping_button"),
            ) {
                Text(stringResource(R.string.cook_add_to_shopping))
            }
            Button(
                onClick = onShowCard,
                modifier = Modifier.weight(1f).testTag("show_card_button"),
            ) {
                Text(stringResource(R.string.cook_show_card))
            }
        }

        Column(modifier = Modifier.padding(bottom = 32.dp)) {}
    }
}

@Composable
private fun ModeChips(state: CookUiState, onModeChange: (CookMode) -> Unit) {
    // Four labels do not fit a phone width: scrolling beats a chip that wraps mid-word.
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        CookMode.entries.forEach { mode ->
            val enabled = mode != CookMode.SERVINGS || state.servingsModeAvailable
            FilterChip(
                selected = state.mode == mode,
                enabled = enabled,
                onClick = { onModeChange(mode) },
                label = { Text(stringResource(mode.labelRes())) },
                modifier = Modifier.testTag("mode_${mode.name}"),
            )
        }
    }
}

@Composable
private fun ServingsInput(state: CookUiState, onServingsChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onServingsChange(state.servingsInput - 1) },
                modifier = Modifier.testTag("servings_minus"),
            ) {
                Icon(
                    Icons.Filled.Remove,
                    contentDescription = stringResource(R.string.cook_decrease_servings),
                )
            }
            Text(
                text = state.servingsInput.toString(),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(horizontal = 20.dp).testTag("servings_value"),
            )
            IconButton(
                onClick = { onServingsChange(state.servingsInput + 1) },
                modifier = Modifier.testTag("servings_plus"),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.cook_increase_servings),
                )
            }
        }
        FactorCaption(state)
    }
}

@Composable
private fun FactorInput(state: CookUiState, onFactorChange: (String) -> Unit) {
    Column {
        OutlinedTextField(
            value = state.factorInput,
            onValueChange = onFactorChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            label = { Text(stringResource(R.string.cook_mode_factor)) },
            modifier = Modifier.width(180.dp).testTag("factor_field"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            listOf("0,5", "2", "3").forEach { preset ->
                OutlinedButton(
                    onClick = { onFactorChange(preset) },
                    modifier = Modifier.testTag("factor_preset_$preset"),
                ) {
                    Text("×$preset")
                }
            }
        }
        FactorCaption(state)
    }
}

@Composable
private fun IngredientConstraintInput(
    state: CookUiState,
    onIngredientSelected: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.cook_pick_ingredient),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            state.lines.filter { it.isScaled }.forEach { line ->
                FilterChip(
                    selected = state.ingredientLineId == line.lineId,
                    onClick = { onIngredientSelected(line.lineId) },
                    label = { Text(line.name) },
                    modifier = Modifier.testTag("constraint_${line.lineId}"),
                )
            }
        }
        if (state.ingredientLineId != null) {
            OutlinedTextField(
                value = state.ingredientQuantityInput,
                onValueChange = onQuantityChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                label = { Text(stringResource(R.string.cook_pantry_have)) },
                modifier = Modifier.padding(top = 8.dp).width(180.dp).testTag("constraint_field"),
            )
        }
        FactorCaption(state)
    }
}

@Composable
private fun PantryInput(state: CookUiState, onPantryAmountChange: (String, String) -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.cook_pantry_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.lines.filter { it.isScaled }.forEach { line ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(line.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = line.originalText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = state.pantryInputs[line.lineId].orEmpty(),
                    onValueChange = { onPantryAmountChange(line.lineId, it) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text(stringResource(R.string.cook_pantry_have)) },
                    modifier = Modifier.width(150.dp).testTag("pantry_${line.lineId}"),
                )
            }
        }
        state.servings?.let { servings ->
            Text(
                text = stringResource(R.string.cook_achievable, servings.format()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp).testTag("achievable"),
            )
        }
    }
}

@Composable
private fun FactorCaption(state: CookUiState) {
    val recipeServings = state.recipe?.servings
    Text(
        text = if (recipeServings != null) {
            stringResource(R.string.cook_from_servings, recipeServings, state.factor.format())
        } else {
            stringResource(R.string.cook_factor_only, state.factor.format())
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp).testTag("factor_caption"),
    )
}

@Composable
private fun CookLineRow(line: CookLine, isBottleneck: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cook_line_${line.lineId}"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = line.name, style = MaterialTheme.typography.bodyLarge)
                if (line.isScaled && line.originalText != line.scaledText) {
                    Text(
                        text = line.originalText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough,
                    )
                }
                if (isBottleneck) {
                    Text(
                        text = stringResource(R.string.cook_bottleneck),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            AnimatedContent(
                targetState = line.scaledText,
                transitionSpec = {
                    (fadeIn(tween(ProPortionMotion.QUANTITY_COUNT_MILLIS)) +
                        slideInVertically(tween(ProPortionMotion.QUANTITY_COUNT_MILLIS)) { it / 4 })
                        .togetherWith(fadeOut(tween(ProPortionMotion.QUANTITY_COUNT_MILLIS)))
                },
                label = "scaled_quantity",
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("scaled_${line.lineId}"),
                )
            }
        }
    }
}

@Composable
private fun SaveVariantDialog(
    suggested: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean) -> Unit,
) {
    var label by remember { mutableStateOf(suggested) }
    var asDefault by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cook_save_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.cook_save_dialog_label)) },
                    modifier = Modifier.testTag("variant_label_field"),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable { asDefault = !asDefault }
                        .testTag("variant_default_row"),
                ) {
                    Checkbox(checked = asDefault, onCheckedChange = { asDefault = it })
                    Text(stringResource(R.string.cook_save_dialog_default))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(label, asDefault) },
                enabled = label.isNotBlank(),
                modifier = Modifier.testTag("variant_save_confirm"),
            ) {
                Text(stringResource(R.string.cook_save_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cook_save_dialog_cancel))
            }
        },
        modifier = Modifier.testTag("save_variant_dialog"),
    )
}

@Composable
private fun SuggestedLabel.text(): String = when (this) {
    is SuggestedLabel.Servings -> stringResource(R.string.cook_label_servings, value.format())
    is SuggestedLabel.Factor -> stringResource(R.string.cook_label_factor, value.format())
    SuggestedLabel.Pantry -> stringResource(R.string.cook_label_pantry)
}

private fun CookMode.labelRes(): Int = when (this) {
    CookMode.SERVINGS -> R.string.cook_mode_servings
    CookMode.INGREDIENT -> R.string.cook_mode_ingredient
    CookMode.FACTOR -> R.string.cook_mode_factor
    CookMode.PANTRY -> R.string.cook_mode_pantry
}

private fun CookError.messageRes(): Int = when (this) {
    CookError.NO_SERVINGS -> R.string.cook_error_no_servings
    CookError.INCOMPATIBLE_UNIT -> R.string.cook_error_unit
    CookError.APPROXIMATE_INGREDIENT -> R.string.cook_error_approximate
    CookError.NON_POSITIVE -> R.string.cook_error_factor
}

/** One decimal, comma separator, no trailing zero: "1,5" and "2", never "2,0". */
internal fun Double.format(): String =
    String.format(Locale.ROOT, "%.2f", this).trimEnd('0').trimEnd('.').replace('.', ',')
