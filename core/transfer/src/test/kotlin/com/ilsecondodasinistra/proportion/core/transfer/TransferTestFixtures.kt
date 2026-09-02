package com.ilsecondodasinistra.proportion.core.transfer

import com.ilsecondodasinistra.proportion.core.domain.unit.DefaultUnitConverter
import com.ilsecondodasinistra.proportion.core.domain.unit.QuantityFormatter
import com.ilsecondodasinistra.proportion.core.domain.unit.UnitNamer
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit

/**
 * A handful of Italian short names, standing in for the Android resource lookup the real
 * [UnitNamer] implementation does. Shared between [PlainTextFormatterTest] and
 * [ShoppingListFormatterTest] so the mapping is defined once.
 */
internal val testUnitNamer = UnitNamer { unit, qty ->
    when (unit) {
        MeasureUnit.GRAM -> "g"
        MeasureUnit.EGG -> if (qty == 1.0) "uovo" else "uova"
        MeasureUnit.TO_TASTE -> "q.b."
        else -> unit.name.lowercase()
    }
}

internal fun testFormatter(): QuantityFormatter = QuantityFormatter(DefaultUnitConverter(), testUnitNamer)
