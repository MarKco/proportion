package com.ilsecondodasinistra.proportion.core.domain

/**
 * Schedules/cancels the periodic background folder sync (phase 10) — kept out of `:core:data` so
 * `:feature:settings` (which flips the toggle that drives this) does not need to know it is
 * `WorkManager` underneath.
 */
interface SyncScheduler {
    /**
     * Idempotent for the same [intervalHours]: calling this again with the value already in
     * effect changes nothing. A different value takes effect on this call, without waiting for
     * the interval currently running to elapse.
     */
    fun schedule(intervalHours: Int)
    fun cancel()
}
