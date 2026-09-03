package com.ilsecondodasinistra.proportion.feature.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilsecondodasinistra.proportion.core.model.Ingredient
import com.ilsecondodasinistra.proportion.core.ui.component.DensityPromptDialog
import com.ilsecondodasinistra.proportion.core.ui.component.UnitPicker
import com.ilsecondodasinistra.proportion.core.ui.tagLabel
import com.ilsecondodasinistra.proportion.core.ui.unitLabel

/**
 * Desired room reserved below a newly-focused ingredient row's own (real, measured) height, so the
 * suggestion list that appears once typing starts already has space to show above the keyboard.
 * Sized for three full rows of suggestion chips plus their [FlowRow] padding, generously rounded
 * up. This is only ever a ceiling: the actual amount requested is capped to whatever room is left
 * above the keyboard after the row itself, so the row (and the edit text in it) is never pushed
 * off the top of the screen trying to fit more clearance than the viewport has.
 */
private val NEW_LINE_SUGGESTIONS_CLEARANCE = 420.dp

/**
 * A single mutable y coordinate in window space, written from layout and read only on demand.
 * Deliberately not Compose state: layout runs on every scrolled frame, and a state write there
 * would recompose on each one - which is precisely the kind of churn this screen is avoiding.
 */
private class WindowY {
    var y: Float = 0f
}

