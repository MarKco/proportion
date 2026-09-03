package com.ilsecondodasinistra.proportion.core.model

/**
 * Coarse classification of an ingredient, for future catalogue browsing/filtering.
 *
 * Foundation-only in phase 8: every ingredient carries one, but no UI surfaces it yet.
 */
enum class IngredientCategory {
    FLOUR_AND_GRAIN,
    DAIRY_AND_EGG,
    FAT_AND_OIL,
    SUGAR_AND_SWEETENER,
    LEAVENING_AND_BAKING,
    CHOCOLATE_AND_COCOA,
    FRUIT,
    VEGETABLE,
    HERB_AND_SPICE,
    MEAT,
    FISH_AND_SEAFOOD,
    LEGUME,
    NUT_AND_SEED,
    CONDIMENT_AND_SAUCE,
    BEVERAGE,
    OTHER,
}
