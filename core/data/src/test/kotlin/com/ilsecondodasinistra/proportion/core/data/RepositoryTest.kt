package com.ilsecondodasinistra.proportion.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.data.repository.IngredientRepositoryImpl
import com.ilsecondodasinistra.proportion.core.data.repository.RecipeRepositoryImpl
import com.ilsecondodasinistra.proportion.core.data.repository.ScaleVariantRepositoryImpl
import com.ilsecondodasinistra.proportion.core.data.repository.ShoppingRepositoryImpl
import com.ilsecondodasinistra.proportion.core.data.repository.TagRepositoryImpl
import com.ilsecondodasinistra.proportion.core.database.ProPortionDatabase
import com.ilsecondodasinistra.proportion.core.domain.BuiltInIngredientNamer
import com.ilsecondodasinistra.proportion.core.domain.TimeProvider
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeFilter
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaleConstraint
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaledLine
import com.ilsecondodasinistra.proportion.core.domain.unit.DefaultUnitConverter
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.RecipeIngredient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RepositoryTest {

    private lateinit var db: ProPortionDatabase
    private lateinit var recipes: RecipeRepositoryImpl
    private lateinit var ingredients: IngredientRepositoryImpl
    private lateinit var tags: TagRepositoryImpl
    private lateinit var variants: ScaleVariantRepositoryImpl
    private lateinit var shopping: ShoppingRepositoryImpl

    private val time = TimeProvider { 1_000L }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val namer = BuiltInIngredientNamer { key -> "[$key]" }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ProPortionDatabase::class.java,
        )
            .addCallback(ProPortionDatabase.seedCallback(ApplicationProvider.getApplicationContext()))
            .allowMainThreadQueries()
            .build()

        recipes = RecipeRepositoryImpl(db.recipeDao(), db.ingredientDao(), namer, time)
        ingredients = IngredientRepositoryImpl(db.ingredientDao(), namer)
        tags = TagRepositoryImpl(db.tagDao())
        variants = ScaleVariantRepositoryImpl(db.scaleVariantDao(), json, time)
        shopping = ShoppingRepositoryImpl(db.shoppingDao(), db.ingredientDao(), namer, DefaultUnitConverter())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun storeCake(): Recipe {
        val flour = ingredients.findOrCreate("Farina 00", MeasureUnit.GRAM)
        val eggs = ingredients.findOrCreate("Uova", MeasureUnit.EGG)
        val dessert = tags.observeAll().first().first { it.key == "dessert" }

        val recipe = Recipe(
            id = "r-1",
            title = "Torta di mele",
            servings = 4,
            steps = listOf("Step uno", "Step due"),
            ingredients = listOf(
                RecipeIngredient("l-1", flour, 0, 300.0, MeasureUnit.GRAM),
                RecipeIngredient("l-2", eggs, 1, 2.0, MeasureUnit.EGG),
            ),
            tags = listOf(dessert),
        )
        recipes.upsert(recipe)
        return recipe
    }

    @Test
    fun `a recipe survives a round trip through the database`() = runTest {
        val original = storeCake()

        val stored = recipes.observeRecipe("r-1").first()!!

        assertThat(stored.title).isEqualTo(original.title)
        assertThat(stored.servings).isEqualTo(4)
        assertThat(stored.steps).containsExactly("Step uno", "Step due").inOrder()
        assertThat(stored.ingredients.map { it.ingredient.name })
            .containsExactly("Farina 00", "Uova").inOrder()
        assertThat(stored.ingredients.first().quantity).isEqualTo(300.0)
        assertThat(stored.tags.single().key).isEqualTo("dessert")
    }

    @Test
    fun `upsert stamps created and updated timestamps`() = runTest {
        storeCake()

        val stored = recipes.observeRecipe("r-1").first()!!

        assertThat(stored.createdAt).isEqualTo(1_000L)
        assertThat(stored.updatedAt).isEqualTo(1_000L)
    }

    @Test
    fun `ingredient lookup is case and accent insensitive`() = runTest {
        val first = ingredients.findOrCreate("Basilico", MeasureUnit.LEAF)
        val again = ingredients.findOrCreate("  BASILICO ", MeasureUnit.LEAF)

        assertThat(again.id).isEqualTo(first.id)
        // Count only user-created rows: the catalogue also carries the seeded built-in ingredients.
        assertThat(ingredients.observeAll().first().count { !it.isBuiltIn }).isEqualTo(1)
    }

    @Test
    fun `an approximate line keeps a null quantity through the round trip`() = runTest {
        val salt = ingredients.findOrCreate("Sale", MeasureUnit.TO_TASTE)
        recipes.upsert(
            Recipe(
                id = "r-2",
                title = "Pasta",
                servings = 2,
                steps = emptyList(),
                ingredients = listOf(RecipeIngredient("l-9", salt, 0, null, MeasureUnit.TO_TASTE)),
                tags = emptyList(),
            ),
        )

        val stored = recipes.observeRecipe("r-2").first()!!

        assertThat(stored.ingredients.single().quantity).isNull()
        assertThat(stored.ingredients.single().unit).isEqualTo(MeasureUnit.TO_TASTE)
    }

    @Test
    fun `density survives the round trip even though v1 never sets it`() = runTest {
        val flour = ingredients.findOrCreate("Farina", MeasureUnit.GRAM)
        db.ingredientDao().upsertAll(listOf(flour.copy(densityGramsPerMl = 0.55).toEntity()))

        // Find this specific row rather than .single(): the catalogue also carries the seeded
        // built-in ingredients.
        val stored = ingredients.observeAll().first().single { it.id == flour.id }

        assertThat(stored.densityGramsPerMl).isEqualTo(0.55)
    }

    @Test
    fun `the filter finds a recipe by ingredient name`() = runTest {
        storeCake()

        val found = recipes.observeRecipes(RecipeFilter(query = "farina")).first()
        val missing = recipes.observeRecipes(RecipeFilter(query = "zafferano")).first()

        assertThat(found.map { it.id }).containsExactly("r-1")
        assertThat(missing).isEmpty()
    }

    @Test
    fun `the filter also finds a recipe by a built-in ingredient's resolved name`() = runTest {
        val flour = db.ingredientDao().findByKey("flour_00")!!.toDomain(namer)
        recipes.upsert(
            Recipe(
                id = "r-builtin-search",
                title = "Pane",
                servings = 1,
                steps = emptyList(),
                ingredients = listOf(RecipeIngredient("l-1", flour, 0, 500.0, MeasureUnit.GRAM)),
                tags = emptyList(),
            ),
        )

        // The seeded row's stored normalised_name is frozen to the raw key "flour_00" - this only
        // passes because the resolved name (here the fake namer's "[flour_00]") is what's searched.
        val found = recipes.observeRecipes(RecipeFilter(query = "flour_00")).first()

        assertThat(found.map { it.id }).containsExactly("r-builtin-search")
    }

    @Test
    fun `a saved variant keeps the constraint rather than the numbers`() = runTest {
        storeCake()

        variants.save("r-1", "Per 6", ScaleConstraint.ByServings(6.0), asDefault = true)
        val stored = variants.observeForRecipe("r-1").first().single()

        assertThat(stored.label).isEqualTo("Per 6")
        assertThat(stored.isDefault).isTrue()
        assertThat(variants.readConstraint(stored)).isEqualTo(ScaleConstraint.ByServings(6.0))
    }

    @Test
    fun `only one variant per recipe stays the default`() = runTest {
        storeCake()

        variants.save("r-1", "Per 6", ScaleConstraint.ByServings(6.0), asDefault = true)
        variants.save("r-1", "Per 8", ScaleConstraint.ByServings(8.0), asDefault = true)
        val stored = variants.observeForRecipe("r-1").first()

        assertThat(stored.filter { it.isDefault }.map { it.label }).containsExactly("Per 8")
    }

    @Test
    fun `shopping amounts merge when the units share a category`() = runTest {
        val recipe = storeCake()
        val flourId = recipe.ingredients.first().ingredient.id

        shopping.addScaled(
            listOf(scaledLine(flourId, "Farina 00", 300.0, MeasureUnit.GRAM)),
            recipeId = "r-1",
        )
        shopping.addScaled(
            listOf(scaledLine(flourId, "Farina 00", 0.2, MeasureUnit.KILOGRAM)),
            recipeId = "r-2",
        )

        val item = shopping.observeItems().first().single()

        assertThat(item.unit).isEqualTo(MeasureUnit.GRAM)
        assertThat(item.quantity).isWithin(1e-9).of(500.0)
        assertThat(item.sourceRecipeIds).containsExactly("r-1", "r-2")
    }

    @Test
    fun `shopping amounts stay separate when the units cannot be added`() = runTest {
        val recipe = storeCake()
        val eggsId = recipe.ingredients[1].ingredient.id

        shopping.addScaled(listOf(scaledLine(eggsId, "Uova", 2.0, MeasureUnit.EGG)), "r-1")
        shopping.addScaled(listOf(scaledLine(eggsId, "Uova", 100.0, MeasureUnit.GRAM)), "r-2")

        val items = shopping.observeItems().first()

        assertThat(items).hasSize(2)
        assertThat(items.map { it.unit }).containsExactly(MeasureUnit.EGG, MeasureUnit.GRAM)
    }

    @Test
    fun `deleting a recipe leaves its ingredients in the catalogue`() = runTest {
        storeCake()

        recipes.delete("r-1")

        assertThat(recipes.observeRecipes().first()).isEmpty()
        // Count only user-created rows: the catalogue also carries the seeded built-in ingredients.
        assertThat(ingredients.observeAll().first().count { !it.isBuiltIn }).isEqualTo(2)
        assertThat(ingredients.observeInUse().first()).isEmpty()
    }

    @Test
    fun `saving a recipe with a built-in ingredient does not rewrite its seeded placeholder row`() = runTest {
        val flour = db.ingredientDao().findByKey("flour_00")!!.toDomain(namer)
        recipes.upsert(
            Recipe(
                id = "r-builtin",
                title = "Pane",
                servings = 1,
                steps = emptyList(),
                ingredients = listOf(RecipeIngredient("l-1", flour, 0, 500.0, MeasureUnit.GRAM)),
                tags = emptyList(),
            ),
        )

        val stillSeeded = db.ingredientDao().findByKey("flour_00")!!
        assertThat(stillSeeded.name).isEqualTo("flour_00")
        assertThat(stillSeeded.normalisedName).isEqualTo("flour_00")
    }

    @Test
    fun `typing a built-in ingredient's raw key as free text resolves to the seeded row`() = runTest {
        val resolved = ingredients.findOrCreate("almond", MeasureUnit.GRAM)

        assertThat(resolved.isBuiltIn).isTrue()
        assertThat(resolved.id).isEqualTo(db.ingredientDao().findByKey("almond")!!.id)
        assertThat(ingredients.observeAll().first().count { !it.isBuiltIn }).isEqualTo(0)
    }

    private fun scaledLine(
        ingredientId: String,
        name: String,
        qty: Double,
        unit: MeasureUnit,
    ) = ScaledLine(
        lineId = "line-$name",
        ingredientId = ingredientId,
        ingredientName = name,
        originalQty = qty,
        originalUnit = unit,
        scaledQty = qty,
        displayUnit = unit,
        displayText = "$qty $unit",
        isScaled = true,
    )
}
