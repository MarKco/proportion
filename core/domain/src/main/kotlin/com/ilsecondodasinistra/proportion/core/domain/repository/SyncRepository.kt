package com.ilsecondodasinistra.proportion.core.domain.repository

import com.ilsecondodasinistra.proportion.core.model.SyncLogEntry
import kotlinx.coroutines.flow.Flow

/**
 * Folder sync (phase 10): shares recipes, and the literal (non built-in) ingredient/tag
 * catalogue, with another device through a folder something like Syncthing keeps in sync —
 * never a raw copy of the database. See `docs/private/HISTORY.md`, phase 10.
 *
 * No-op (never throws) whenever sync is disabled or no folder has been chosen — a caller does not
 * need to check [com.ilsecondodasinistra.proportion.core.model.UserPreferences.syncEnabled] first.
 */
interface SyncRepository {

    /** Writes/updates this one recipe's file in the sync folder. Also covers a tombstone. */
    suspend fun exportRecipe(recipeId: String)

    /** Writes/updates this one ingredient's file. A built-in row is silently skipped. */
    suspend fun exportIngredient(ingredientId: String)

    /** Writes/updates this one tag's file. A built-in row is silently skipped. */
    suspend fun exportTag(tagId: String)

    /**
     * The full sync pass: (re-)exports every local recipe and every literal ingredient/tag —
     * cheap and idempotent, and what makes turning sync on, or a fresh install pointed at an
     * already-populated folder, work without a separate "first run" code path — then imports
     * every file found in the folder, applying [com.ilsecondodasinistra.proportion.core.sync]'s
     * policy against what is already here.
     */
    suspend fun syncNow(): SyncResult

    /** The sync activity log, most recent last — see [SyncLogEntry]. */
    fun observeLog(): Flow<List<SyncLogEntry>>
}

data class SyncResult(
    val exported: Int,
    val recipesImported: Int,
    val recipesDeleted: Int,
    val catalogueImported: Int,
)
