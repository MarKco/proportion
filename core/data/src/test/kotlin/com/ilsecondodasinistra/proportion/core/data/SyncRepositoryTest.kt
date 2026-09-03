package com.ilsecondodasinistra.proportion.core.data

import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.data.repository.IngredientRepositoryImpl
import com.ilsecondodasinistra.proportion.core.data.repository.RecipeRepositoryImpl
import com.ilsecondodasinistra.proportion.core.data.repository.SyncRepositoryImpl
import com.ilsecondodasinistra.proportion.core.data.repository.TagRepositoryImpl
import com.ilsecondodasinistra.proportion.core.data.repository.TransferRepositoryImpl
import com.ilsecondodasinistra.proportion.core.database.ProPortionDatabase
import com.ilsecondodasinistra.proportion.core.datastore.SyncLogDataSource
import com.ilsecondodasinistra.proportion.core.domain.BuiltInIngredientNamer
import com.ilsecondodasinistra.proportion.core.domain.TimeProvider
import com.ilsecondodasinistra.proportion.core.domain.repository.PreferencesRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.SyncRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.SyncResult
import com.ilsecondodasinistra.proportion.core.model.AppTheme
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.RecipeIngredient
import com.ilsecondodasinistra.proportion.core.model.SyncLogEntry
import com.ilsecondodasinistra.proportion.core.model.ThemeMode
import com.ilsecondodasinistra.proportion.core.model.UserPreferences
import java.nio.file.Files
import javax.inject.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** No auto-export from a plain repository write — this suite drives [SyncRepositoryImpl] directly. */
private val noopSync = Provider<SyncRepository> {
    object : SyncRepository {
        override suspend fun exportRecipe(recipeId: String) = Unit
        override suspend fun exportIngredient(ingredientId: String) = Unit
        override suspend fun exportTag(tagId: String) = Unit
        override suspend fun syncNow(): SyncResult = SyncResult(0, 0, 0, 0)
        override fun observeLog(): Flow<List<SyncLogEntry>> = flowOf(emptyList())
    }
}

private class FakePreferencesRepository(initial: UserPreferences) : PreferencesRepository {
    val state = MutableStateFlow(initial)
    override fun observePreferences(): Flow<UserPreferences> = state
    override suspend fun setThemeMode(mode: ThemeMode) = Unit
    override suspend fun setDynamicColour(enabled: Boolean) = Unit
    override suspend fun setAppTheme(theme: AppTheme) = Unit
    override suspend fun setSyncEnabled(enabled: Boolean) {
        state.value = state.value.copy(syncEnabled = enabled)
    }
    override suspend fun setSyncFolderUri(uri: String?) {
        state.value = state.value.copy(syncFolderUri = uri)
    }
    override suspend fun setSyncIntervalHours(hours: Int) {
        state.value = state.value.copy(syncIntervalHours = hours)
    }
}

@RunWith(RobolectricTestRunner::class)
class SyncRepositoryTest {

    private lateinit var db: ProPortionDatabase
    private lateinit var recipes: RecipeRepositoryImpl
    private lateinit var ingredients: IngredientRepositoryImpl
    private lateinit var tags: TagRepositoryImpl
    private lateinit var transfer: TransferRepositoryImpl
    private lateinit var sync: SyncRepositoryImpl
    private lateinit var preferences: FakePreferencesRepository
    private lateinit var syncLog: SyncLogDataSource
    private lateinit var folderUri: String

