package com.ilsecondodasinistra.proportion.core.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SyncPlanTest {

    @Test
    fun `nothing local yet and the incoming row is alive means insert`() {
        val action = decideSyncAction(local = null, remote = SyncableState(updatedAt = 100L))

        assertThat(action).isEqualTo(SyncAction.Insert)
    }

    @Test
    fun `nothing local yet and the incoming row is a tombstone means skip`() {
        val action = decideSyncAction(
            local = null,
            remote = SyncableState(updatedAt = 100L, deletedAt = 100L),
        )

        assertThat(action).isEqualTo(SyncAction.Skip)
    }

    @Test
    fun `a more recent incoming write overwrites an older local row`() {
        val action = decideSyncAction(
            local = SyncableState(updatedAt = 100L),
            remote = SyncableState(updatedAt = 200L),
        )

        assertThat(action).isEqualTo(SyncAction.Overwrite)
    }

    @Test
    fun `a more recent incoming tombstone deletes an older local row`() {
        val action = decideSyncAction(
            local = SyncableState(updatedAt = 100L),
            remote = SyncableState(updatedAt = 200L, deletedAt = 200L),
        )

        assertThat(action).isEqualTo(SyncAction.Delete)
    }

    @Test
    fun `an older incoming write is skipped, alive or not`() {
        val local = SyncableState(updatedAt = 200L)

        assertThat(decideSyncAction(local, SyncableState(updatedAt = 100L)))
            .isEqualTo(SyncAction.Skip)
        assertThat(decideSyncAction(local, SyncableState(updatedAt = 100L, deletedAt = 100L)))
            .isEqualTo(SyncAction.Skip)
    }

    @Test
    fun `an incoming write at the exact same timestamp is skipped, not re-applied`() {
        val action = decideSyncAction(
            local = SyncableState(updatedAt = 200L),
            remote = SyncableState(updatedAt = 200L),
        )

        assertThat(action).isEqualTo(SyncAction.Skip)
    }

    @Test
    fun `a locally tombstoned row is revived by a more recent incoming write, an undelete`() {
        val action = decideSyncAction(
            local = SyncableState(updatedAt = 100L, deletedAt = 100L),
            remote = SyncableState(updatedAt = 200L),
        )

        assertThat(action).isEqualTo(SyncAction.Overwrite)
    }

    @Test
    fun `an ingredient or tag, whose deletedAt is always null on both sides, never deletes`() {
        val action = decideSyncAction(
            local = SyncableState(updatedAt = 100L),
            remote = SyncableState(updatedAt = 200L),
        )

        assertThat(action).isNotEqualTo(SyncAction.Delete)
    }
}
