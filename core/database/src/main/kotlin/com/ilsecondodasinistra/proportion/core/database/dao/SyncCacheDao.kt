package com.ilsecondodasinistra.proportion.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ilsecondodasinistra.proportion.core.database.entity.SyncExportCacheEntity
import com.ilsecondodasinistra.proportion.core.database.entity.SyncSeenFileEntity

/** Folder sync (phase 10) dirty-check cache — see [SyncExportCacheEntity]/[SyncSeenFileEntity]. */
@Dao
interface SyncCacheDao {

    @Query("SELECT exported_updated_at FROM sync_export_cache WHERE entity_id = :id")
    suspend fun exportedUpdatedAt(id: String): Long?

    @Upsert
    suspend fun upsertExportCache(entry: SyncExportCacheEntity)

    @Query("DELETE FROM sync_export_cache WHERE entity_id = :id")
    suspend fun deleteExportCache(id: String)

    @Query("SELECT last_modified FROM sync_seen_file WHERE file_name = :name")
    suspend fun seenMtime(name: String): Long?

    @Upsert
    suspend fun upsertSeenFile(entry: SyncSeenFileEntity)

    @Query("DELETE FROM sync_seen_file WHERE file_name = :name")
    suspend fun deleteSeenFile(name: String)
}
