package com.ilsecondodasinistra.proportion.core.database.dao

/**
 * Folder sync (phase 10) push pass: an id paired with the `updated_at` it currently has, so the
 * push loop can compare against [SyncCacheDao] without a second query per row.
 */
data class SyncableEntityId(val id: String, val updatedAt: Long)
