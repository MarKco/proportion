package com.ilsecondodasinistra.proportion.core.model

/**
 * A recipe tag.
 *
 * Built-in tags carry a stable [key] resolved through `strings.xml`, so they follow the app
 * language. Tags created by the user carry a literal [name] and are never translated. Exactly one
 * of the two is set.
 */
data class Tag(
    val id: String,
    val key: String?,
    val name: String?,
    val isBuiltIn: Boolean,
    val colorIndex: Int = 0,
) {
    init {
        require((key == null) != (name == null)) {
            "a tag carries either a built-in key or a literal name, never both and never neither"
        }
    }

    companion object {
        /** Keys seeded on first run. `OVEN` also drives the baking advisory. */
        val BUILT_IN_KEYS = listOf(
            "appetizer",
            "first_course",
            "main_course",
            "side_dish",
            "dessert",
            "bread_and_leavened",
            "preserves",
            "drinks",
            "oven",
        )
    }
}
