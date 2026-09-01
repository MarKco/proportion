package com.ilsecondodasinistra.proportion.core.transfer

/** What to do with a file whose recipes may already exist here. */
enum class ImportMode {
    /** Keep what is here, add what is new, skip ids already present. */
    MERGE,

    /** Empty the library first. Destructive, so the UI asks twice. */
    REPLACE_ALL,
}

sealed interface ImportPreview {
    /** Counts only: nothing has been written yet. */
    data class Ready(val total: Int, val alreadyPresent: Int) : ImportPreview
    data class Invalid(val reason: DecodeFailure) : ImportPreview
}

sealed interface ImportOutcome {
    data class Imported(
        val added: Int,
        val skipped: Int,
        val replacedLibrary: Boolean,
    ) : ImportOutcome

    data class Failed(val reason: DecodeFailure) : ImportOutcome
}