    private var now = 1_000L
    private val time = TimeProvider { now }
    private val namer = BuiltInIngredientNamer { key -> "[$key]" }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, ProPortionDatabase::class.java)
            .addCallback(ProPortionDatabase.seedCallback(context))
            .allowMainThreadQueries()
            .build()

        val tempDir = Files.createTempDirectory("sync-test").toFile()
        folderUri = Uri.fromFile(tempDir).toString()
        preferences = FakePreferencesRepository(UserPreferences(syncEnabled = true, syncFolderUri = folderUri))

        val logFile = context.filesDir.resolve("sync-log-${System.nanoTime()}.preferences_pb")
        syncLog = SyncLogDataSource(PreferenceDataStoreFactory.create { logFile })

        recipes = RecipeRepositoryImpl(db.recipeDao(), db.ingredientDao(), namer, time, noopSync)
        ingredients = IngredientRepositoryImpl(db.ingredientDao(), namer, time, noopSync)
        tags = TagRepositoryImpl(db.tagDao(), time, noopSync)
        transfer = TransferRepositoryImpl(
            recipeRepository = recipes,
            ingredientRepository = ingredients,
            ingredientDao = db.ingredientDao(),
            namer = namer,
            tagRepository = tags,
            recipeDao = db.recipeDao(),
            tagDao = db.tagDao(),
            time = time,
        )
        sync = SyncRepositoryImpl(
            context = context,
            recipeDao = db.recipeDao(),
            ingredientDao = db.ingredientDao(),
            tagDao = db.tagDao(),
            syncCacheDao = db.syncCacheDao(),
            transferRepository = transfer,
            preferencesRepository = preferences,
            syncLog = syncLog,
            time = time,
        )
    }

    @After
    fun tearDown() = db.close()

    private fun folderFile(name: String) = java.io.File(Uri.parse(folderUri).path, name)

    private suspend fun storeCake(id: String = "r-cake"): Recipe {
        val flour = ingredients.findOrCreate("Farina 00", MeasureUnit.GRAM)
        val recipe = Recipe(
            id = id,
            title = "Torta",
            servings = 4,
            steps = emptyList(),
            ingredients = listOf(RecipeIngredient("l-1", flour, 0, 300.0, MeasureUnit.GRAM)),
            tags = emptyList(),
        )
        recipes.upsert(recipe)
        return recipe
    }

    @Test
    fun `sync disabled does nothing and writes nothing`() = runTest {
        preferences.setSyncEnabled(false)
        storeCake()

        val result = sync.syncNow()

        assertThat(result).isEqualTo(SyncResult(0, 0, 0, 0))
        assertThat(folderFile("recipe-r-cake.proportion").exists()).isFalse()
    }

    @Test
    fun `syncNow pushes every local recipe to the folder`() = runTest {
        storeCake()

        val result = sync.syncNow()

        assertThat(result.exported).isAtLeast(1)
        assertThat(folderFile("recipe-r-cake.proportion").exists()).isTrue()
        assertThat(folderFile("recipe-r-cake.proportion").readText()).contains("Torta")
    }

    @Test
    fun `a second syncNow with nothing changed does not rewrite the recipe file`() = runTest {
        storeCake()
        sync.syncNow()
        val writtenAt = folderFile("recipe-r-cake.proportion").lastModified()

        val result = sync.syncNow()

        assertThat(result.exported).isEqualTo(0)
        assertThat(folderFile("recipe-r-cake.proportion").lastModified()).isEqualTo(writtenAt)
    }

    @Test
    fun `editing a recipe after a sync still re-exports it`() = runTest {
        storeCake()
        sync.syncNow()

        now = 2_000L
        recipes.upsert(recipes.observeRecipe("r-cake").first()!!.copy(title = "Torta aggiornata"))
        val result = sync.syncNow()

        assertThat(result.exported).isAtLeast(1)
        assertThat(folderFile("recipe-r-cake.proportion").readText()).contains("Torta aggiornata")
    }

    @Test
    fun `an unchanged file is not re-read once the pull cache has warmed up`() = runTest {
        storeCake()
        sync.syncNow() // run 1: first push creates the file.
        // run 2: pull sees its own freshly-created file for the first time (cache miss, harmless
        // Skip since it's this device's own unchanged content) and caches its mtime; push then
        // skips it (unchanged since run 1) so the file stops moving from here on.
        sync.syncNow()
        val file = folderFile("recipe-r-cake.proportion")
        val stableMtime = file.lastModified()
        val logCountBeforeTamper = syncLog.entries.first().size

        // Corrupt the file without touching its mtime: a re-read would log a parse error.
        file.writeText("not valid json")
        file.setLastModified(stableMtime)

        sync.syncNow() // run 3: mtime unchanged from the cache set in run 2 -> must not re-read it.

        val newEntries = syncLog.entries.first().drop(logCountBeforeTamper)
        assertThat(newEntries.none { it.isError }).isTrue()
    }

    @Test
    fun `exportRecipe writes a single file immediately`() = runTest {
        storeCake()

        sync.exportRecipe("r-cake")

        assertThat(folderFile("recipe-r-cake.proportion").exists()).isTrue()
    }

    @Test
    fun `a recipe file with no local match is inserted`() = runTest {
        val other = storeCakeOnAnotherDevice(id = "r-remote", updatedAt = 500L)
        folderFile("recipe-r-remote.proportion").writeText(other)

        val result = sync.syncNow()

        assertThat(result.recipesImported).isEqualTo(1)
        assertThat(recipes.observeRecipe("r-remote").first()?.title).isEqualTo("Ricetta remota")
    }

    @Test
    fun `a more recent remote recipe overwrites the local one and keeps its own timestamp`() = runTest {
        now = 100L
        storeCake()
        val remoteText = storeCakeOnAnotherDevice(id = "r-cake", updatedAt = 999L, title = "Torta aggiornata")
        folderFile("recipe-r-cake.proportion").writeText(remoteText)

        val result = sync.syncNow()

        assertThat(result.recipesImported).isEqualTo(1)
        val stored = recipes.observeRecipe("r-cake").first()!!
        assertThat(stored.title).isEqualTo("Torta aggiornata")
        assertThat(stored.updatedAt).isEqualTo(999L)
    }

    @Test
    fun `an older remote recipe is skipped, the local one survives untouched`() = runTest {
        now = 999L
        storeCake()
        val remoteText = storeCakeOnAnotherDevice(id = "r-cake", updatedAt = 100L, title = "Versione vecchia")
        folderFile("recipe-r-cake.proportion").writeText(remoteText)

        val result = sync.syncNow()

        assertThat(result.recipesImported).isEqualTo(0)
        assertThat(recipes.observeRecipe("r-cake").first()!!.title).isEqualTo("Torta")
    }

    @Test
    fun `a more recent remote tombstone deletes the local recipe`() = runTest {
        now = 100L
        storeCake()
        val remoteText = storeCakeOnAnotherDevice(id = "r-cake", updatedAt = 999L, deletedAt = 999L)
        folderFile("recipe-r-cake.proportion").writeText(remoteText)

        val result = sync.syncNow()

        assertThat(result.recipesDeleted).isEqualTo(1)
        assertThat(recipes.observeRecipe("r-cake").first()).isNull()
    }

    @Test
    fun `a malformed recipe file is logged as an error, not a crash`() = runTest {
        folderFile("recipe-garbage.proportion").writeText("not json at all")

        sync.syncNow()

        val log = sync.observeLog().first()
        assertThat(log.any { it.isError }).isTrue()
    }

    @Test
    fun `an ingredient created independently on two devices merges onto the local id`() = runTest {
        val local = ingredients.findOrCreate("Farina integrale", MeasureUnit.GRAM)
        val entry = """
            {"id":"remote-id","name":"Farina integrale","normalisedName":"farina integrale",
             "defaultUnit":"GRAM","densityGramsPerMl":0.6,"updatedAt":2000}
        """.trimIndent()
        folderFile("ingredient-remote-id.proportion").writeText(entry)

        val result = sync.syncNow()

        assertThat(result.catalogueImported).isAtLeast(1)
        val stored = ingredients.observeAll().first().single { it.normalisedName == "farina integrale" }
        assertThat(stored.id).isEqualTo(local.id)
        assertThat(stored.densityGramsPerMl).isEqualTo(0.6)
    }

    @Test
    fun `an old tombstone is hard-deleted and its file removed on the next sync`() = runTest {
        now = 100L
        storeCake()
        recipes.delete("r-cake")
        sync.exportRecipe("r-cake")
        assertThat(folderFile("recipe-r-cake.proportion").exists()).isTrue()

        now = 100L + 31L * 24 * 60 * 60 * 1000 // 31 days later, past the 30-day grace window
        sync.syncNow()

        assertThat(folderFile("recipe-r-cake.proportion").exists()).isFalse()
        assertThat(db.syncCacheDao().exportedUpdatedAt("r-cake")).isNull()
        assertThat(db.syncCacheDao().seenMtime("recipe-r-cake.proportion")).isNull()
    }

    /** A `.proportion` recipe file as if written by a second device — not this one's repositories. */
    private fun storeCakeOnAnotherDevice(
        id: String,
        updatedAt: Long,
        title: String = "Ricetta remota",
        deletedAt: Long? = null,
    ): String = """
        {"format":"proportion","version":1,"recipes":[{
            "id":"$id","title":"$title","updatedAt":$updatedAt,
            ${if (deletedAt != null) "\"deletedAt\":$deletedAt," else ""}
            "ingredients":[]
        }]}
    """.trimIndent()
}
