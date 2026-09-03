package com.ilsecondodasinistra.proportion.core.model

/**
 * One line of the folder sync (phase 10) activity log — kept so Settings can show recent errors
 * and the user can share the whole log via the system share sheet for debugging.
 */
data class SyncLogEntry(
    val timestamp: Long,
    val message: String,
    val isError: Boolean,
)
