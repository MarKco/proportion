package com.ilsecondodasinistra.proportion.core.ui

import android.content.Context
import com.ilsecondodasinistra.proportion.core.domain.unit.UnitNamer
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.UnitCategory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Resolves unit names from `strings.xml`, so they follow the app language and pluralise correctly
 * ("1 uovo" but "2 uova"). The domain stays free of Android by talking to [UnitNamer] instead.
 */
class AndroidUnitNamer @Inject constructor(
    @ApplicationContext private val context: Context,
) : UnitNamer {

    override fun shortName(unit: MeasureUnit, qty: Double): String =
        if (unit.category == UnitCategory.COUNT) countName(unit, qty.roundToInt()) else continuousName(unit)

    /** Every unit whose name doesn't depend on the quantity — split out to keep each `when` small. */
    private fun continuousName(unit: MeasureUnit): String = when (unit) {
        MeasureUnit.GRAM -> context.getString(R.string.unit_gram)
        MeasureUnit.KILOGRAM -> context.getString(R.string.unit_kilogram)
        MeasureUnit.OUNCE -> context.getString(R.string.unit_ounce)
        MeasureUnit.POUND -> context.getString(R.string.unit_pound)
        MeasureUnit.MILLILITRE -> context.getString(R.string.unit_millilitre)
        MeasureUnit.LITRE -> context.getString(R.string.unit_litre)
        MeasureUnit.TEASPOON -> context.getString(R.string.unit_teaspoon)
        MeasureUnit.TABLESPOON -> context.getString(R.string.unit_tablespoon)
        MeasureUnit.GLASS -> context.getString(R.string.unit_glass)
        MeasureUnit.CUP -> context.getString(R.string.unit_cup)
        MeasureUnit.FLUID_OUNCE -> context.getString(R.string.unit_fluid_ounce)
        MeasureUnit.PINT -> context.getString(R.string.unit_pint)
        MeasureUnit.QUART -> context.getString(R.string.unit_quart)
        MeasureUnit.GALLON -> context.getString(R.string.unit_gallon)
        MeasureUnit.TO_TASTE -> context.getString(R.string.unit_to_taste)
        MeasureUnit.PINCH -> context.getString(R.string.unit_pinch)
        MeasureUnit.DRIZZLE -> context.getString(R.string.unit_drizzle)
        MeasureUnit.PIECE, MeasureUnit.EGG, MeasureUnit.CLOVE, MeasureUnit.SLICE,
        MeasureUnit.LEAF, MeasureUnit.SACHET, MeasureUnit.JAR ->
            error("$unit is a COUNT unit, handled by countName instead")
    }

    /** "1 uovo" but "2 uova": every COUNT unit pluralises through `plurals.xml`. */
    private fun countName(unit: MeasureUnit, count: Int): String = when (unit) {
        MeasureUnit.PIECE -> plural(R.plurals.unit_piece, count)
        MeasureUnit.EGG -> plural(R.plurals.unit_egg, count)
        MeasureUnit.CLOVE -> plural(R.plurals.unit_clove, count)
        MeasureUnit.SLICE -> plural(R.plurals.unit_slice, count)
        MeasureUnit.LEAF -> plural(R.plurals.unit_leaf, count)
        MeasureUnit.SACHET -> plural(R.plurals.unit_sachet, count)
        MeasureUnit.JAR -> plural(R.plurals.unit_jar, count)
        else -> error("$unit is not a COUNT unit, handled by continuousName instead")
    }

    private fun plural(resId: Int, count: Int): String =
        context.resources.getQuantityString(resId, count)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class UnitNamerModule {

    /** The domain asks for names through [UnitNamer]; only this layer knows about resources. */
    @Binds
    abstract fun unitNamer(impl: AndroidUnitNamer): UnitNamer
}
