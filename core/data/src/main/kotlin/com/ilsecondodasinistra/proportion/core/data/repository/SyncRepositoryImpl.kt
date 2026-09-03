package com.ilsecondodasinistra.proportion.core.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ilsecondodasinistra.proportion.core.data.toEntity
import com.ilsecondodasinistra.proportion.core.database.dao.IngredientDao
import com.ilsecondodasinistra.proportion.core.database.dao.RecipeDao
import com.ilsecondodasinistra.proportion.core.database.dao.TagDao
import com.ilsecondodasinistra.proportion.core.database.entity.IngredientEntity
import com.ilsecondodasinistra.proportion.core.database.entity.TagEntity
import com.ilsecondodasinistra.proportion.core.datastore.SyncLogDataSource
import com.ilsecondodasinistra.proportion.core.domain.IngredientNames
import com.ilsecondodasinistra.proportion.core.domain.TimeProvider
import com.ilsecondodasinistra.proportion.core.domain.repository.PreferencesRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.SyncRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.SyncResult
import com.ilsecondodasinistra.proportion.core.model.IngredientCategory
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.SyncLogEntry
import com.ilsecondodasinistra.proportion.core.sync.SyncAction
import com.ilsecondodasinistra.proportion.core.sync.SyncableState
import com.ilsecondodasinistra.proportion.core.sync.decideSyncAction
import com.ilsecondodasinistra.proportion.core.transfer.ProportionCodec
import com.ilsecondodasinistra.proportion.core.transfer.ProportionFile
import com.ilsecondodasinistra.proportion.core.transfer.TransferRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Folder sync (phase 10) I/O: reads and writes `.proportion` files in a Storage Access
 * Framework tree the user picked, and decides what to do with what it finds there via
 * `:core:sync`'s pure policy. Never touches the raw database file — see the design doc for why.
 *
 * [syncNow] always does a full push before it pulls: every local recipe and literal
 * ingredient/tag is (re-)exported first. That is what makes turning sync on, and a fresh install
 * pointed at an already-populated folder, work with no separate "first run" path — see the spec.
 */
