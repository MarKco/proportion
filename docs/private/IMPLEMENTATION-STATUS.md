# ProPortion — implementation status

## Resume here (read this first after a break)

**Where things stand:** phases 1 to 5 are done. The app builds, installs, and does the whole core
job: enter a recipe, find it, rescale it four different ways, share it, back it up and restore it.
217 tests pass; detekt and lint are clean.

**What is next:** phase 6 — the dashboard, the persistent shopping list, cooking mode, favourites
and the cook counter. Write `docs/private/plans/<date>-phase-6-....md` first, following the shape of
the phase 4 and 5 plans, then implement task by task.

**How to build and check:**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"   # or Android Studio's bundled JBR
./gradlew detekt testDebugUnitTest :core:model:test :core:domain:test :core:transfer:test assembleDebug
./gradlew installDebug     # a Fairphone 3 (Android 13) is usually attached over USB
```

Screenshots for a device walkthrough: `adb shell screencap -p /sdcard/x.png && adb pull /sdcard/x.png`.

**Read next, in this order:** `specs/2026-09-01-proportion-v1-design.md` (the approved design),
`plans/` (one per phase), `architecture.md`, `scaling-engine.md`, `data-model.md`,
`proportion-format.md`, `adr/`.

**House rules that are easy to break by accident:**

- Never `git commit` or `git push` — Marco does that himself.
- Never mention Marco's employer anywhere.
- Tests first for `:core:domain` and `:core:transfer`; neither may import `android.*`.
- No hardcoded user-facing strings; `values/` English, `values-it/` Italian.
- No arithmetic in composables — quantities come from `ScaledRecipe`.
- Features never depend on other features; routes stay type-safe.
- Update this file at the end of every task, not at the end of the phase.

---

Living checklist, updated as work progresses. If a session ends, this file says where things stand.

**Legend:** `[ ]` not started · `[~]` in progress · `[x]` done

Last updated: 2026-09-01 (phase 5 complete, 217 tests green, verified on a Fairphone 3 / Android 13)

## Phase 0 — Design
- [x] Brainstorming and decisions
- [x] Visual blueprint (artifact)
- [x] Design spec — `docs/private/specs/2026-09-01-proportion-v1-design.md`
- [x] Implementation plan, phases 1–2 — `docs/private/plans/2026-09-01-phase-1-2-foundations-and-domain.md`
- [x] Implementation plan, phase 3 — `docs/private/plans/2026-09-01-phase-3-enter-and-browse.md`
- [x] Implementation plan, phase 4 — `docs/private/plans/2026-09-01-phase-4-cook-this-recipe.md`
- [x] Implementation plan, phase 5 — `docs/private/plans/2026-09-01-phase-5-data-exchange.md`
- [ ] Implementation plans for phases 6–7 (written one block at a time)

## Phase 1 — Foundations
- [x] Gradle multi-module scaffolding + version catalog (AGP 9.4.0, Gradle 9.7.1, Kotlin 2.3.21)
- [x] Design system: pastel palette, typography, motion, Material You + WCAG contrast tests
- [x] Adaptive + monochrome app icon (clipart 3D pie with candles)
- [x] Navigation with four tabs (per-tab back stack) + Robolectric Compose tests
- [x] README.md
- [x] GitHub Actions CI workflow + detekt config (green locally: detekt, lint-free build, 94 tests)

## Phase 2 — Domain and data
- [x] Model and typed units
- [x] Scaling engine (TDD) — converter, formatter, 4 constraint modes, discrete snaps
- [x] Oven advisory rule (oven tag, band 0.7–1.4, tin diameter = d x sqrt(factor))
- [x] Room schema, DAOs, migrations, seed of built-in tags (schema 1 exported)
- [x] Repositories (recipes, ingredients, tags, variants, shopping, preferences) + Hilt wiring

## Phase 3 — Enter and browse
- [x] `:core:ui` module: tag labels, unit picker, state bodies, ingredient rows
- [x] Recipe editor (`:feature:editor`): validation, ingredient autocomplete, tags, steps, discard dialog
- [x] Recipe list: debounced search, tag chips, ingredient sheet, sort, two distinct empty states
- [x] Recipe detail: formatted quantities, steps, variants, favourite, edit/delete
- [x] Navigation wired end to end, verified against the real Hilt graph

## Phase 4 — Cook this recipe
- [x] `:feature:cook`: four constraint modes (servings / ingredient / factor / pantry) with a live list
- [x] Warnings inline with snap chips; oven advisory banner with the tin-diameter hint
- [x] Scaled card (same shape as the detail) + save as variant + marks the recipe cooked
- [x] Cook button wired from the recipe detail; keyboard no longer covers the editor fields

## Phase 5 — Data exchange
- [x] `:core:transfer`: `.proportion` codec with version checks, tolerant parsing, refused unknown units
- [x] Plain-text share formatter (aligned quantities, scaled variant, attribution)
- [x] `TransferRepository`: preview without writing, merge vs replace-all, catalogue and tag resolution
- [x] Settings screen: theme, dynamic colour, backup and restore through the Storage Access Framework
- [x] Share as text / as a file from the recipe; opening a `.proportion` file lands in the restore flow
- [x] Developer docs: architecture, module map, data model, scaling engine, format, contributing, 4 ADRs

## Phase 6 — Home, shopping, cooking mode  <-- NEXT (needs its implementation plan first)
- [ ] Dashboard cards
- [ ] Persistent shopping list
- [ ] Cooking mode, favourites, cook counter

## Phase 7 — Polish
- [ ] Full it/en translations
- [ ] docs/public (it + en)
- [ ] docs/manual (it + en) — **with real screenshots captured from the test device** (Fairphone 3,
      `adb shell screencap`) and a walkthrough of each flow with worked examples:
      enter a recipe, rescale by servings, fix one ingredient, "with what I have", save a variant,
      share, back up and restore
- [x] docs/private (architecture, data model, scaling engine, format, ADRs) — done in phase 5
- [ ] Accessibility pass
- [ ] Release preparation

## Build environment (verified 2026-09-01)
- Gradle 9.7.1 wrapper, AGP 9.4.0, Kotlin 2.3.21, KSP 2.3.11, Hilt 2.60.1, Room 2.8.4.
- **AGP 9 ships built-in Kotlin support** — convention plugins must NOT apply `org.jetbrains.kotlin.android`.
- `build-logic` compiles with Gradle's embedded Kotlin, so it needs `-Xskip-metadata-version-check`.
- compileSdk/targetSdk stay at **36**: `platforms;android-37` is not published in the stable SDK channel yet,
  so AndroidX is pinned to the last versions that allow compiling against 36 (Compose BOM 2026.05.01,
  navigation 2.9.8, core-ktx 1.18.0, lifecycle 2.10.0, activity 1.12.4, hilt-navigation-compose 1.3.0).
- Build with JDK 21: `JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew :app:assembleDebug`

## Notes
- Never commit or push: Marco does that himself.
- v2 readiness kept alive: density column, `UnitConverter` signature, tolerant JSON parser.
