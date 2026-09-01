package com.ilsecondodasinistra.proportion.core.data.repository

import com.ilsecondodasinistra.proportion.core.data.toDomain
import com.ilsecondodasinistra.proportion.core.database.dao.IngredientDao
import com.ilsecondodasinistra.proportion.core.database.dao.ShoppingDao
import com.ilsecondodasinistra.proportion.core.database.entity.ShoppingItemEntity
import com.ilsecondodasinistra.proportion.core.domain.repository.ShoppingRepository
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaledLine
import com.ilsecondodasinistra.proportion.core.domain.unit.UnitConverter
import com.ilsecondodasinistra.proportion.core.model.ShoppingItem
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ShoppingRepositoryImpl @Inject constructor(
    private val shoppingDao: ShoppingDao,
    private val ingredientDao: IngredientDao,
    private val converter: UnitConverter,
) : ShoppingRepository {

    override fun observeItems(): Flow<List<ShoppingItem>> =
        combine(shoppingDao.observeItems(), ingredientDao.observeAll()) { items, ingredients ->
            val byId = ingredients.associateBy { it.id }
            items.mapNotNull { item ->
                byId[item.ingredientId]?.let { item.toDomain(it.toDomain()) }
            }
        }

    /**
     * 300 g plus 0.2 kg is 500 g; 2 eggs plus 300 g of eggs is two separate lines, because there is
     * no honest way to add them.
     */
    override suspend fun addScaled(lines: List<ScaledLine>, recipeId: String) {
        lines.filter { it.isScaled && it.scaledQty != null }.forEach { line ->
            val quantity = line.scaledQty ?: return@forEach
            val existing = shoppingDao.itemsFor(line.ingredientId)
                .firstOrNull { converter.convert(quantity, line.displayUnit, it.unit) != null }

            if (existing == null) {
                shoppingDao.upsert(
                    ShoppingItemEntity(
                        id = UUID.randomUUID().toString(),
                        ingredientId = line.ingredientId,
                        quantity = quantity,
                        unit = line.displayUnit,
                        sourceRecipeIds = listOf(recipeId),
                    ),
                )
            } else {
                val added = converter.convert(quantity, line.displayUnit, existing.unit) ?: return@forEach
                shoppingDao.upsert(
                    existing.copy(
                        quantity = (existing.quantity ?: 0.0) + added,
                        sourceRecipeIds = (existing.sourceRecipeIds + recipeId).distinct(),
                        isChecked = false,
                    ),
                )
            }
        }
    }

    override suspend fun setChecked(id: String, checked: Boolean) = shoppingDao.setChecked(id, checked)

    override suspend fun clearChecked() = shoppingDao.clearChecked()

    override suspend fun clearAll() = shoppingDao.clearAll()
}
