package com.ilsecondodasinistra.proportion.core.domain

import java.text.Normalizer
import java.util.Locale

/**
 * Folds an ingredient name to the key used for lookup, filtering and de-duplication.
 *
 * Without this, "Farina 00", "farina 00" and "FARINA 00 " would be three different ingredients and
 * the ingredient filter would be useless.
 */
object IngredientNames {

    fun normalise(name: String): String =
        Normalizer.normalize(name.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(ACCENTS, "")
            .replace(WHITESPACE, " ")

    private val ACCENTS = Regex("\\p{Mn}+")
    private val WHITESPACE = Regex("\\s+")
}
