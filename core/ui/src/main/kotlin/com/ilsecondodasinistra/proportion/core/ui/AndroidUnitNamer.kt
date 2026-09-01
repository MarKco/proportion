package com.ilsecondodasinistra.proportion.core.ui

import android.content.Context
import com.ilsecondodasinistra.proportion.core.domain.unit.UnitNamer
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
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

    override fun shortName(unit: MeasureUnit, qty: Double): String {
        val count = qty.roundToInt()
        return when (unit) {
            MeasureUnit.GRAM -> context.getString(R.string.unit_gram)
            MeasureUnit.KILOGRAM -> context.getString(R.string.unit_kilogram)
            MeasureUnit.MILLILITRE -> context.getString(R.string.unit_millilitre)
            MeasureUnit.LITRE -> context.getString(R.string.unit_litre)
            MeasureUnit.TEASPOON -> context.getString(R.string.unit_teaspoon)
            MeasureUnit.TABLESPOON -> context.getString(R.string.unit_tablespoon)
            MeasureUnit.GLASS -> context.getString(R.string.unit_glass)
            MeasureUnit.CUP -> context.getString(R.string.unit_cup)
            MeasureUnit.TO_TASTE -> context.getString(R.string.unit_to_taste)
            MeasureUnit.PINCH -> context.getString(R.string.unit_pinch)
            MeasureUnit.DRIZZLE -> context.getString(R.string.unit_drizzle)
            MeasureUnit.PIECE -> plural(R.plurals.unit_piece, count)
            MeasureUnit.EGG -> plural(R.plurals.unit_egg, count)
            MeasureUnit.CLOVE -> plural(R.plurals.unit_clove, count)
            MeasureUnit.SLICE -> plural(R.plurals.unit_slice, count)
            MeasureUnit.LEAF -> plural(R.plurals.unit_leaf, count)
            MeasureUnit.SACHET -> plural(R.plurals.unit_sachet, count)
            MeasureUnit.JAR -> plural(R.plurals.unit_jar, count)
        }
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
