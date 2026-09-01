package com.ilsecondodasinistra.proportion.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Tag
import com.ilsecondodasinistra.proportion.core.model.UnitCategory

/**
 * Built-in tags carry a key and follow the app language; user tags carry literal text and are shown
 * exactly as typed.
 */
@StringRes
fun builtInTagLabelRes(key: String): Int? = when (key) {
    "appetizer" -> R.string.tag_appetizer
    "first_course" -> R.string.tag_first_course
    "main_course" -> R.string.tag_main_course
    "side_dish" -> R.string.tag_side_dish
    "dessert" -> R.string.tag_dessert
    "bread_and_leavened" -> R.string.tag_bread_and_leavened
    "preserves" -> R.string.tag_preserves
    "drinks" -> R.string.tag_drinks
    "oven" -> R.string.tag_oven
    else -> null
}

@Composable
fun tagLabel(tag: Tag): String {
    val res = tag.key?.let { builtInTagLabelRes(it) }
    return when {
        res != null -> stringResource(res)
        else -> tag.name ?: tag.key.orEmpty()
    }
}

@StringRes
fun unitCategoryLabelRes(category: UnitCategory): Int = when (category) {
    UnitCategory.MASS -> R.string.unit_category_mass
    UnitCategory.VOLUME -> R.string.unit_category_volume
    UnitCategory.COUNT -> R.string.unit_category_count
    UnitCategory.APPROXIMATE -> R.string.unit_category_approximate
}

/** Units offered in the picker, grouped in the order a cook thinks about them. */
val unitsByCategory: Map<UnitCategory, List<MeasureUnit>> =
    MeasureUnit.entries.groupBy { it.category }

/**
 * Unit name for the UI, pluralised on [qty]. Screens use this rather than talking to
 * [com.ilsecondodasinistra.proportion.core.domain.unit.UnitNamer] directly, which is the domain's
 * seam and takes no Compose context.
 */
@Composable
fun unitLabel(unit: MeasureUnit, qty: Double = 2.0): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(unit, qty, context) { AndroidUnitNamer(context).shortName(unit, qty) }
}
