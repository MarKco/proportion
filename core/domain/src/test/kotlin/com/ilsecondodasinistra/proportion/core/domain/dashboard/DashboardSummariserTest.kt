package com.ilsecondodasinistra.proportion.core.domain.dashboard

import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.Tag
import org.junit.Test

class DashboardSummariserTest {

    private val summariser = DashboardSummariser()

    private fun tag(key: String) = Tag(id = "tag-$key", key = key, name = null, isBuiltIn = true)

    private fun recipe(
        id: String,
        title: String = id,
        tags: List<Tag> = emptyList(),
        cookCount: Int = 0,
        lastCookedAt: Long? = null,
        favourite: Boolean = false,
        updatedAt: Long = 0L,
    ) = Recipe(
        id = id,
        title = title,
        servings = 4,
        steps = emptyList(),
        ingredients = emptyList(),
        tags = tags,
        isFavourite = favourite,
        cookCount = cookCount,
        lastCookedAt = lastCookedAt,
        updatedAt = updatedAt,
    )

    @Test
    fun `an empty library summarises to zeros, not to nulls the UI has to guard`() {
        val summary = summariser.summarise(emptyList())

        assertThat(summary.recipeCount).isEqualTo(0)
        assertThat(summary.totalCooks).isEqualTo(0)
        assertThat(summary.favouriteCount).isEqualTo(0)
        assertThat(summary.courseSlices).isEmpty()
        assertThat(summary.continueCooking).isNull()
        assertThat(summary.mostCooked).isEmpty()
        assertThat(summary.favourites).isEmpty()
    }

    @Test
    fun `counts are the plain totals`() {
        val summary = summariser.summarise(
            listOf(
                recipe("a", cookCount = 3, favourite = true),
                recipe("b", cookCount = 1),
                recipe("c"),
            ),
        )

        assertThat(summary.recipeCount).isEqualTo(3)
        assertThat(summary.totalCooks).isEqualTo(4)
        assertThat(summary.favouriteCount).isEqualTo(1)
    }

    @Test
    fun `slices follow the course order, and empty courses are dropped`() {
        val summary = summariser.summarise(
            listOf(
                recipe("a", tags = listOf(tag("dessert"))),
                recipe("b", tags = listOf(tag("first_course"))),
                recipe("c", tags = listOf(tag("first_course"))),
            ),
        )

        assertThat(summary.courseSlices.map { it.tagKey }).containsExactly("first_course", "dessert").inOrder()
        assertThat(summary.courseSlices.first().count).isEqualTo(2)
    }

    @Test
    fun `a recipe with two course tags counts once in each slice`() {
        val summary = summariser.summarise(
            listOf(recipe("a", tags = listOf(tag("main_course"), tag("side_dish")))),
        )

        assertThat(summary.courseSlices.map { it.tagKey })
            .containsExactly("main_course", "side_dish").inOrder()
        assertThat(summary.courseSlices.map { it.count }).containsExactly(1, 1)
    }

    @Test
    fun `oven is not a course, so an oven-only recipe counts as uncategorised`() {
        val summary = summariser.summarise(
            listOf(
                recipe("a", tags = listOf(tag("oven"))),
                recipe("b", tags = listOf(Tag("t1", null, "nonna", isBuiltIn = false))),
            ),
        )

        assertThat(summary.courseSlices).isEmpty()
        assertThat(summary.uncategorisedCount).isEqualTo(2)
    }

    @Test
    fun `continue cooking is the most recently cooked recipe`() {
        val summary = summariser.summarise(
            listOf(
                recipe("a", lastCookedAt = 100L, cookCount = 1),
                recipe("b", lastCookedAt = 900L, cookCount = 1),
                recipe("c"),
            ),
        )

        assertThat(summary.continueCooking?.id).isEqualTo("b")
    }

    @Test
    fun `nothing cooked yet means no continue card`() {
        assertThat(summariser.summarise(listOf(recipe("a"))).continueCooking).isNull()
    }

    @Test
    fun `most cooked ignores never-cooked recipes, sorts by count then title, and caps at topN`() {
        val summary = summariser.summarise(
            listOf(
                recipe("a", title = "Zuppa", cookCount = 5),
                recipe("b", title = "Brodo", cookCount = 5),
                recipe("c", title = "Pane", cookCount = 2),
                recipe("d", title = "Torta", cookCount = 1),
                recipe("e", title = "Mai", cookCount = 0),
            ),
            topN = 3,
        )

        assertThat(summary.mostCooked.map { it.title }).containsExactly("Brodo", "Zuppa", "Pane").inOrder()
    }

    @Test
    fun `favourites come back most recently updated first, capped at topN`() {
        val summary = summariser.summarise(
            listOf(
                recipe("a", favourite = true, updatedAt = 10L),
                recipe("b", favourite = true, updatedAt = 30L),
                recipe("c", favourite = false, updatedAt = 99L),
            ),
            topN = 3,
        )

        assertThat(summary.favourites.map { it.id }).containsExactly("b", "a").inOrder()
    }
}
