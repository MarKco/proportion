package com.ilsecondodasinistra.proportion.core.domain.scale

import com.ilsecondodasinistra.proportion.core.domain.unit.QuantityFormatter
import com.ilsecondodasinistra.proportion.core.domain.unit.UnitConverter
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Recipe
import com.ilsecondodasinistra.proportion.core.model.RecipeIngredient

/**
 * Turns one constraint into one factor, then applies that factor to every scalable line.
 *
 * Everything downstream of the factor is shared by all four constraint kinds, which is what keeps
 * the rules in one place instead of four.
 */
class DefaultRecipeScaler(
    private val converter: UnitConverter,
    private val formatter: QuantityFormatter,
    private val discreteAnalyser: DiscreteAnalyser,
    private val bakingAdvisor: BakingAdvisor,
) : RecipeScaler {

    override fun scale(recipe: Recipe, constraint: ScaleConstraint): ScaleResult {
        if (recipe.ingredients.isEmpty()) return ScaleResult.Failure(ScaleError.EmptyRecipe)

        val resolved = when (constraint) {
            is ScaleConstraint.ByFactor -> ResolvedFactor(constraint.factor)
            is ScaleConstraint.ByServings -> resolveServings(recipe, constraint)
            is ScaleConstraint.ByIngredient -> resolveIngredient(recipe, constraint)
            is ScaleConstraint.ByAvailability -> resolveAvailability(recipe, constraint)
        }

        val factor = when (resolved) {
            is ResolvedFactor -> resolved.factor
            is ResolvedError -> return ScaleResult.Failure(resolved.error)
        }

        if (factor <= 0.0 || !factor.isFinite()) {
            return ScaleResult.Failure(ScaleError.NonPositiveFactor)
        }

        return ScaleResult.Success(build(recipe, factor, resolved))
    }

    private fun resolveServings(recipe: Recipe, constraint: ScaleConstraint.ByServings): Resolved {
        val base = recipe.servings ?: return ResolvedError(ScaleError.NoServingsDefined)
        if (base <= 0) return ResolvedError(ScaleError.NoServingsDefined)
        return ResolvedFactor(constraint.target / base)
    }

    private fun resolveIngredient(recipe: Recipe, constraint: ScaleConstraint.ByIngredient): Resolved {
        val line = recipe.ingredients.firstOrNull { it.id == constraint.lineId }
            ?: return ResolvedError(ScaleError.UnknownLine)

        if (!line.unit.isScalable || !constraint.unit.isScalable) {
            return ResolvedError(ScaleError.ConstraintOnApproximateUnit)
        }

        val original = line.quantity ?: return ResolvedError(ScaleError.IncompatibleUnit)
        if (original <= 0.0) return ResolvedError(ScaleError.IncompatibleUnit)

        val requested = converter.convert(constraint.qty, constraint.unit, line.unit)
            ?: return ResolvedError(ScaleError.IncompatibleUnit)

        return ResolvedFactor(requested / original)
    }

    /**
     * "What can I make with what I have?" Every amount the user reports allows a factor of its own;
     * the smallest one wins, and that ingredient is the bottleneck. Everything else is left over.
     */
    private fun resolveAvailability(
        recipe: Recipe,
        constraint: ScaleConstraint.ByAvailability,
    ): Resolved {
        val linesById = recipe.ingredients.associateBy { it.id }

        val candidates = constraint.have.mapNotNull { available ->
            val line = linesById[available.lineId] ?: return@mapNotNull null
            if (!line.unit.isScalable || !available.unit.isScalable) return@mapNotNull null

            val original = line.quantity ?: return@mapNotNull null
            if (original <= 0.0) return@mapNotNull null

            val inLineUnit = converter.convert(available.qty, available.unit, line.unit)
                ?: return@mapNotNull null

            Candidate(
                lineId = line.id,
                unit = line.unit,
                availableQty = inLineUnit,
                requiredQty = original,
                factor = inLineUnit / original,
            )
        }

        val bottleneck = candidates.minByOrNull { it.factor }
            ?: return ResolvedError(ScaleError.NonPositiveFactor)

        val leftovers = candidates
            .filter { it.lineId != bottleneck.lineId }
            .map {
                Leftover(
                    lineId = it.lineId,
                    qty = it.availableQty - it.requiredQty * bottleneck.factor,
                    unit = it.unit,
                )
            }
            .filter { it.qty > LEFTOVER_EPSILON }

        return ResolvedFactor(
            factor = bottleneck.factor,
            bottleneckLineId = bottleneck.lineId,
            leftovers = leftovers,
        )
    }

    private fun build(recipe: Recipe, factor: Double, resolved: ResolvedFactor): ScaledRecipe {
        val rawLines = recipe.ingredients.sortedBy { it.position }.map { scaleLine(it, factor) }
        val analysis = discreteAnalyser.analyse(rawLines, recipe.ingredients)
        val baking = bakingAdvisor.advise(recipe, factor)

        return ScaledRecipe(
            factor = factor,
            servings = recipe.servings?.let { it * factor },
            lines = analysis.lines,
            warnings = analysis.warnings + listOfNotNull(baking),
            bottleneckLineId = resolved.bottleneckLineId,
            leftovers = resolved.leftovers,
            snapSuggestions = analysis.snaps,
        )
    }

    private fun scaleLine(line: RecipeIngredient, factor: Double): ScaledLine {
        val name = line.ingredient.name
        val quantity = line.quantity

        if (!line.unit.isScalable || quantity == null) {
            return ScaledLine(
                lineId = line.id,
                ingredientId = line.ingredient.id,
                ingredientName = name,
                originalQty = quantity,
                originalUnit = line.unit,
                scaledQty = null,
                displayUnit = line.unit,
                displayText = line.displayText ?: formatter.format(0.0, line.unit).text,
                isScaled = false,
            )
        }

        val formatted = formatter.format(quantity * factor, line.unit)
        return ScaledLine(
            lineId = line.id,
            ingredientId = line.ingredient.id,
            ingredientName = name,
            originalQty = quantity,
            originalUnit = line.unit,
            scaledQty = quantity * factor,
            displayUnit = formatted.unit,
            displayText = formatted.text,
            isScaled = true,
        )
    }

    private sealed interface Resolved

    private data class ResolvedFactor(
        val factor: Double,
        val bottleneckLineId: String? = null,
        val leftovers: List<Leftover> = emptyList(),
    ) : Resolved

    private data class ResolvedError(val error: ScaleError) : Resolved

    private data class Candidate(
        val lineId: String,
        val unit: MeasureUnit,
        val availableQty: Double,
        val requiredQty: Double,
        val factor: Double,
    )

    private companion object {
        /** Below this, a leftover is rounding noise rather than something to report. */
        const val LEFTOVER_EPSILON = 1e-9
    }
}
