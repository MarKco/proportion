package com.ilsecondodasinistra.proportion.feature.shopping

import com.ilsecondodasinistra.proportion.core.domain.repository.ShoppingRepository
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaledLine
import com.ilsecondodasinistra.proportion.core.domain.unit.DefaultUnitConverter
import com.ilsecondodasinistra.proportion.core.domain.unit.QuantityFormatter
import com.ilsecondodasinistra.proportion.core.domain.unit.UnitNamer
import com.ilsecondodasinistra.proportion.core.model.Ingredient
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.ShoppingItem
import com.ilsecondodasinistra.proportion.core.transfer.ShoppingListStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

class TestUnitNamer : UnitNamer {
    override fun shortName(unit: MeasureUnit, qty: Double): String = when (unit) {
        MeasureUnit.GRAM -> "g"
        MeasureUnit.EGG -> if (qty == 1.0) "uovo" else "uova"
        MeasureUnit.TO_TASTE -> "q.b."
        else -> unit.name.lowercase()
    }
}

fun testFormatter(): QuantityFormatter = QuantityFormatter(DefaultUnitConverter(), TestUnitNamer())

object ShoppingTestData {

    const val flourId = "item-flour"

    private fun ingredient(name: String, unit: MeasureUnit) =
        Ingredient(id = "ing-${name.lowercase()}", name = name, normalisedName = name.lowercase(), defaultUnit = unit)

    /** Farina came from two recipes, so it should show it is shared; Sale is already checked. */
    val items = listOf(
        ShoppingItem(
            id = flourId,
            ingredient = ingredient("Farina", MeasureUnit.GRAM),
            quantity = 300.0,
            unit = MeasureUnit.GRAM,
            isChecked = false,
            sourceRecipeIds = listOf("r-cake", "r-bread"),
        ),
        ShoppingItem(
            id = "item-eggs",
            ingredient = ingredient("Uova", MeasureUnit.EGG),
            quantity = 2.0,
            unit = MeasureUnit.EGG,
            isChecked = false,
            sourceRecipeIds = listOf("r-cake"),
        ),
        ShoppingItem(
            id = "item-salt",
            ingredient = ingredient("Sale", MeasureUnit.TO_TASTE),
            quantity = null,
            unit = MeasureUnit.TO_TASTE,
            isChecked = true,
            sourceRecipeIds = listOf("r-cake"),
        ),
    )

    val strings = ShoppingListStrings(
        title = "Lista della spesa",
        checkedTitle = "Già presi",
        attribution = "Condiviso da ProPortion",
    )
}

class FakeShoppingRepository(initial: List<ShoppingItem>) : ShoppingRepository {

    private val stored = MutableStateFlow(initial)

    val checked = mutableMapOf<String, Boolean>()
    var clearCheckedCalls = 0
        private set
    var clearAllCalls = 0
        private set

    override fun observeItems(): Flow<List<ShoppingItem>> = stored

    override suspend fun addScaled(lines: List<ScaledLine>, recipeId: String) = Unit

    override suspend fun setChecked(id: String, checked: Boolean) {
        this.checked[id] = checked
        stored.value = stored.value.map { if (it.id == id) it.copy(isChecked = checked) else it }
    }

    override suspend fun clearChecked() {
        clearCheckedCalls++
        stored.value = stored.value.filterNot { it.isChecked }
    }

    override suspend fun clearAll() {
        clearAllCalls++
        stored.value = emptyList()
    }
}