@Singleton
class SyncRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recipeDao: RecipeDao,
    private val ingredientDao: IngredientDao,
    private val tagDao: TagDao,
    private val transferRepository: TransferRepository,
    private val preferencesRepository: PreferencesRepository,
    private val syncLog: SyncLogDataSource,
    private val time: TimeProvider,
) : SyncRepository {

    override suspend fun exportRecipe(recipeId: String) {
        val folder = openFolder() ?: return
        val text = transferRepository.exportRecipe(recipeId) ?: return
        writeOrLog(folder, fileName(RECIPE_PREFIX, recipeId), text, "la ricetta")
    }

    override suspend fun exportIngredient(ingredientId: String) {
        val folder = openFolder() ?: return
        val text = transferRepository.exportIngredient(ingredientId) ?: return
        writeOrLog(folder, fileName(INGREDIENT_PREFIX, ingredientId), text, "l'ingrediente")
    }

    override suspend fun exportTag(tagId: String) {
        val folder = openFolder() ?: return
        val text = transferRepository.exportTag(tagId) ?: return
        writeOrLog(folder, fileName(TAG_PREFIX, tagId), text, "il tag")
    }

    override suspend fun syncNow(): SyncResult {
        val folder = openFolder() ?: return SyncResult(0, 0, 0, 0)

        // Pull before push: a file already in the folder for an id we also have locally might be
        // newer than what we know — pushing first would blindly overwrite it with our stale local
        // copy before we ever got to read it. Pulling first merges it in, so the push right after
        // re-exports the now-merged (correct) state instead of clobbering someone else's update.
        val (recipesImported, recipesDeleted) = pullRecipes(folder)
        val catalogueImported = pullIngredients(folder) + pullTags(folder)
        val exported = pushEverything(folder)
        cleanupOldTombstones(folder)

        val result = SyncResult(
            exported = exported,
            recipesImported = recipesImported,
            recipesDeleted = recipesDeleted,
            catalogueImported = catalogueImported,
        )
        log(
            isError = false,
            "Sync completata: ${result.exported} esportate, ${result.recipesImported} ricette " +
                "importate, ${result.recipesDeleted} cancellate, ${result.catalogueImported} voci " +
                "di catalogo aggiornate",
        )
        return result
    }

    override fun observeLog(): Flow<List<SyncLogEntry>> = syncLog.entries

    // --- push -----------------------------------------------------------------------------

    private suspend fun pushEverything(folder: DocumentFile): Int {
        var exported = 0
        recipeDao.allIds().forEach { id ->
            val text = transferRepository.exportRecipe(id) ?: return@forEach
            if (writeFile(folder, fileName(RECIPE_PREFIX, id), text)) exported++
        }
        ingredientDao.allLiteralIds().forEach { id ->
            val text = transferRepository.exportIngredient(id) ?: return@forEach
            if (writeFile(folder, fileName(INGREDIENT_PREFIX, id), text)) exported++
        }
        tagDao.allLiteralIds().forEach { id ->
            val text = transferRepository.exportTag(id) ?: return@forEach
            if (writeFile(folder, fileName(TAG_PREFIX, id), text)) exported++
        }
        return exported
    }

    // --- pull -----------------------------------------------------------------------------

    private suspend fun pullRecipes(folder: DocumentFile): Pair<Int, Int> {
        var imported = 0
        var deleted = 0
        filesWithPrefix(folder, RECIPE_PREFIX).forEach { file ->
            val text = readFile(file) ?: run {
                log(isError = true, "File ricetta illeggibile nella cartella di sincronizzazione")
                return@forEach
            }
            val resolved = transferRepository.resolveRecipe(text) ?: run {
                log(isError = true, "File ricetta non riconosciuto nella cartella di sincronizzazione")
                return@forEach
            }
            val local = recipeDao.findByIdIncludingDeleted(resolved.id)
            val localState = local?.let { SyncableState(it.recipe.updatedAt, it.recipe.deletedAt) }
            val remoteState = SyncableState(resolved.updatedAt, resolved.deletedAt)
            when (decideSyncAction(localState, remoteState)) {
                SyncAction.Insert, SyncAction.Overwrite -> {
                    applyRecipe(resolved)
                    imported++
                }
                SyncAction.Delete -> {
                    recipeDao.hardDeleteRecipe(resolved.id)
                    deleted++
                }
                SyncAction.Skip -> Unit
            }
        }
        return imported to deleted
    }

    private suspend fun applyRecipe(recipe: Recipe) {
        ingredientDao.upsertAll(
            recipe.ingredients.map { it.ingredient }.filterNot { it.isBuiltIn }.map { it.toEntity() },
        )
        recipeDao.upsertRecipe(
            recipe = recipe.toEntity(),
            lines = recipe.ingredients.mapIndexed { index, line -> line.copy(position = index).toEntity(recipe.id) },
            tagIds = recipe.tags.map { it.id },
        )
    }

    private suspend fun pullIngredients(folder: DocumentFile): Int {
        var imported = 0
        filesWithPrefix(folder, INGREDIENT_PREFIX).forEach { file ->
            val text = readFile(file) ?: run {
                log(isError = true, "File ingrediente illeggibile nella cartella di sincronizzazione")
                return@forEach
            }
            val remote = ProportionCodec.decodeIngredientEntry(text) ?: run {
                log(isError = true, "File ingrediente non riconosciuto nella cartella di sincronizzazione")
                return@forEach
            }
            val unit = runCatching { MeasureUnit.valueOf(remote.defaultUnit) }.getOrNull() ?: run {
                log(isError = true, "Ingrediente '${remote.name}' scartato: unità sconosciuta")
                return@forEach
            }

            // Match by id first; a match by name covers the same ingredient created independently
            // on two devices before their first sync (two different ids, same concept) — the local
            // id is kept so recipes already pointing at it stay valid. See the spec.
            val local = ingredientDao.findById(remote.id) ?: ingredientDao.findByNormalisedName(remote.normalisedName)
            val action = decideSyncAction(
                local?.let { SyncableState(it.updatedAt) },
                SyncableState(remote.updatedAt),
            )
            if (action == SyncAction.Insert || action == SyncAction.Overwrite) {
                val category = remote.category?.let { runCatching { IngredientCategory.valueOf(it) }.getOrNull() }
                ingredientDao.upsertAll(
                    listOf(
                        IngredientEntity(
                            id = local?.id ?: remote.id,
                            key = null,
                            name = remote.name,
                            normalisedName = remote.normalisedName,
                            isBuiltIn = false,
                            defaultUnit = unit,
                            category = category,
                            densityGramsPerMl = remote.densityGramsPerMl,
                            itemWeightGrams = remote.itemWeightGrams,
                            updatedAt = remote.updatedAt,
                        ),
                    ),
                )
                imported++
            }
        }
        return imported
    }

    private suspend fun pullTags(folder: DocumentFile): Int {
        var imported = 0
        filesWithPrefix(folder, TAG_PREFIX).forEach { file ->
            val text = readFile(file) ?: run {
                log(isError = true, "File tag illeggibile nella cartella di sincronizzazione")
                return@forEach
            }
            val remote = ProportionCodec.decodeTagEntry(text) ?: run {
                log(isError = true, "File tag non riconosciuto nella cartella di sincronizzazione")
                return@forEach
            }

            val wantedName = IngredientNames.normalise(remote.name)
            val local = tagDao.findById(remote.id) ?: tagDao.observeAll().first().firstOrNull { tag ->
                !tag.isBuiltIn && IngredientNames.normalise(tag.name.orEmpty()) == wantedName
            }
            val action = decideSyncAction(
                local?.let { SyncableState(it.updatedAt) },
                SyncableState(remote.updatedAt),
            )
            if (action == SyncAction.Insert || action == SyncAction.Overwrite) {
                tagDao.upsert(
                    TagEntity(
                        id = local?.id ?: remote.id,
                        key = null,
                        name = remote.name,
                        isBuiltIn = false,
                        colorIndex = remote.colorIndex,
                        updatedAt = remote.updatedAt,
                    ),
                )
                imported++
            }
        }
        return imported
    }

    private suspend fun cleanupOldTombstones(folder: DocumentFile) {
        val cutoff = time.now() - TOMBSTONE_GRACE_MILLIS
        recipeDao.tombstonesOlderThan(cutoff).forEach { id ->
            recipeDao.hardDeleteRecipe(id)
            folder.findFile(fileName(RECIPE_PREFIX, id))?.delete()
        }
    }

    // --- folder / file plumbing -------------------------------------------------------------

    private suspend fun openFolder(): DocumentFile? {
        val prefs = preferencesRepository.observePreferences().first()
        if (!prefs.syncEnabled) return null
        val uriString = prefs.syncFolderUri ?: run {
            log(isError = true, "Sincronizzazione attiva ma nessuna cartella selezionata")
            return null
        }
        val tree = runCatching { resolveFolder(uriString) }.getOrNull()
        if (tree?.isUsableFolder() != true) {
            log(isError = true, "Cartella di sincronizzazione non raggiungibile: permesso revocato o cartella spostata")
            return null
        }
        return tree
    }

    private fun DocumentFile.isUsableFolder(): Boolean = isDirectory && canRead() && canWrite()

    /**
     * The real picker (`ActivityResultContracts.OpenDocumentTree`) only ever stores a `content://`
     * tree URI — the `file://` branch exists solely so a test can point this at a plain temp
     * directory via `DocumentFile.fromFile`, which works under Robolectric with no SAF provider
     * needed; production code never takes it.
     */
    private fun resolveFolder(uriString: String): DocumentFile? {
        val uri = Uri.parse(uriString)
        return if (uri.scheme == "file") {
            uri.path?.let { DocumentFile.fromFile(File(it)) }
        } else {
            DocumentFile.fromTreeUri(context, uri)
        }
    }

    private suspend fun writeOrLog(folder: DocumentFile, name: String, text: String, what: String) {
        if (!writeFile(folder, name, text)) {
            log(isError = true, "Impossibile scrivere $what sulla cartella di sincronizzazione")
        }
    }

    private fun writeFile(folder: DocumentFile, name: String, content: String): Boolean = runCatching {
        val target = folder.findFile(name) ?: folder.createFile(SYNC_MIME_TYPE, name)
        val uri = target?.uri ?: return false
        // See resolveFolder's note: file:// only ever happens under test.
        if (uri.scheme == "file") {
            uri.path?.let { File(it).writeText(content) } != null
        } else {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray()) } != null
        }
    }.getOrDefault(false)

    private fun readFile(file: DocumentFile): String? = runCatching {
        if (file.uri.scheme == "file") {
            file.uri.path?.let { File(it).readText() }
        } else {
            context.contentResolver.openInputStream(file.uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }
    }.getOrNull()

    private fun filesWithPrefix(folder: DocumentFile, prefix: String): List<DocumentFile> =
        folder.listFiles().filter { file ->
            val name = file.name.orEmpty()
            file.isFile && name.startsWith(prefix) && name.endsWith(SUFFIX)
        }

    private fun fileName(prefix: String, id: String): String = "$prefix$id$SUFFIX"

    private suspend fun log(isError: Boolean, message: String) {
        syncLog.append(SyncLogEntry(timestamp = time.now(), message = message, isError = isError))
    }

    private companion object {
        const val RECIPE_PREFIX = "recipe-"
        const val INGREDIENT_PREFIX = "ingredient-"
        const val TAG_PREFIX = "tag-"
        val SUFFIX = ".${ProportionFile.EXTENSION}"
        const val TOMBSTONE_GRACE_MILLIS = 30L * 24 * 60 * 60 * 1000

        /**
         * Deliberately not [ProportionFile.MIME_TYPE] (`application/octet-stream`): some SAF
         * providers map a MIME type to a file extension and append it to the display name given
         * to `createFile`, which would defeat [fileName]'s own `.proportion` suffix and break the
         * `findFile(name)` lookup a re-export relies on to overwrite rather than duplicate. A
         * vendor-specific type has no such mapping anywhere.
         */
        const val SYNC_MIME_TYPE = "application/x-proportion-sync"
    }
}
