package com.ilsecondodasinistra.proportion.core.data

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds a `.proportion` file the user opened from outside the app until the settings screen is
 * ready to preview it. Consumed once: reopening settings later must not re-offer an old import.
 */
@Singleton
class PendingImport @Inject constructor() {

    sealed interface Pending {
        data class Content(val text: String) : Pending

        /** The file was opened but could not be read — say so rather than doing nothing. */
        data object Unreadable : Pending
    }

    private var pending: Pending? = null

    fun offer(content: String) {
        pending = Pending.Content(content)
    }

    fun offerUnreadable() {
        pending = Pending.Unreadable
    }

    fun consume(): Pending? = pending.also { pending = null }
}
