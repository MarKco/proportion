package com.ilsecondodasinistra.proportion.core.domain

/** Resolves a built-in ingredient's [key] to its name in the current app language. */
fun interface BuiltInIngredientNamer {
    fun name(key: String): String
}
