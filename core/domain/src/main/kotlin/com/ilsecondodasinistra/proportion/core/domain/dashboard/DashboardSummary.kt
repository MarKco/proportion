package com.ilsecondodasinistra.proportion.core.domain.dashboard

import com.ilsecondodasinistra.proportion.core.model.Recipe

/** One arc of the donut: how many recipes carry this course tag. */
data class CourseSlice(val tagId: String, val tagKey: String, val count: Int)

/**
 * Everything the dashboard draws, computed once. The screen renders this and calculates nothing:
 * that is what keeps the four cards consistent with each other on every recomposition.
 */
data class DashboardSummary(
    val recipeCount: Int = 0,
    val totalCooks: Int = 0,
    val favouriteCount: Int = 0,
    val courseSlices: List<CourseSlice> = emptyList(),
    val uncategorisedCount: Int = 0,
    val continueCooking: Recipe? = null,
    val mostCooked: List<Recipe> = emptyList(),
    val favourites: List<Recipe> = emptyList(),
) {
    companion object {
        /** The built-in tags that describe a course. `oven` is a technique, not a course. */
        val COURSE_KEYS = listOf(
            "appetizer", "first_course", "main_course",
            "side_dish", "dessert", "bread_and_leavened", "preserves", "drinks",
        )
    }
}
