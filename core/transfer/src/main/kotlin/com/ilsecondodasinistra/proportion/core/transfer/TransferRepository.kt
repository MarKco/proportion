package com.ilsecondodasinistra.proportion.core.transfer

import com.ilsecondodasinistra.proportion.core.model.Recipe

/**
 * Moves recipes in and out of the app.
 *
 * Declared here rather than in `:core:domain` because the file format is what it is about, and
 * `:core:transfer` already depends on the domain — the other direction would be a cycle.
 *
 * Import is two steps on purpose: [preview] reads the file and reports what it holds without
 * touching the database, so the user decides with the counts in front of them.
 */
interface TransferRepository {

    suspend fun exportAll(): String

    /** Null when the recipe no longer exists. */
    suspend fun exportRecipe(recipeId: String): String?

    /**
     * Folder sync (phase 10) only. Null when the ingredient no longer exists, or is built-in —
     * built-ins are seeded identically on every install and never need to travel.
     */
    suspend fun exportIngredient(ingredientId: String): String?

    /** Folder sync (phase 10) only. Null when the tag no longer exists, or is built-in. */
    suspend fun exportTag(tagId: String): String?

    /**
     * Folder sync (phase 10) only: decodes a single-recipe `.proportion` file into a fully
     * resolved [Recipe] — ingredients and tags matched against the catalogue exactly as [import]
     * would, `updatedAt`/`deletedAt`/`createdAt` carried through untouched — without writing
     * anything. `null` on a malformed file, an empty one, or one carrying more than one recipe (a
     * sync file only ever holds one).
     */
    suspend fun resolveRecipe(text: String): Recipe?

    suspend fun preview(text: String): ImportPreview

    suspend fun import(text: String, mode: ImportMode): ImportOutcome
}
