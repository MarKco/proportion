package com.ilsecondodasinistra.proportion.core.domain.dashboard

import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.Tag
import kotlin.random.Random
import org.junit.Test

class RecipePickerTest {

    private val picker = RecipePicker()
    private val dessert = Tag(id = "tag-dessert", key = "dessert", name = null, isBuiltIn = true)

    private fun recipe(id: String, tags: List<Tag> = emptyList()) = Recipe(
        id = id, title = id, servings = 4, steps = emptyList(),
        ingredients = emptyList(), tags = tags,
    )

    @Test
    fun `an empty library has nothing to suggest`() {
        assertThat(picker.pick(emptyList(), tagId = null, excluding = null, random = Random(1))).isNull()
    }

    @Test
    fun `a tag filter restricts the candidates`() {
        val picked = picker.pick(
            listOf(recipe("a"), recipe("b", tags = listOf(dessert))),
            tagId = "tag-dessert",
            excluding = null,
            random = Random(1),
        )

        assertThat(picked?.id).isEqualTo("b")
    }

    @Test
    fun `a tag nothing carries suggests nothing rather than falling back`() {
        val picked = picker.pick(listOf(recipe("a")), tagId = "tag-dessert", excluding = null, random = Random(1))

        assertThat(picked).isNull()
    }

    @Test
    fun `reshuffling never lands on the recipe already showing`() {
        val recipes = listOf(recipe("a"), recipe("b"), recipe("c"))

        repeat(50) { seed ->
            val picked = picker.pick(recipes, tagId = null, excluding = "a", random = Random(seed))
            assertThat(picked?.id).isNotEqualTo("a")
        }
    }

    @Test
    fun `with a single candidate, reshuffling returns it rather than nothing`() {
        val picked = picker.pick(listOf(recipe("a")), tagId = null, excluding = "a", random = Random(1))

        assertThat(picked?.id).isEqualTo("a")
    }

    @Test
    fun `the same seed picks the same recipe`() {
        val recipes = listOf(recipe("a"), recipe("b"), recipe("c"))

        assertThat(picker.pick(recipes, null, null, Random(7))?.id)
            .isEqualTo(picker.pick(recipes, null, null, Random(7))?.id)
    }
}
