package com.ilsecondodasinistra.proportion.core.transfer

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

    suspend fun preview(text: String): ImportPreview

    suspend fun import(text: String, mode: ImportMode): ImportOutcome
}
