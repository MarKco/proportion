package com.ilsecondodasinistra.proportion.core.domain.dashboard

import com.ilsecondodasinistra.proportion.core.model.Recipe

class DashboardSummariser {

    fun summarise(recipes: List<Recipe>, topN: Int = DEFAULT_TOP_N): DashboardSummary {
        if (recipes.isEmpty()) return DashboardSummary()

        val courseKeys = DashboardSummary.COURSE_KEYS
        val slices = courseKeys.mapNotNull { key ->
            val tagged = recipes.filter { recipe ->
                recipe.tags.any { it.isBuiltIn && it.key == key }
            }
            val tagId = tagged.firstNotNullOfOrNull { recipe ->
                recipe.tags.firstOrNull { it.isBuiltIn && it.key == key }?.id
            }
            if (tagged.isEmpty() || tagId == null) null
            else CourseSlice(tagId = tagId, tagKey = key, count = tagged.size)
        }

        return DashboardSummary(
            recipeCount = recipes.size,
            totalCooks = recipes.sumOf { it.cookCount },
            favouriteCount = recipes.count { it.isFavourite },
            courseSlices = slices,
            uncategorisedCount = recipes.count { recipe ->
                recipe.tags.none { it.isBuiltIn && it.key in courseKeys }
            },
            continueCooking = recipes
                .filter { it.lastCookedAt != null }
                .maxByOrNull { it.lastCookedAt ?: 0L },
            mostCooked = recipes
                .filter { it.cookCount > 0 }
                .sortedWith(compareByDescending<Recipe> { it.cookCount }.thenBy { it.title })
                .take(topN),
            favourites = recipes
                .filter { it.isFavourite }
                .sortedByDescending { it.updatedAt }
                .take(topN),
        )
    }

    private companion object {
        const val DEFAULT_TOP_N = 3
    }
}
