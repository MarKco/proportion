package com.ilsecondodasinistra.proportion.feature.home

/** A recipe as the dashboard cards show it: nothing but text and an id to navigate with. */
data class RecipeCardItem(
    val recipeId: String,
    val title: String,
    val cookCount: Int = 0,
    val isFavourite: Boolean = false,
)

/** The last cooked recipe, plus the scaling it was cooked at when one is saved as default. */
data class ContinueCooking(
    val recipeId: String,
    val title: String,
    val variantLabel: String?,
)

data class DonutSliceUi(val tagKey: String, val count: Int, val colorIndex: Int)

data class HomeUiState(
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,

    val recipeCount: Int = 0,
    val totalCooks: Int = 0,
    val favouriteCount: Int = 0,
    val donutSlices: List<DonutSliceUi> = emptyList(),
    val uncategorisedCount: Int = 0,

    val continueCooking: ContinueCooking? = null,
    val mostCooked: List<RecipeCardItem> = emptyList(),
    val favourites: List<RecipeCardItem> = emptyList(),

    val suggestion: RecipeCardItem? = null,
    val suggestionTagId: String? = null,
    val suggestionUnavailable: Boolean = false,
    val suggestionTags: List<TagChipItem> = emptyList(),
)

data class TagChipItem(val id: String, val key: String?, val name: String?)
