package com.ilsecondodasinistra.proportion.core.model

/**
 * The four kinds of quantity ProPortion knows how to reason about.
 *
 * Conversion is only ever allowed inside a category: without a density, grams and millilitres are
 * not interchangeable (see the v2 preparation in the design spec).
 */
enum class UnitCategory {
    MASS,
    VOLUME,
    COUNT,
    APPROXIMATE,
}

/**
 * A unit of measure.
 *
 * @param baseFactor how many base units one of this unit is worth. Base units are the gram for
 * [UnitCategory.MASS], the millilitre for [UnitCategory.VOLUME] and one piece for
 * [UnitCategory.COUNT]. Approximate units have no meaningful magnitude, so their factor is zero.
 */
enum class MeasureUnit(val category: UnitCategory, val baseFactor: Double) {
    GRAM(UnitCategory.MASS, 1.0),
    KILOGRAM(UnitCategory.MASS, 1_000.0),
    // Imperial mass, exact: 1 oz = 28.3495 g, 1 lb = 16 oz = 453.592 g.
    OUNCE(UnitCategory.MASS, 28.3495),
    POUND(UnitCategory.MASS, 453.592),

    MILLILITRE(UnitCategory.VOLUME, 1.0),
    LITRE(UnitCategory.VOLUME, 1_000.0),

    // Domestic measures are volume units, which is what makes cup <-> ml work without a density.
    TEASPOON(UnitCategory.VOLUME, 5.0),
    TABLESPOON(UnitCategory.VOLUME, 15.0),
    GLASS(UnitCategory.VOLUME, 200.0),
    CUP(UnitCategory.VOLUME, 240.0),
    // Imperial (US customary) volume, exact: docs/densities.json's own source table.
    FLUID_OUNCE(UnitCategory.VOLUME, 29.5735),
    PINT(UnitCategory.VOLUME, 473.176),
    QUART(UnitCategory.VOLUME, 946.353),
    GALLON(UnitCategory.VOLUME, 3785.41),

    PIECE(UnitCategory.COUNT, 1.0),
    EGG(UnitCategory.COUNT, 1.0),
    CLOVE(UnitCategory.COUNT, 1.0),
    SLICE(UnitCategory.COUNT, 1.0),
    LEAF(UnitCategory.COUNT, 1.0),
    SACHET(UnitCategory.COUNT, 1.0),
    JAR(UnitCategory.COUNT, 1.0),

    TO_TASTE(UnitCategory.APPROXIMATE, 0.0),
    PINCH(UnitCategory.APPROXIMATE, 0.0),
    DRIZZLE(UnitCategory.APPROXIMATE, 0.0),
    ;

    /** Half an egg does not exist: a non-integer result here needs a warning and a snap. */
    val isDiscrete: Boolean get() = category == UnitCategory.COUNT

    /** "To taste" stays "to taste" whatever the factor is. */
    val isScalable: Boolean get() = category != UnitCategory.APPROXIMATE
}
