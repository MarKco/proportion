package com.ilsecondodasinistra.proportion.core.data.repository

import com.ilsecondodasinistra.proportion.core.data.toDomain
import com.ilsecondodasinistra.proportion.core.database.dao.IngredientDao
import com.ilsecondodasinistra.proportion.core.database.dao.TagDao
import com.ilsecondodasinistra.proportion.core.database.entity.IngredientEntity
import com.ilsecondodasinistra.proportion.core.database.entity.TagEntity
import com.ilsecondodasinistra.proportion.core.domain.IngredientNames
import com.ilsecondodasinistra.proportion.core.domain.repository.IngredientRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.TagRepository
import com.ilsecondodasinistra.proportion.core.model.Ingredient
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Tag
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class IngredientRepositoryImpl @Inject constructor(
    private val dao: IngredientDao,
) : IngredientRepository {

    override fun observeAll(): Flow<List<Ingredient>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeInUse(): Flow<List<Ingredient>> =
        dao.observeInUse().map { list -> list.map { it.toDomain() } }

    override suspend fun findOrCreate(name: String, defaultUnit: MeasureUnit): Ingredient {
        val normalised = IngredientNames.normalise(name)
        dao.findByNormalisedName(normalised)?.let { return it.toDomain() }

        val created = IngredientEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            normalisedName = normalised,
            defaultUnit = defaultUnit,
        )
        dao.upsertAll(listOf(created))
        return created.toDomain()
    }
}

class TagRepositoryImpl @Inject constructor(
    private val dao: TagDao,
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
        )
        dao.upsert(created)
        return created.toDomain()
    }

    /** Built-in tags are protected by the query itself, so this is safe to call blindly. */
    override suspend fun deleteUserTag(id: String) = dao.deleteUserTag(id)
}
