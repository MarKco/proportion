package com.ilsecondodasinistra.proportion.core.database

import androidx.room.TypeConverter
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import kotlinx.serialization.json.Json

/**
 * Room type converters.
 *
 * String lists (recipe steps, source recipe ids) are stored as JSON rather than joined on a
 * separator: steps contain commas, newlines and quotes, and a separator that turns up inside a step
 * would silently split it in two.
 */
class Converters {

    @TypeConverter
    fun unitToName(unit: MeasureUnit): String = unit.name

    @TypeConverter
    fun nameToUnit(name: String): MeasureUnit = MeasureUnit.valueOf(name)

    @TypeConverter
    fun listToString(values: List<String>): String = json.encodeToString(values)

    @TypeConverter
    fun stringToList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else json.decodeFromString(value)

    private companion object {
        val json = Json
    }
}
