package com.ilsecondodasinistra.proportion.core.sync

/** What a device should do with one incoming file from the sync folder. */
sealed interface SyncAction {
    /** Nothing local yet: write the incoming row as a new one. */
    data object Insert : SyncAction

    /** Something local exists, but the incoming write is more recent: replace it. */
    data object Overwrite : SyncAction

    /** The incoming write is a tombstone more recent than the local row: remove it locally. */
    data object Delete : SyncAction

    /** The local row is at least as recent as the incoming one: do nothing. */
    data object Skip : SyncAction
}
