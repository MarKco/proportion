package com.ilsecondodasinistra.proportion.core.sync

/**
 * What a device knows about one synced entity (a recipe, an ingredient, or a tag) at one point:
 * when it was last written, and whether that write was a deletion.
 *
 * Entity-agnostic on purpose: folder sync (phase 10) runs the same policy for all three kinds.
 * Only a recipe ever carries a non-null [deletedAt] today — ingredients and tags have no delete
 * flow in the app yet, so their [SyncableState] always has `deletedAt = null` on both sides, and
 * [decideSyncAction] never returns [SyncAction.Delete] for them as a consequence, not as a
 * special case.
 */
data class SyncableState(val updatedAt: Long, val deletedAt: Long? = null)

/**
 * Decides what to do with one incoming file, given what this device already knows about the same
 * entity. [local] is `null` when the entity does not exist on this device yet.
 *
 * Pure and total: no I/O, no clock. A tie ([remote] no more recent than [local]) always favours
 * the local row, so re-processing the same file twice is a no-op.
 */
fun decideSyncAction(local: SyncableState?, remote: SyncableState): SyncAction = when {
    local == null -> if (remote.deletedAt != null) SyncAction.Skip else SyncAction.Insert
    remote.updatedAt <= local.updatedAt -> SyncAction.Skip
    remote.deletedAt != null -> SyncAction.Delete
    else -> SyncAction.Overwrite
}
