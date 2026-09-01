package com.ilsecondodasinistra.proportion.core.data.repository

import com.ilsecondodasinistra.proportion.core.data.toDomain
import com.ilsecondodasinistra.proportion.core.database.dao.ScaleVariantDao
import com.ilsecondodasinistra.proportion.core.database.entity.ScaleVariantEntity
import com.ilsecondodasinistra.proportion.core.domain.TimeProvider
import com.ilsecondodasinistra.proportion.core.domain.repository.ScaleVariantRepository
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaleConstraint
import com.ilsecondodasinistra.proportion.core.model.ScaleVariant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * A variant stores the constraint, not the numbers, so it keeps working after the recipe is edited.
 */
class ScaleVariantRepositoryImpl @Inject constructor(
    private val dao: ScaleVariantDao,
    private val json: Json,
    private val time: TimeProvider,
) : ScaleVariantRepository {

    override fun observeForRecipe(recipeId: String): Flow<List<ScaleVariant>> =
        dao.observeForRecipe(recipeId).map { list -> list.map { it.toDomain() } }

    override fun readConstraint(variant: ScaleVariant): ScaleConstraint =
        json.decodeFromString(variant.constraintPayload)

    override suspend fun save(
        recipeId: String,
        label: String,
        constraint: ScaleConstraint,
        asDefault: Boolean,
    ): String {
        val entity = ScaleVariantEntity(
            id = UUID.randomUUID().toString(),
            recipeId = recipeId,
            label = label,
            constraintPayload = json.encodeToString(constraint),
            isDefault = asDefault,
            createdAt = time.now(),
        )
        dao.upsertVariant(entity)
        return entity.id
    }

    override suspend fun delete(id: String) = dao.delete(id)
}
