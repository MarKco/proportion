package com.ilsecondodasinistra.proportion.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.data.repository.IngredientRepositoryImpl
import com.ilsecondodasinistra.proportion.core.data.repository.RecipeRepositoryImpl
import com.ilsecondodasinistra.proportion.core.data.repository.TagRepositoryImpl
import com.ilsecondodasinistra.proportion.core.data.repository.TransferRepositoryImpl
import com.ilsecondodasinistra.proportion.core.database.ProPortionDatabase
import com.ilsecondodasinistra.proportion.core.domain.TimeProvider
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.RecipeIngredient
import com.ilsecondodasinistra.proportion.core.transfer.DecodeFailure
import com.ilsecondodasinistra.proportion.core.transfer.ImportMode
import com.ilsecondodasinistra.proportion.core.transfer.ImportOutcome
import com.ilsecondodasinistra.proportion.core.transfer.ImportPreview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransferRepositoryTest {

    private lateinit var db: ProPortionDatabase
    private lateinit var recipes: RecipeRepositoryImpl
    private lateinit var ingredients: IngredientRepositoryImpl
    private lateinit var transfer: TransferRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ProPortionDatabase::class.java,
        )
            .addCallback(ProPortionDatabase.seedCallback())
            .allowMainThreadQueries()
            .build()

        val time = TimeProvider { 1_000L }
        recipes = RecipeRepositoryImpl(db.recipeDao(), db.ingredientDao(), time)
        ingredients = IngredientRepositoryImpl(db.ingredientDao())
        transfer = TransferRepositoryImpl(
            recipeRepository = recipes,
            ingredientRepository = ingredients,
            tagRepository = TagRepositoryImpl(db.tagDao()),
            recipeDao = db.recipeDao(),
            tagDao = db.tagDao(),
            time = time,
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun storeCake(id: String = "r-cake", title: String = "Torta di mele"): Recipe {
        val flour = ingredients.findOrCreate("Farina 00", MeasureUnit.GRAM)
        val eggs = ingredients.findOrCreate("Uova", MeasureUnit.EGG)
        val dessert = db.tagDao().findByKey("dessert")!!.toDomain()

        val recipe = Recipe(
            id = id,
            title = title,
            servings = 4,
            steps = listOf("Sbatti.", "Inforna."),
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
    fun `a library exports and imports back into an empty database`() = runTest {
        storeCake()
        val text = transfer.exportAll()

        db.recipeDao().deleteAllRecipes()
        assertThat(recipes.observeRecipes().first()).isEmpty()

        val outcome = transfer.import(text, ImportMode.MERGE) as ImportOutcome.Imported

        assertThat(outcome.added).isEqualTo(1)
        val restored = recipes.observeRecipes().first().single()
        assertThat(restored.title).isEqualTo("Torta di mele")
        assertThat(restored.ingredients.map { it.ingredient.name })
            .containsExactly("Farina 00", "Uova").inOrder()
        assertThat(restored.ingredients.first().quantity).isEqualTo(300.0)
        assertThat(restored.steps).containsExactly("Sbatti.", "Inforna.").inOrder()
        assertThat(restored.tags.single().key).isEqualTo("dessert")
    }

    @Test
    fun `a preview counts without writing anything`() = runTest {
        storeCake()
        val text = transfer.exportAll()
        db.recipeDao().deleteAllRecipes()

        val preview = transfer.preview(text) as ImportPreview.Ready

        assertThat(preview.total).isEqualTo(1)
        assertThat(preview.alreadyPresent).isEqualTo(0)
        assertThat(recipes.observeRecipes().first()).isEmpty()
    }

    @Test
    fun `a preview reports how many are already here`() = runTest {
        storeCake()
        val text = transfer.exportAll()

        val preview = transfer.preview(text) as ImportPreview.Ready

        assertThat(preview.total).isEqualTo(1)
        assertThat(preview.alreadyPresent).isEqualTo(1)
    }

    @Test
    fun `merge keeps what is here and skips duplicates`() = runTest {
        storeCake()
        val text = transfer.exportAll()
        storeCake(id = "r-other", title = "Risotto")

        val outcome = transfer.import(text, ImportMode.MERGE) as ImportOutcome.Imported

        assertThat(outcome.added).isEqualTo(0)
        assertThat(outcome.skipped).isEqualTo(1)
        assertThat(recipes.observeRecipes().first().map { it.id })
            .containsExactly("r-cake", "r-other")
    }

    @Test
    fun `merge adds recipes that are not here yet`() = runTest {
        storeCake()
        val text = transfer.exportAll()
        db.recipeDao().deleteAllRecipes()
        storeCake(id = "r-other", title = "Risotto")

        val outcome = transfer.import(text, ImportMode.MERGE) as ImportOutcome.Imported

        assertThat(outcome.added).isEqualTo(1)
        assertThat(recipes.observeRecipes().first().map { it.title })
            .containsExactly("Torta di mele", "Risotto")
    }

    @Test
    fun `replace all empties the library first`() = runTest {
        storeCake()
        val text = transfer.exportAll()
        storeCake(id = "r-other", title = "Risotto")

        val outcome = transfer.import(text, ImportMode.REPLACE_ALL) as ImportOutcome.Imported

        assertThat(outcome.replacedLibrary).isTrue()
        assertThat(recipes.observeRecipes().first().map { it.id }).containsExactly("r-cake")
    }

    @Test
    fun `an incoming ingredient reuses the catalogue row`() = runTest {
        storeCake()
        val text = transfer.exportAll()
        db.recipeDao().deleteAllRecipes()

        transfer.import(text, ImportMode.MERGE)

        // Still two ingredients, not four: the names resolved to the rows already there.
        assertThat(ingredients.observeAll().first().map { it.name })
            .containsExactly("Farina 00", "Uova")
    }

    @Test
    fun `an incoming user tag is created and a built in tag binds to the seeded one`() = runTest {
        val recipe = storeCake()
        val userTag = TagRepositoryImpl(db.tagDao()).findOrCreateUserTag("merenda")
        recipes.upsert(recipe.copy(tags = recipe.tags + userTag))

        val text = transfer.exportAll()
        db.recipeDao().deleteAllRecipes()
        db.tagDao().deleteUserTag(userTag.id)

        transfer.import(text, ImportMode.MERGE)

        val restored = recipes.observeRecipes().first().single()
        assertThat(restored.tags.mapNotNull { it.key }).contains("dessert")
        assertThat(restored.tags.mapNotNull { it.name }).contains("merenda")
    }

    @Test
    fun `a malformed file fails and leaves the library untouched`() = runTest {
        storeCake()

        val outcome = transfer.import("{ not really json", ImportMode.REPLACE_ALL)

        assertThat((outcome as ImportOutcome.Failed).reason)
            .isInstanceOf(DecodeFailure.Malformed::class.java)
        assertThat(recipes.observeRecipes().first()).hasSize(1)
    }

    @Test
    fun `a file from a future version is refused before anything is deleted`() = runTest {
        storeCake()
        val text = transfer.exportAll().replace("\"version\": 1", "\"version\": 42")

        val outcome = transfer.import(text, ImportMode.REPLACE_ALL)

        assertThat((outcome as ImportOutcome.Failed).reason)
            .isInstanceOf(DecodeFailure.FutureVersion::class.java)
        assertThat(recipes.observeRecipes().first()).hasSize(1)
    }

    @Test
    fun `exporting a single recipe carries only that one`() = runTest {
        storeCake()
        storeCake(id = "r-other", title = "Risotto")

        val text = transfer.exportRecipe("r-other")!!

        assertThat(text).contains("Risotto")
        assertThat(text).doesNotContain("Torta di mele")
    }

    @Test
    fun `exporting a recipe that no longer exists returns nothing`() = runTest {
        assertThat(transfer.exportRecipe("nope")).isNull()
    }
}
