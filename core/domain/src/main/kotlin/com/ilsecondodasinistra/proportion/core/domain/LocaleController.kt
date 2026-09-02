package com.ilsecondodasinistra.proportion.core.domain

/**
 * The app's own language, independent of the device's. `null` means "follow the system".
 *
 * A BCP-47 tag ("it", "en"), not a [com.ilsecondodasinistra.proportion.core.model.ThemeMode]-style
 * enum: the set of languages the app ships is a build-time fact (`values-it/`, `values-en/`), not a
 * domain concept worth its own type.
 */
interface LocaleController {
    /** The tag currently in effect, or `null` if the app is following the system language. */
    fun currentTag(): String?

    /** `null` reverts to following the system language. */
    fun setTag(tag: String?)
}
