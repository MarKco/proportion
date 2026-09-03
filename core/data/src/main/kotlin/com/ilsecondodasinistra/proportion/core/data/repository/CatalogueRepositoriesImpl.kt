package com.ilsecondodasinistra.proportion.core.data.repository

import com.ilsecondodasinistra.proportion.core.data.toDomain
import com.ilsecondodasinistra.proportion.core.database.dao.IngredientDao
import com.ilsecondodasinistra.proportion.core.database.dao.TagDao
import com.ilsecondodasinistra.proportion.core.database.entity.IngredientEntity
import com.ilsecondodasinistra.proportion.core.database.entity.TagEntity
import com.ilsecondodasinistra.proportion.core.domain.BuiltInIngredientNamer
import com.ilsecondodasinistra.proportion.core.domain.IngredientNames
import com.ilsecondodasinistra.proportion.core.domain.TimeProvider
import com.ilsecondodasinistra.proportion.core.domain.repository.IngredientRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.SyncRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.TagRepository
import com.ilsecondodasinistra.proportion.core.model.Ingredient
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Tag
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class IngredientRepositoryImpl @Inject constructor(
    private val dao: IngredientDao,
    private val namer: BuiltInIngredientNamer,
    private val time: TimeProvider,
    // Provider, not a direct SyncRepository — see RecipeRepositoryImpl's note on the Dagger cycle.
    private val sync: Provider<SyncRepository>,
) : IngredientRepository {

    override fun observeAll(): Flow<List<Ingredient>> =
        dao.observeAll().map { list -> list.map { it.toDomain(namer) }.sortedBy { it.normalisedName } }

    override fun observeInUse(): Flow<List<Ingredient>> =
        dao.observeInUse().map { list -> list.map { it.toDomain(namer) }.sortedBy { it.normalisedName } }

    override suspend fun findOrCreate(name: String, defaultUnit: MeasureUnit): Ingredient {
        val normalised = IngredientNames.normalise(name)
        observeAll().first().firstOrNull { it.normalisedName == normalised }?.let { return it }

        // A built-in row's stored normalised_name is frozen to its raw key (see ProPortionDatabase's
        // seeding, and RecipeRepositoryImpl.upsert's guard that keeps it that way) - this catches a
        // normalised match against that raw key that the resolved-name check above cannot see, e.g.
        // typing "almond" when the catalogue currently displays "Almonds".
        dao.findByNormalisedName(normalised)?.let { return it.toDomain(namer) }

        val created = IngredientEntity(
            id = UUID.randomUUID().toString(),
            key = null,
            name = name.trim(),
            normalisedName = normalised,
            isBuiltIn = false,
            defaultUnit = defaultUnit,
            updatedAt = time.now(),
        )
        dao.upsertAll(listOf(created))
        sync.get().exportIngredient(created.id)
        return created.toDomain(namer)
    }

    override suspend fun setDensityData(id: String, densityGramsPerMl: Double?, itemWeightGrams: Double?) {
        val now = time.now()
        densityGramsPerMl?.let { dao.updateDensity(id, it, now) }
        itemWeightGrams?.let { dao.updateItemWeight(id, it, now) }
        sync.get().exportIngredient(id)
    }
}

class TagRepositoryImpl @Inject constructor(
    private val dao: TagDao,
    private val time: TimeProvider,
    // Provider, not a direct SyncRepository — see RecipeRepositoryImpl's note on the Dagger cycle.
    private val sync: Provider<SyncRepository>,
) : TagRepository {

    override fun observeAll(): Flow<List<Tag>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun findOrCreateUserTag(name: String): Tag {
        val trimmed = name.trim()
        val wanted = IngredientNames.normalise(trimmed)
        val existing = dao.observeAll().first().firstOrNull { tag ->
            tag.name?.let { IngredientNames.normalise(it) } == wanted
        }
        if (existing != null) return existing.toDomain()

        val created = TagEntity(
            id = UUID.randomUUID().toString(),
            key = null,
            name = trimmed,
            isBuiltIn = false,
            updatedAt = time.now(),
        )
        dao.upsert(created)
        sync.get().exportTag(created.id)
        return created.toDomain()
    }

    /** Built-in tags are protected by the query itself, so this is safe to call blindly. */
    override suspend fun deleteUserTag(id: String) = dao.deleteUserTag(id)
}