@Composable
fun EditorRoute(
    onDone: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onDone()
    }

    EditorScreen(
        state = state,
        onTitleChange = viewModel::onTitleChange,
        onServingsChange = viewModel::onServingsChange,
        onLineNameChange = viewModel::onLineNameChange,
        onSuggestionPick = viewModel::onSuggestionPick,
        onLineQuantityChange = viewModel::onLineQuantityChange,
        onLineUnitChange = viewModel::onLineUnitChange,
        onDensityPromptConfirm = viewModel::onDensityPromptConfirm,
        onDensityPromptDismiss = viewModel::onDensityPromptDismiss,
        onAddLine = viewModel::onAddLine,
        onNewLineFocusHandled = viewModel::onNewLineFocusHandled,
        onRemoveLine = viewModel::onRemoveLine,
        onStepChange = viewModel::onStepChange,
        onAddStep = viewModel::onAddStep,
        onNewStepFocusHandled = viewModel::onNewStepFocusHandled,
        onRemoveStep = viewModel::onRemoveStep,
        onTagToggle = viewModel::onTagToggle,
        onCreateTag = viewModel::onCreateTag,
        onSave = viewModel::onSave,
        onBack = onDone,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    state: EditorUiState,
    onTitleChange: (String) -> Unit,
    onServingsChange: (Int?) -> Unit,
    onLineNameChange: (Int, String) -> Unit,
    onSuggestionPick: (Int, Ingredient) -> Unit,
    onLineQuantityChange: (Int, String) -> Unit,
    onLineUnitChange: (Int, com.ilsecondodasinistra.proportion.core.model.MeasureUnit) -> Unit,
    onDensityPromptConfirm: (Double?, Double?) -> Unit,
    onDensityPromptDismiss: () -> Unit,
    onAddLine: () -> Unit,
    onNewLineFocusHandled: () -> Unit,
    onRemoveLine: (Int) -> Unit,
    onStepChange: (Int, String) -> Unit,
    onAddStep: () -> Unit,
    onNewStepFocusHandled: () -> Unit,
    onRemoveStep: (Int) -> Unit,
    onTagToggle: (String) -> Unit,
    onCreateTag: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    var showDiscardDialog by remember { mutableStateOf(false) }
    val leave = { if (state.isDirty) showDiscardDialog = true else onBack() }
    // The scrollable Column's own laid-out height - fixed by fillMaxSize, unaffected by the
    // keyboard (imePadding insets the *content* inside this box, it doesn't shrink the box
    // itself) - so this is the reference used below to work out how much room is actually left
    // above the keyboard to scroll a newly-focused ingredient row into.
    var scrollViewportHeightPx by remember { mutableStateOf(0) }

    // Scrolling a newly-focused ingredient row into view is done by driving this state directly
    // rather than through a BringIntoViewRequester: a requester can only be asked to fit a rect,
    // and when the rect it is handed doesn't fit it silently scrolls as far as it can instead -
    // which, for a request as tall as the viewport, means flinging the row up under the top bar.
    // An explicit target offset is exact, and clamps by simply not moving further.
    val scrollState = rememberScrollState()

    // Window y of the first item inside the scrollable content, i.e. of the content's own top.
    // Distances are measured against this rather than against the viewport node: the viewport
    // node's reported position does not include the Scaffold padding applied to it, so using it
    // put every measurement one top-bar-height off - which scrolled a focused row that much too
    // far, up under the top bar. A row's offset from the content top, minus the current scroll
    // value, is the row's position in the viewport, with no inset arithmetic involved.
    //
    // Kept in a plain holder rather than in state: it is written from layout on every pass, and a
    // state write there would recompose the whole screen on each one.
    val contentPosition = remember { WindowY() }

    // WindowInsets.ime is a live value that keeps reporting the real-time keyboard height -
    // including small fluctuations while the IME is up (e.g. its own predictive-text/candidate
    // strip changing height as the user types), which `imePadding()` would otherwise follow on
    // every single frame, reflowing this whole screen's content each time. That reflow is what
    // reads as the field glitching/re-scrolling on every keystroke while the keyboard is open.
    // So: use `imePadding()` only for the opening/closing transition; once the height is stable,
    // freeze it and stop reacting to further live changes until the keyboard actually closes.
    val density = LocalDensity.current
    val imeVisible = WindowInsets.isImeVisible
    val imeInsets = WindowInsets.ime
    var stableImeBottomPx by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            var lastHeight = imeInsets.getBottom(density)
            var stableFrames = 0
            var frame = 0
            while (stableFrames < 3 && frame < 30) {
                withFrameNanos {}
                val height = imeInsets.getBottom(density)
                stableFrames = if (height == lastHeight) stableFrames + 1 else 0
                lastHeight = height
                frame++
            }
            stableImeBottomPx = lastHeight
        } else {
            stableImeBottomPx = null
        }
    }

    // How much of the scrollable viewport the keyboard actually covers. NOT the same as the ime
    // inset: that one is measured from the bottom of the *window*, so it includes the navigation
    // bar - which Scaffold has already taken out of the content padding, and therefore out of the
    // viewport height measured above. Subtracting it here keeps the two measurements in the same
    // coordinate space; using the raw ime inset instead double-counts the navigation bar and
    // scrolls a focused row that much too far up (far enough to hide it under the top bar).
    val navigationBarBottomPx = WindowInsets.navigationBars.getBottom(density)
    val stableImeOverlapPx = stableImeBottomPx?.let {
        (it - navigationBarBottomPx).coerceAtLeast(0)
    }

    BackHandler(enabled = state.isDirty) { showDiscardDialog = true }

    Scaffold(
        modifier = Modifier.testTag("editor_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.editor_title_edit else R.string.editor_title_new,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = leave, modifier = Modifier.testTag("editor_back")) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.editor_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onSave,
                        enabled = !state.isSaving,
                        modifier = Modifier.testTag("editor_save"),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.editor_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onSizeChanged { scrollViewportHeightPx = it.height }
                .verticalScroll(scrollState)
                .then(
                    stableImeBottomPx?.let { Modifier.padding(bottom = with(density) { it.toDp() }) }
                        ?: Modifier.imePadding(),
                )
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.editor_field_title)) },
                singleLine = true,
                isError = ValidationError.TITLE_REQUIRED in state.errors,
                supportingText = {
                    if (ValidationError.TITLE_REQUIRED in state.errors) {
                        Text(stringResource(R.string.editor_error_title))
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("editor_title_field")
                    // First item in the scrollable content: its position marks the content's top.
                    .onGloballyPositioned { contentPosition.y = it.positionInWindow().y },
            )

            ServingsStepper(state.servings, onServingsChange)

            SectionTitle(stringResource(R.string.editor_section_tags))
            TagSection(state, onTagToggle, onCreateTag)

            SectionTitle(stringResource(R.string.editor_section_ingredients))
            if (ValidationError.INGREDIENTS_REQUIRED in state.errors) {
                ErrorText(stringResource(R.string.editor_error_ingredients))
            }
            if (ValidationError.QUANTITY_REQUIRED in state.errors) {
                ErrorText(stringResource(R.string.editor_error_quantity))
            }
            if (ValidationError.UNIT_REQUIRED in state.errors) {
                ErrorText(stringResource(R.string.editor_error_unit))
            }

            state.lines.forEachIndexed { index, line ->
                IngredientEditorRow(
                    index = index,
                    line = line,
                    suggestions = if (state.suggestionLineIndex == index) state.suggestions else emptyList(),
                    requestFocusAndScroll = line.id == state.justAddedLineId,
                    scrollState = scrollState,
                    scrollViewportHeightPx = scrollViewportHeightPx,
                    contentPosition = contentPosition,
                    stableImeOverlapPx = stableImeOverlapPx,
                    onFocusHandled = onNewLineFocusHandled,
                    onNameChange = onLineNameChange,
                    onSuggestionPick = onSuggestionPick,
                    onQuantityChange = onLineQuantityChange,
                    onUnitChange = onLineUnitChange,
                    onRemove = { onRemoveLine(index) },
                )
            }

            TextButton(onClick = onAddLine, modifier = Modifier.testTag("editor_add_ingredient")) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.editor_add_ingredient),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            SectionTitle(stringResource(R.string.editor_section_steps))
            // Recreated whenever the step count changes; stable across recompositions triggered by
            // typing, since those don't change state.steps.size.
            val stepFocusRequesters = remember(state.steps.size) { List(state.steps.size) { FocusRequester() } }
            state.steps.forEachIndexed { index, step ->
                StepEditorRow(
                    index = index,
                    step = step,
                    requestFocus = index == state.justAddedStepIndex,
                    focusRequester = stepFocusRequesters[index],
                    onFocusHandled = onNewStepFocusHandled,
                    onStepChange = onStepChange,
                    onRemoveStep = { onRemoveStep(index) },
                    onNext = {
                        if (index == state.steps.lastIndex) {
                            onAddStep()
                        } else {
                            stepFocusRequesters[index + 1].requestFocus()
                        }
                    },
                )
            }

            TextButton(onClick = onAddStep, modifier = Modifier.testTag("editor_add_step")) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.editor_add_step),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Column(modifier = Modifier.padding(bottom = 32.dp)) {}
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBack()
                    },
                    modifier = Modifier.testTag("discard_confirm"),
                ) {
                    Text(stringResource(R.string.editor_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.editor_discard_cancel))
                }
            },
            modifier = Modifier.testTag("discard_dialog"),
        )
    }

    state.pendingDensityPrompt?.let { prompt ->
        DensityPromptDialog(
            ingredientName = prompt.ingredientName,
            requirement = prompt.requirement,
            onDismiss = onDensityPromptDismiss,
            onConfirm = onDensityPromptConfirm,
        )
    }
}

