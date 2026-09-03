package com.ilsecondodasinistra.proportion.core.domain

/**
 * Schedules/cancels the periodic background folder sync (phase 10) — kept out of `:core:data` so
 * `:feature:settings` (which flips the toggle that drives this) does not need to know it is
 * `WorkManager` underneath.
 */
interface SyncScheduler {
    /** Idempotent: calling this while already scheduled changes nothing. */
    fun schedule()
    fun cancel()
}
