# 2. The scaling engine is pure Kotlin

**Status:** accepted, 2026-09-01

## Context

The rules about eggs, "to taste" and baking are the product. They are also the easiest thing to get
subtly wrong, and the hardest to notice when wrong.

## Decision

`:core:domain` is a JVM module with no Android dependency, asserted by a test that scans the sources
for `import android.` / `import androidx.`. All scaling arithmetic lives there; composables render
what it returns.

## Consequences

Domain tests run in milliseconds, which makes test-first cheap enough to actually do — the engine
has over fifty tests. The cost is that anything needing Android (unit names, resources) must be
passed in through a seam: `UnitNamer` is implemented in `:core:ui` and injected.
