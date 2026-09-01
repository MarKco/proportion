package com.ilsecondodasinistra.proportion.feature.cook

import com.ilsecondodasinistra.proportion.core.domain.scale.SnapOption
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Recipe

/** What the user is holding fixed. One at a time: two constraints would over-determine the recipe. */
enum class CookMode { SERVINGS, INGREDIENT, FACTOR, PANTRY }

/** Why a scaling was refused. The screen turns these into sentences. */
enum class CookError { NO_SERVINGS, INCOMPATIBLE_UNIT, APPROXIMATE_INGREDIENT, NON_POSITIVE }

data class CookLine(
    val lineId: String,
    val name: String,
    val originalText: String,
    val scaledText: String,
    val unit: MeasureUnit,
    val isScaled: Boolean,
    val hasWarning: Boolean = false,
    val warningText: String? = null,
    val snaps: List<SnapChip> = emptyList(),
)

/** A practical amount to round to, with the factor it implies for the whole recipe. */
data class SnapChip(val label: String, val option: SnapOption)

data class OvenAdvisory(val factor: Double, val tinDiameterRatio: Double)

data class LeftoverUi(val lineId: String, val name: String, val text: String)

/** The label offered when saving, so the screen can phrase it in the app language. */
sealed interface SuggestedLabel {
    data class Servings(val value: Double) : SuggestedLabel
    data class Factor(val value: Double) : SuggestedLabel
    data object Pantry : SuggestedLabel
}

data class CookUiState(
    val isLoading: Boolean = true,
    val recipe: Recipe? = null,
    val mode: CookMode = CookMode.SERVINGS,
    val servingsModeAvailable: Boolean = true,

    val servingsInput: Int = 0,
    val factorInput: String = "1",
    /** The exact value behind [factorInput]: text would round 4/3 to 1,33 and drift the recipe. */
    val factorValue: Double? = 1.0,
    val ingredientLineId: String? = null,
    val ingredientQuantityInput: String = "",
    val pantryInputs: Map<String, String> = emptyMap(),

    val factor: Double = 1.0,
    val servings: Double? = null,
    val lines: List<CookLine> = emptyList(),
    val ovenAdvisory: OvenAdvisory? = null,
    val bottleneckLineId: String? = null,
    val leftovers: List<LeftoverUi> = emptyList(),
    val error: CookError? = null,

    val showCard: Boolean = false,
    val saveDialogVisible: Boolean = false,
    val suggestedLabel: SuggestedLabel = SuggestedLabel.Factor(1.0),
)
