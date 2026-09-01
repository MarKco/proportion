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