@Composable
private fun ServingsStepper(servings: Int?, onChange: (Int?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.editor_field_servings),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onChange(((servings ?: 1) - 1).coerceAtLeast(1)) },
            modifier = Modifier.testTag("servings_minus"),
        ) {
            Icon(
                Icons.Filled.Remove,
                contentDescription = stringResource(R.string.editor_decrease_servings),
            )
        }
        Text(
            text = (servings ?: 1).toString(),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.testTag("servings_value"),
        )
        IconButton(
            onClick = { onChange((servings ?: 1) + 1) },
            modifier = Modifier.testTag("servings_plus"),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.editor_increase_servings),
            )
        }
    }
}

@Composable
private fun TagSection(
    state: EditorUiState,
    onTagToggle: (String) -> Unit,
    onCreateTag: (String) -> Unit,
) {
    var newTag by remember { mutableStateOf("") }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        state.availableTags.forEach { tag ->
            FilterChip(
                selected = tag.id in state.selectedTagIds,
                onClick = { onTagToggle(tag.id) },
                label = { Text(tagLabel(tag)) },
                modifier = Modifier.testTag("editor_tag_${tag.key ?: tag.name}"),
            )
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newTag,
            onValueChange = { newTag = it },
            label = { Text(stringResource(R.string.editor_new_tag)) },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("editor_new_tag_field"),
        )
        IconButton(
            onClick = {
                onCreateTag(newTag)
                newTag = ""
            },
            enabled = newTag.isNotBlank(),
            modifier = Modifier.testTag("editor_new_tag_add"),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.editor_new_tag))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IngredientEditorRow(
    index: Int,
    line: EditorLine,
    suggestions: List<Ingredient>,
    requestFocusAndScroll: Boolean,
    onFocusHandled: () -> Unit,
    onNameChange: (Int, String) -> Unit,
    onSuggestionPick: (Int, Ingredient) -> Unit,
    onQuantityChange: (Int, String) -> Unit,
    onUnitChange: (Int, com.ilsecondodasinistra.proportion.core.model.MeasureUnit) -> Unit,
    onRemove: () -> Unit,
    scrollState: ScrollState,
    scrollViewportHeightPx: Int,
    contentPosition: WindowY,
    stableImeOverlapPx: Int?,
) {
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    var cardSize by remember { mutableStateOf<IntSize?>(null) }
    var nameFieldFocused by remember { mutableStateOf(false) }
    val rowPosition = remember { WindowY() }

    // Clearance reserved below the card as REAL laid-out space (see the spacer at the bottom of
    // this composable). Reserving it in the layout is what keeps the scroll position still while
    // typing: the caret stays inside the viewport on its own, so BasicTextField's built-in
    // per-keystroke "keep the cursor visible" bringIntoView finds nothing to do and never scrolls.
    // Fighting that call with a counter-scroll of our own after every keystroke - the previous
    // approach - produced a visible upward jump on every character typed.
    var reservedClearancePx by remember { mutableStateOf(0) }

    // The suggestion chips grow into the reserved clearance rather than on top of it: the spacer
    // shrinks by exactly the height they take, so the row's total height stays put as the filtered
    // list changes size on each keystroke and nothing below it shifts either.
    var suggestionsHeightPx by remember { mutableStateOf(0) }
    val liveSuggestionsHeightPx = if (suggestions.isEmpty()) 0 else suggestionsHeightPx
    val spacerHeight = with(density) {
        (reservedClearancePx - liveSuggestionsHeightPx).coerceAtLeast(0).toDp()
    }

    LaunchedEffect(requestFocusAndScroll) {
        if (requestFocusAndScroll) focusRequester.requestFocus()
    }

    // Keyed on the name field's own focus state rather than requestFocusAndScroll, so this also
    // fires when the user manually taps into a line that was never "added" via the + button -
    // e.g. the single empty line a brand-new recipe starts with, or the one a recipe is left
    // with after deleting every ingredient - neither of which ever gets requestFocusAndScroll.
    //
    // Waits for stableImeOverlapPx (computed once, up in EditorScreen, and held steady rather than
    // tracking the keyboard's live/animating height) instead of polling its own - both so the
    // reservation below is computed against the same frozen height the screen's padding actually
    // uses, and so this doesn't re-poll redundantly per row.
    //
    // Deliberately runs only ONCE per focus gain (not keyed on cardSize or on the suggestion
    // list's changing size/emptiness): re-running this as the card resizes while typing - which
    // happens on almost every keystroke, as the filtered suggestion count and chip wrapping keep
    // changing - is exactly what made the row visibly jump on every character. It reserves the
    // clearance once (see NEW_LINE_SUGGESTIONS_CLEARANCE) and scrolls once; from then on the
    // reserved space, not repeated scrolling, is what keeps the suggestions visible.
    LaunchedEffect(nameFieldFocused, stableImeOverlapPx) {
        val imeOverlapPx = stableImeOverlapPx
        if (!nameFieldFocused || imeOverlapPx == null) {
            reservedClearancePx = 0
            return@LaunchedEffect
        }

        var size = cardSize
        var sizeWait = 0
        while (size == null && sizeWait < 30) {
            withFrameNanos {}
            size = cardSize
            sizeWait++
        }
        val finalSize = size ?: return@LaunchedEffect

        // Reserve room below the row for the suggestions that are about to appear, but never more
        // than the viewport above the keyboard actually has left after the row itself: clearance
        // the screen cannot show is clearance that would only push the row off the top.
        val availableAboveKeyboardPx = (scrollViewportHeightPx - imeOverlapPx).coerceAtLeast(0)
        val maxClearancePx = (availableAboveKeyboardPx - finalSize.height).coerceAtLeast(0)
        val desiredClearancePx = with(density) { NEW_LINE_SUGGESTIONS_CLEARANCE.roundToPx() }
        val clearancePx = minOf(desiredClearancePx, maxClearancePx)
        reservedClearancePx = clearancePx

        // Let the spacer get laid out first, so the scroll range already includes it - a row near
        // the end of the content otherwise has nothing left to scroll into.
        withFrameNanos {}
        withFrameNanos {}

        // Scroll by exactly the overlap between the block (row + its reserved clearance) and the
        // band the keyboard covers, and never by more than would bring the row's own top to the
        // top of the viewport. That second clamp is the whole point of computing the offset by
        // hand: the row itself always stays visible, and when there isn't room for the full
        // clearance the suggestions simply get less of it instead of the row getting shoved away.
        val rowTopInContentPx = rowPosition.y - contentPosition.y
        val rowTopInViewportPx = rowTopInContentPx - scrollState.value
        val blockBottomPx = rowTopInViewportPx + finalSize.height + clearancePx
        val visibleBottomPx = (scrollViewportHeightPx - imeOverlapPx).toFloat()
        val scrollByPx = (blockBottomPx - visibleBottomPx)
            .coerceAtLeast(0f)
            .coerceAtMost(rowTopInViewportPx.coerceAtLeast(0f))
        if (scrollByPx > 0f) {
            scrollState.animateScrollTo((scrollState.value + scrollByPx).toInt())
        }

        // Only now, not in the focus-request effect above: clearing the ViewModel's flag flips
        // requestFocusAndScroll back to false on the next recomposition, and (when this was the
        // just-added line) that must not happen until the scroll above has actually run. Guarded
        // because this effect also fires for manual taps on lines that were never "added".
        if (requestFocusAndScroll) onFocusHandled()
    }

    Column(
        modifier = Modifier.onGloballyPositioned { rowPosition.y = it.positionInWindow().y },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("editor_line_$index")
                .onSizeChanged { cardSize = it },
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = line.name,
                        onValueChange = { onNameChange(index, it) },
                        label = { Text(stringResource(R.string.editor_ingredient_name)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("editor_line_name_$index")
                            .focusRequester(focusRequester)
                            .onFocusChanged { nameFieldFocused = it.isFocused },
                    )
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.testTag("editor_line_remove_$index"),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.editor_remove),
                        )
                    }
                }

                if (suggestions.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                            .onSizeChanged { suggestionsHeightPx = it.height }
                            .testTag("editor_suggestions_$index"),
                    ) {
                        suggestions.forEach { ingredient ->
                            AssistChip(
                                onClick = { onSuggestionPick(index, ingredient) },
                                label = { Text(ingredient.name) },
                                modifier = Modifier.testTag("suggestion_${ingredient.id}"),
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = line.quantity,
                        onValueChange = { onQuantityChange(index, it) },
                        label = { Text(stringResource(R.string.editor_quantity)) },
                        singleLine = true,
                        enabled = line.unit?.isScalable != false,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(140.dp).testTag("editor_line_qty_$index"),
                    )
                    UnitPicker(
                        selected = line.unit,
                        unitName = { unit -> unitLabel(unit) },
                        onSelect = { onUnitChange(index, it) },
                        emptyLabel = stringResource(R.string.editor_unit_unset),
                    )
                }
            }
        }

        // The reserved clearance itself: real, scrollable space that the one-shot scroll above
        // brings into view along with the card, and that the suggestion chips then grow into.
        if (spacerHeight > 0.dp) {
            Spacer(modifier = Modifier.height(spacerHeight))
        }
    }
}

@Composable
private fun StepEditorRow(
    index: Int,
    step: String,
    requestFocus: Boolean,
    focusRequester: FocusRequester,
    onFocusHandled: () -> Unit,
    onStepChange: (Int, String) -> Unit,
    onRemoveStep: () -> Unit,
    onNext: () -> Unit,
) {
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            onFocusHandled()
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = step,
            onValueChange = { onStepChange(index, it) },
            label = { Text(stringResource(R.string.editor_step_number, index + 1)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
            modifier = Modifier.weight(1f).testTag("editor_step_$index")
                .focusRequester(focusRequester),
        )
        IconButton(onClick = onRemoveStep) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.editor_remove),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(text = text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}
