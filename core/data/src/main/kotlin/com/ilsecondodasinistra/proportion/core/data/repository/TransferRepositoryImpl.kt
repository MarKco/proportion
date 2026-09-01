package com.ilsecondodasinistra.proportion.core.data.repository

import com.ilsecondodasinistra.proportion.core.data.toDomain
import com.ilsecondodasinistra.proportion.core.database.dao.RecipeDao
import com.ilsecondodasinistra.proportion.core.database.dao.TagDao
import com.ilsecondodasinistra.proportion.core.domain.TimeProvider
import com.ilsecondodasinistra.proportion.core.domain.repository.IngredientRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.TagRepository
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.RecipeIngredient
import com.ilsecondodasinistra.proportion.core.model.Tag
import com.ilsecondodasinistra.proportion.core.transfer.DecodeResult
import com.ilsecondodasinistra.proportion.core.transfer.ImportMode
import com.ilsecondodasinistra.proportion.core.transfer.ImportOutcome
import com.ilsecondodasinistra.proportion.core.transfer.ImportPreview
import com.ilsecondodasinistra.proportion.core.transfer.ProportionCodec
import com.ilsecondodasinistra.proportion.core.transfer.ProportionFile
import com.ilsecondodasinistra.proportion.core.transfer.TransferRepository
import com.ilsecondodasinistra.proportion.core.transfer.WireRecipe
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Turns a `.proportion` file into rows of this database, and back.
 *
 * The interesting part is resolution: an incoming ingredient is matched against the catalogue by
 * its normalised name so importing a friend's recipe does not create a second "Farina 00", and an
 * incoming built-in tag binds to the seeded tag with the same key so it stays translated.
 */
class TransferRepositoryImpl @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
    private val tagRepository: TagRepository,
    private val recipeDao: RecipeDao,
    private val tagDao: TagDao,
    private val time: TimeProvider,
) : TransferRepository {

    override suspend fun exportAll(): String =
        ProportionCodec.encode(recipeRepository.observeRecipes().first(), exportedAt = stamp())

    override suspend fun exportRecipe(recipeId: String): String? {
        val recipe = recipeRepository.observeRecipe(recipeId).first() ?: return null
        return ProportionCodec.encode(listOf(recipe), exportedAt = stamp())
    }

    override suspend fun preview(text: String): ImportPreview =
        when (val decoded = ProportionCodec.decode(text)) {
            is DecodeResult.Failure -> ImportPreview.Invalid(decoded.reason)
            is DecodeResult.Success -> ImportPreview.Ready(
                total = decoded.recipes.size,
                alreadyPresent = recipeDao.existingIds(decoded.recipes.map { it.id }).size,
            )
        }

    override suspend fun import(text: String, mode: ImportMode): ImportOutcome {
        val decoded = when (val result = ProportionCodec.decode(text)) {
            is DecodeResult.Failure -> return ImportOutcome.Failed(result.reason)
            is DecodeResult.Success -> result.recipes
        }

        if (mode == ImportMode.REPLACE_ALL) {
            recipeDao.deleteAllRecipes()
        }

        val existing = if (mode == ImportMode.MERGE) {
            recipeDao.existingIds(decoded.map { it.id }).toSet()
        } else {
            emptySet()
        }

        var added = 0
        var skipped = 0

        decoded.forEach { wire ->
            if (wire.id in existing) {
                skipped++
                return@forEach
            }
            recipeRepository.upsert(wire.toRecipe())
            added++
        }

        return ImportOutcome.Imported(
            added = added,
            skipped = skipped,
            replacedLibrary = mode == ImportMode.REPLACE_ALL,
        )
    }

    private suspend fun WireRecipe.toRecipe(): Recipe {
        val lines = ingredients.mapIndexed { position, wire ->
            val unit = MeasureUnit.valueOf(wire.unit)
            RecipeIngredient(
                id = UUID.randomUUID().toString(),
                ingredient = ingredientRepository.findOrCreate(wire.name, unit),
                position = position,
                quantity = wire.qty,
                unit = unit,
                displayText = wire.display,
                note = wire.note,
            )
        }

        return Recipe(
            id = id,
            title = title,
            servings = servings,
            steps = steps,
            ingredients = lines,
            tags = tags.mapNotNull { resolveTag(it) },
            notes = notes,
        )
    }

    private suspend fun resolveTag(wireTag: String): Tag? = when {
        wireTag.startsWith(ProportionFile.BUILT_IN_TAG_PREFIX) -> {
            val key = wireTag.removePrefix(ProportionFile.BUILT_IN_TAG_PREFIX)
            // An unknown built-in key comes from a newer app: drop it rather than invent a tag.
            tagDao.findByKey(key)?.toDomain()
        }

        wireTag.isBlank() -> null

        else -> tagRepository.findOrCreateUserTag(wireTag)
    }

    private fun stamp(): String = time.now().toString()
}
