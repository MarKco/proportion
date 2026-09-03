# Architecture decisions

## 1. Multi-module by feature and core

**Status:** accepted, 2026-09-01

### Context

ProPortion is a single-developer app, so the cost of module boundaries has to earn its keep.

### Decision

Split into `:app`, `:feature:*` and `:core:*`, with convention plugins in `build-logic`, following
the Now-in-Android layout. Features may not depend on each other.

### Consequences

More Gradle files and a longer first build. In exchange, the scaling engine can be tested without an
Android runtime, a screen's dependencies are visible in its `build.gradle.kts`, and incremental
builds only rebuild what changed. The rule against feature-to-feature dependencies is what actually
prevents the graph from silently becoming a ball of mud; when two features need the same thing, it
moves down into a core module.

## 2. The scaling engine is pure Kotlin

**Status:** accepted, 2026-09-01

### Context

The rules about eggs, "to taste" and baking are the product. They are also the easiest thing to get
subtly wrong, and the hardest to notice when wrong.

### Decision

`:core:domain` is a JVM module with no Android dependency, asserted by a test that scans the sources
for `import android.` / `import androidx.`. All scaling arithmetic lives there; composables render
what it returns.

### Consequences

Domain tests run in milliseconds, which makes test-first cheap enough to actually do — the engine
has over fifty tests. The cost is that anything needing Android (unit names, resources) must be
passed in through a seam: `UnitNamer` is implemented in `:core:ui` and injected. The same pattern was
later reused for `:core:transfer` (phase 5) and `:core:sync` (phase 10): both are equally pure, and
both are asserted the same way.

## 3. Type-safe navigation routes

**Status:** accepted, 2026-09-01 (replaces the initial string-route implementation)

### Context

The first pass used string routes with `navArgument`. It worked, but every argument existed twice —
once in the route pattern, once in the ViewModel's `SavedStateHandle` key — with nothing checking
that the two agreed.

### Decision

`@Serializable` route classes, `composable<T>`, `navigate(RouteKey(...))`, and
`savedStateHandle.toRoute<T>()` in the ViewModel.

### Consequences

A wrong argument is a compile error. Route classes are named `…RouteKey` where a composable already
owns the plain name (`CookRouteKey` / `CookRoute`). One wrinkle: `toRoute` decodes through
`android.os.Bundle`, so ViewModel tests that use it run under Robolectric rather than plain JUnit.

## 4. Mass ↔ volume conversion deferred to v2, but prepared in v1

**Status:** accepted 2026-09-01, superseded 2026-09-03 (phase 9 shipped it)

### Context

Converting grams to millilitres needs a density per ingredient, plus a table to maintain and a way
for users to correct it. That is a feature, not a detail — but retrofitting it must not force a
database migration on installed devices.

### Decision

Ship v1 without it, and prepare three things:

1. `Ingredient.density_g_per_ml` exists in **schema version 1**, nullable and unwritten.
2. `UnitConverter.convert` takes the ingredient from the start, even though v1 ignores it.
3. `DensityRepository` exists with a `NoDensityRepository` binding; v2 swaps that one binding.

### Consequences

v1 refused mass ↔ volume conversions rather than guessing. The preparation paid off exactly as
planned: phase 9 (2026-09-03) added the real conversion — `UnitConverter` rewritten,
`Ingredient.itemWeightGrams` added alongside the existing density column, a density-prompt dialog
wired into Editor/Cook/Detail — with **no schema-1 migration and no signature break**, only a schema
bump to add `item_weight_grams` and backfill data. `DensityRepository`/`NoDensityRepository` were
deleted as dead weight once density moved to living directly on `Ingredient`/`IngredientRef`
(`Ingredient.toRef()` is the bridge) — the originally sketched `DensityRepository` seam turned out
unnecessary once the real design was built, but the schema and signature prep were exactly what made
the real feature a same-day addition instead of a rework. See `HISTORY.md`, phase 9, for the full
story including the imperial units added on request in the same phase. Note that cup ↔ ml already
worked from v1, because domestic measures are modelled as volume units with a factor in millilitres.
