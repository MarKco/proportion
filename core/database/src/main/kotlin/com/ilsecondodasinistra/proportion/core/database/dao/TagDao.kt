package com.ilsecondodasinistra.proportion.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ilsecondodasinistra.proportion.core.database.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY is_built_in DESC, IFNULL(key, name)")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE key = :key LIMIT 1")
    suspend fun findByKey(key: String): TagEntity?

    @Upsert
    suspend fun upsert(tag: TagEntity)

    /** Built-in tags are never deletable; the repository enforces that. */
    @Query("DELETE FROM tags WHERE id = :id AND is_built_in = 0")
    suspend fun deleteUserTag(id: String)
}
