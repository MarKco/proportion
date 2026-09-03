# The scaling engine

`:core:domain`, pure Kotlin, tested first. This is the part of the app that has to be right.

## One factor, four ways to reach it

```
ByServings(target)      target / recipe.servings
ByIngredient(line, qty) requested / original, after converting into the line's unit
ByFactor(factor)        used as is
ByAvailability(have)    a candidate factor per amount; the minimum wins and marks the bottleneck
```

Everything downstream of the factor is shared, which is why the rules live in one place rather than
four.

## Units

`MeasureUnit` carries a `category` and a `baseFactor`:

- `MASS` (g, kg) and `VOLUME` (ml, l, tsp, tbsp, glass, cup) are continuous.
- Domestic measures are **volume units with a factor in millilitres**, which is what makes
  cup ↔ ml work in v1 with no density involved.
- `COUNT` (piece, egg, clove, slice, leaf, sachet, jar) is **discrete**.
- `APPROXIMATE` (to taste, pinch, drizzle) **never scales** and never contributes to a factor.

Conversion crosses categories through the ingredient's own data (phase 9): MASS ↔ VOLUME via
`Ingredient.densityGramsPerMl`, COUNT ↔ MASS/VOLUME via `Ingredient.itemWeightGrams` (only when the
COUNT unit is that ingredient's own `defaultUnit` — a clove is not a slice, whatever its weight),
both chained through grams as the hub for COUNT ↔ VOLUME. With no density known, mass ↔ volume is
refused rather than guessed: 100 g of flour is not 100 ml. `requirementFor(from, to, ingredient)`
tells a caller what's missing (density, item weight, both, or nothing an answer would fix) — the UI
layer uses it to offer a "density unknown" prompt exactly when it would help.

## Impractical results

`DiscreteAnalyser` compares each discrete quantity with the nearest whole number:

- within 5%, it snaps silently — 2.02 eggs is 2 eggs and nobody needs to be told;
- beyond that, it emits `NonIntegerDiscrete` with the exact value plus a `SnapOption` for the whole
  numbers on either side, each carrying **the factor that amount implies**;
- a discrete result below 1 is clamped to 1: an ingredient must not vanish.

Accepting a snap re-runs the whole pipeline with the new factor. It never edits one line, which is
what keeps the recipe in proportion. The exact factor travels as a `Double` beside its display text
— rounding 4/3 to "1,33" and reading it back would drift the recipe.

Continuous quantities below half a gram or half a millilitre raise `TooSmallToMeasure`.

## Baking

`BakingAdvisor` fires when a recipe carries the built-in `oven` tag and the factor leaves the
0.7–1.4 band. It is advisory, never blocking, and carries a tin suggestion at constant batter depth:
**new diameter ≈ current × √factor** (a 24 cm tin at ×1.5 is about 29 cm).
