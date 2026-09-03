package com.ilsecondodasinistra.proportion.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.database.dao.IngredientDao
import com.ilsecondodasinistra.proportion.core.database.dao.RecipeDao
import com.ilsecondodasinistra.proportion.core.database.dao.TagDao
import com.ilsecondodasinistra.proportion.core.database.dao.observeFiltered
import com.ilsecondodasinistra.proportion.core.database.entity.IngredientEntity
import com.ilsecondodasinistra.proportion.core.database.entity.RecipeEntity
import com.ilsecondodasinistra.proportion.core.database.entity.RecipeIngredientEntity
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecipeDaoTest {

    private lateinit var db: ProPortionDatabase
    private lateinit var recipeDao: RecipeDao
    private lateinit var ingredientDao: IngredientDao
    private lateinit var tagDao: TagDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ProPortionDatabase::class.java,
        )
            .addCallback(ProPortionDatabase.seedCallback(ApplicationProvider.getApplicationContext()))
            .allowMainThreadQueries()
            .build()
        recipeDao = db.recipeDao()
        ingredientDao = db.ingredientDao()
        tagDao = db.tagDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedCake(): String {
        val flour = IngredientEntity(
            "ing-flour", key = null, "Farina 00", "farina 00", isBuiltIn = false, defaultUnit = MeasureUnit.GRAM,
        )
        val eggs = IngredientEntity(
            "ing-eggs", key = null, "Uova", "uova", isBuiltIn = false, defaultUnit = MeasureUnit.EGG,
        )
        ingredientDao.upsertAll(listOf(flour, eggs))

        val dessertTagId = tagDao.observeAll().first().first { it.key == "dessert" }.id
        recipeDao.upsertRecipe(
            recipe = RecipeEntity(
                id = "r-cake",
                title = "Torta di mele",
                servings = 4,
                steps = listOf("Sbatti le uova.", "Inforna."),
                notes = "Ricetta della nonna",
                updatedAt = 200L,
            ),
            lines = listOf(
                RecipeIngredientEntity("l-1", "r-cake", "ing-flour", 0, 300.0, MeasureUnit.GRAM),
                RecipeIngredientEntity("l-2", "r-cake", "ing-eggs", 1, 2.0, MeasureUnit.EGG),
            ),
            tagIds = listOf(dessertTagId),
        )
        return dessertTagId
    }

    private suspend fun seedRisotto() {
        val rice = IngredientEntity(
            "ing-rice", key = null, "Riso", "riso", isBuiltIn = false, defaultUnit = MeasureUnit.GRAM,
        )
        ingredientDao.upsertAll(listOf(rice))

        val firstCourseId = tagDao.observeAll().first().first { it.key == "first_course" }.id
        recipeDao.upsertRecipe(
            recipe = RecipeEntity(
                id = "r-risotto",
                title = "Risotto",
                servings = 2,
                steps = listOf("Tosta il riso."),
                updatedAt = 100L,
            ),
            lines = listOf(RecipeIngredientEntity("l-3", "r-risotto", "ing-rice", 0, 320.0, MeasureUnit.GRAM)),
            tagIds = listOf(firstCourseId),
        )
    }

    @Test
    fun `inserting a recipe with lines and tags reads it back whole`() = runTest {
        seedCake()

        val stored = recipeDao.observeAll().first().single()

        assertThat(stored.recipe.title).isEqualTo("Torta di mele")
        assertThat(stored.recipe.steps).containsExactly("Sbatti le uova.", "Inforna.").inOrder()
        assertThat(stored.lines.map { it.ingredient.name }).containsExactly("Farina 00", "Uova")
        assertThat(stored.lines.first { it.line.id == "l-2" }.line.unit).isEqualTo(MeasureUnit.EGG)
        assertThat(stored.tags.map { it.key }).containsExactly("dessert")
    }

    @Test
    fun `free text search matches the title`() = runTest {
        seedCake()
        seedRisotto()

        val found = recipeDao.observeFiltered(query = "torta").first()

        assertThat(found.map { it.recipe.id }).containsExactly("r-cake")
    }

    @Test
    fun `free text search matches a built-in ingredient via the resolved-id list`() = runTest {
        seedCake()
        seedRisotto()

        // A query matching nothing by title/notes/normalised_name here - this proves
        // matchingBuiltInIds alone can surface a recipe, the mechanism RecipeRepositoryImpl relies
        // on for built-in ingredients whose stored normalised_name is frozen to their raw key.
        val found = recipeDao.filtered(
            query = "flour_00",
            tagIds = emptyList(),
            tagCount = 0,
            ingredientIds = emptyList(),
            ingredientCount = 0,
            sort = "RECENT",
            matchingBuiltInIds = listOf("ing-flour"),
        ).first()

        assertThat(found.map { it.recipe.id }).containsExactly("r-cake")
    }

    @Test
    fun `free text search matches an ingredient name`() = runTest {
        seedCake()
        seedRisotto()

        val found = recipeDao.observeFiltered(query = "riso").first()

        assertThat(found.map { it.recipe.id }).containsExactly("r-risotto")
    }

    @Test
    fun `free text search matches the notes`() = runTest {
        seedCake()
        seedRisotto()

        val found = recipeDao.observeFiltered(query = "nonna").first()

        assertThat(found.map { it.recipe.id }).containsExactly("r-cake")
    }

    @Test
    fun `a recipe matches an ingredient filter only when it contains every selected ingredient`() = runTest {
        seedCake()
        seedRisotto()

        val bothPresent = recipeDao.observeFiltered(ingredientIds = listOf("ing-flour", "ing-eggs")).first()
        val mixedSelection = recipeDao.observeFiltered(ingredientIds = listOf("ing-flour", "ing-rice")).first()

        assertThat(bothPresent.map { it.recipe.id }).containsExactly("r-cake")
        assertThat(mixedSelection).isEmpty()
    }

    @Test
    fun `tag filter and text filter combine with AND`() = runTest {
        val dessertTagId = seedCake()
        seedRisotto()

        val matching = recipeDao.observeFiltered(query = "torta", tagIds = listOf(dessertTagId)).first()
        val conflicting = recipeDao.observeFiltered(query = "risotto", tagIds = listOf(dessertTagId)).first()

        assertThat(matching.map { it.recipe.id }).containsExactly("r-cake")
        assertThat(conflicting).isEmpty()
    }

    @Test
    fun `several tags widen the selection`() = runTest {
        val dessertTagId = seedCake()
        seedRisotto()
        val firstCourseId = tagDao.observeAll().first().first { it.key == "first_course" }.id

        val found = recipeDao.observeFiltered(tagIds = listOf(dessertTagId, firstCourseId)).first()

        assertThat(found.map { it.recipe.id }).containsExactly("r-cake", "r-risotto")
    }

    @Test
    fun `deleting a recipe cascades to its lines but keeps the ingredients`() = runTest {
        seedCake()

        recipeDao.deleteRecipe("r-cake")

        assertThat(recipeDao.observeAll().first()).isEmpty()
        assertThat(recipeDao.countLines()).isEqualTo(0)
        // observeAll() now also carries the seeded built-in catalogue (Task 6); filter down to the
        // user-created ingredients this test actually cares about surviving the cascade.
        assertThat(ingredientDao.observeAll().first().filterNot { it.isBuiltIn }.map { it.id })
            .containsExactly("ing-flour", "ing-eggs")
    }

    @Test
    fun `the ingredient filter list only offers ingredients actually used`() = runTest {
        seedCake()
        ingredientDao.upsertAll(
            listOf(
                IngredientEntity(
                    "ing-unused",
                    key = null,
                    "Zafferano",
                    "zafferano",
                    isBuiltIn = false,
                    defaultUnit = MeasureUnit.SACHET,
                ),
            ),
        )

        val inUse = ingredientDao.observeInUse().first().map { it.id }

        assertThat(inUse).containsExactly("ing-flour", "ing-eggs")
    }

    @Test
    fun `built in tags are seeded once and include the oven tag`() = runTest {
        val tags = tagDao.observeAll().first()

        assertThat(tags.filter { it.isBuiltIn }.map { it.key })
            .containsExactly(
                "appetizer", "first_course", "main_course", "side_dish", "dessert",
                "bread_and_leavened", "preserves", "drinks", "oven",
            )
        assertThat(tags.count { it.key == "oven" }).isEqualTo(1)
    }

    @Test
    fun `recipes come back most recently updated first`() = runTest {
        seedCake()
        seedRisotto()

        val all = recipeDao.observeAll().first()

        assertThat(all.map { it.recipe.id }).containsExactly("r-cake", "r-risotto").inOrder()
    }
}
