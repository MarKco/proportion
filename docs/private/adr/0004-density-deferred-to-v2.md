# 4. Mass ↔ volume conversion deferred to v2, but prepared in v1

**Status:** accepted, 2026-09-01

## Context

Converting grams to millilitres needs a density per ingredient, plus a table to maintain and a way
for users to correct it. That is a feature, not a detail — but retrofitting it must not force a
database migration on installed devices.

## Decision

Ship v1 without it, and prepare three things:

1. `Ingredient.density_g_per_ml` exists in **schema version 1**, nullable and unwritten.
2. `UnitConverter.convert` takes the ingredient from the start, even though v1 ignores it.
3. `DensityRepository` exists with a `NoDensityRepository` binding; v2 swaps that one binding.

## Consequences

v1 refuses mass ↔ volume conversions rather than guessing. v2 adds the density table and one Hilt
binding — no migration, no call sites to change. Note that cup ↔ ml already works in v1, because
domestic measures are modelled as volume units with a factor in millilitres.
