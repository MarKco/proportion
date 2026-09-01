package com.ilsecondodasinistra.proportion.core.transfer

import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.Tag
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed interface DecodeResult {
    data class Success(val recipes: List<WireRecipe>) : DecodeResult
    data class Failure(val reason: DecodeFailure) : DecodeResult
}

sealed interface DecodeFailure {
    /** Valid JSON, but not one of ours. */
    data object NotProportionFile : DecodeFailure

    /** Written by a newer app. Refused rather than half-read. */
    data class FutureVersion(val found: Int, val supported: Int) : DecodeFailure

    /** Truncated, corrupt, or carrying a unit this version does not know. */
    data class Malformed(val message: String) : DecodeFailure
}

/**
 * Reads and writes `.proportion` files.
 *
 * Unknown fields are ignored on purpose: a file written by a later version must still import here,
 * minus what this version cannot understand. The one thing that is never guessed is a unit — a
 * quantity in an unknown unit is refused, because silently substituting one would change a recipe.
 */
object ProportionCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun encode(recipes: List<Recipe>, exportedAt: String? = null): String {
        val file = ProportionFile(
            exportedAt = exportedAt,
            recipes = recipes.map { it.toWire() },
        )
        return json.encodeToString(file)
    }

    fun decode(text: String): DecodeResult {
        val file = try {
            json.decodeFromString<ProportionFile>(text)
        } catch (e: SerializationException) {
            return DecodeResult.Failure(DecodeFailure.Malformed(e.message.orEmpty()))
        } catch (e: IllegalArgumentException) {
            return DecodeResult.Failure(DecodeFailure.Malformed(e.message.orEmpty()))
        }

        if (file.format != ProportionFile.FORMAT) {
            return DecodeResult.Failure(DecodeFailure.NotProportionFile)
        }
        if (file.version > ProportionFile.VERSION) {
            return DecodeResult.Failure(
                DecodeFailure.FutureVersion(file.version, ProportionFile.VERSION),
            )
        }

        val unknownUnit = file.recipes
            .flatMap { it.ingredients }
            .firstOrNull { line -> MeasureUnit.entries.none { it.name == line.unit } }

        if (unknownUnit != null) {
            return DecodeResult.Failure(
                DecodeFailure.Malformed("unknown unit ${unknownUnit.unit}"),
            )
        }

        return DecodeResult.Success(migrate(file).recipes)
    }

    /** No migrations yet; the hook exists so version 2 has an obvious place to land. */
    private fun migrate(file: ProportionFile): ProportionFile = file

    private fun Recipe.toWire() = WireRecipe(
        id = id,
        title = title,
        servings = servings,
        tags = tags.map { it.toWireTag() },
        ingredients = ingredients.sortedBy { it.position }.map { line ->
            WireIngredient(
                name = line.ingredient.name,
                qty = line.quantity,
                unit = line.unit.name,
                display = line.displayText,
                note = line.note,
                density = line.ingredient.densityGramsPerMl,
            )
        },
        steps = steps,
        notes = notes,
    )

    private fun Tag.toWireTag(): String =
        key?.let { "${ProportionFile.BUILT_IN_TAG_PREFIX}$it" } ?: name.orEmpty()
}
