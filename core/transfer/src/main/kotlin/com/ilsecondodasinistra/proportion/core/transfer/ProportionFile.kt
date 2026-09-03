package com.ilsecondodasinistra.proportion.core.transfer

import com.ilsecondodasinistra.proportion.core.domain.scale.ScaleConstraint
import kotlinx.serialization.Serializable

/**
 * The `.proportion` wire format.
 *
 * This is a contract with other installs of the app, so it is deliberately separate from the
 * database entities: the schema can change without the file format changing, and the other way
 * round. [VERSION] is what makes that survivable.
 */
@Serializable
data class ProportionFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: String? = null,
    val recipes: List<WireRecipe> = emptyList(),
) {
    companion object {
        const val FORMAT = "proportion"

        /** Raise this only together with a migration in [ProportionCodec]. */
        const val VERSION = 1

        const val EXTENSION = "proportion"
        const val MIME_TYPE = "application/octet-stream"

        /** Built-in tags travel by key so they stay translated on the other side. */
        const val BUILT_IN_TAG_PREFIX = "builtin:"

        /** Built-in ingredients travel by key too, same reason as [BUILT_IN_TAG_PREFIX]. */
        const val BUILT_IN_INGREDIENT_PREFIX = "builtin:"
    }
}

@Serializable
data class WireRecipe(
    /** A stable UUID, so the receiving app can tell an update from a new recipe. */
    val id: String,
    val title: String,
    val servings: Int? = null,
    val tags: List<String> = emptyList(),
    val ingredients: List<WireIngredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val notes: String? = null,
    val variants: List<WireVariant> = emptyList(),
    /**
     * Folder sync (phase 10) tombstone: set instead of omitting the recipe, so the other device
     * can tell "deleted after we last synced" from "never existed" and propagate the deletion.
     * Absent (`null`) for every recipe a v1 app writes.
     */
    val deletedAt: Long? = null,
    /**
     * Folder sync (phase 10) only: when this recipe was last written, the key a conflict is
     * resolved by. `0` for every recipe a v1 app writes (never compared against anything in v1).
     */
    val updatedAt: Long = 0L,
    /** Folder sync (phase 10) only: carried through so a re-imported recipe keeps its age. */
    val createdAt: Long = 0L,
)

@Serializable
data class WireIngredient(
    val name: String,
    val qty: Double? = null,
    val unit: String,
    val display: String? = null,
    val note: String? = null,
    /** v2 preparation: written by nobody in v1, carried through untouched when present. */
    val density: Double? = null,
)

@Serializable
data class WireVariant(
    val label: String,
    val constraint: ScaleConstraint,
    val isDefault: Boolean = false,
)

/**
 * Folder sync (phase 10) only: one of these is the whole content of an `ingredient-<id>.proportion`
 * file — a literal (non built-in) catalogue row, not a reference inside a recipe (that is
 * [WireIngredient]). Built-in rows never travel this way: they are seeded identically on every
 * install.
 */
@Serializable
data class WireIngredientEntry(
    val id: String,
    val name: String,
    val normalisedName: String,
    val defaultUnit: String,
    val category: String? = null,
    val densityGramsPerMl: Double? = null,
    val itemWeightGrams: Double? = null,
    val updatedAt: Long = 0L,
)

/** Folder sync (phase 10) only: the whole content of a `tag-<id>.proportion` file. */
@Serializable
data class WireTagEntry(
    val id: String,
    val name: String,
    val colorIndex: Int = 0,
    val updatedAt: Long = 0L,
)
