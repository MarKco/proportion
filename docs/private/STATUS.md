# Status

Living checklist. Update this at the end of every task, not at the end of the phase — if a session
ends mid-phase, this file says where things stand. For the story of how each phase got here (design
rationale, real bugs found and fixed, gotchas), see `HISTORY.md`.

**Legend:** `[ ]` not started · `[~]` in progress · `[x]` done

Last updated: 2026-09-03 (documentation consolidated: `docs/private` reduced from 27+ files plus a
working-screenshots folder to seven; `docs/manual/{it,en}` given real device screenshots; the
Editor's "press Enter to add/move between steps" behaviour added and a real bug in it fixed — a
multiline field's software keyboard shows a plain return key, not a "Next" action, on most
keyboards, so `KeyboardActions.onNext` never fired; fixed by intercepting the `Enter` key event
directly in `EditorScreen.kt`'s `StepEditorRow`).

## Resume here

Phases 1–9 are complete: v1 is feature-complete and polished, phase 8 added the 477-entry
translated ingredient catalogue, phase 9 added cross-category unit conversion (density/item-weight)
across Editor, Cook and Detail plus imperial units. Phase 10 (Syncthing-folder sync) has its
mechanism fully built and verified single-device on a Fairphone 3; the real two-device test is
still open, explicitly Marco's to run (see `HISTORY.md`, phase 10, for the four unchecked items).

`./gradlew verifyAll` is green (detekt, lint, every test, a debug APK).

**House rules that are easy to break by accident:**

- Never `git commit` or `git push` — Marco does that himself.
- Never mention Marco's employer anywhere.
- Tests first for `:core:domain`, `:core:transfer` and `:core:sync`; none of the three may import
  `android.*`.
- No hardcoded user-facing strings; `values/` English, `values-it/` Italian.
- No arithmetic in composables — quantities come from `ScaledRecipe`.
- Features never depend on other features; routes stay type-safe.
- Update this file at the end of every task, not at the end of the phase.

## Phase checklist

### Phase 0 — Design
- [x] Brainstorming, visual blueprint, approved v1 design spec

### Phase 1 — Foundations
- [x] Gradle multi-module scaffolding + version catalog
- [x] Design system: pastel palette, typography, motion, Material You + WCAG contrast tests
- [x] Adaptive + monochrome app icon
- [x] Navigation, four tabs, green CI

### Phase 2 — Domain and data
- [x] Model, typed units, scaling engine (TDD)
- [x] Oven advisory rule
- [x] Room schema, DAOs, migrations, built-in tag seeding
- [x] Repositories + Hilt wiring

### Phase 3 — Enter and browse
- [x] `:core:ui` shared components
- [x] Recipe editor, recipe list (search/filters/sort), recipe detail
- [x] Navigation wired end to end

### Phase 4 — Cook this recipe
- [x] `:feature:cook`: four constraint modes, warnings/snaps, oven advisory
- [x] Scaled card, save as variant, marks the recipe cooked

### Phase 5 — Data exchange
- [x] `:core:transfer`: `.proportion` codec, plain-text formatter
- [x] `TransferRepository`: preview, merge/replace, catalogue resolution
- [x] Settings: backup/restore via SAF; share/receive `.proportion` files

### Phase 6 — Home, shopping, cooking mode
- [x] Dashboard cards, shopping list, cooking mode, default variant + cook counter
- [x] Verified end to end on a Fairphone 3

### Phase 7 — Polish
- [x] Translation/CI hygiene, accessibility pass, the two promised animations
- [x] `docs/public`, `docs/manual` (real screenshots added later, see below)
- [x] `docs/private` developer docs, release signing mechanism
- [x] Added after: per-app language picker, app theme picker (Pastel/Vivid/Playful/High contrast)

### Phase 8 — Ingredient catalogue
- [x] 477 built-in ingredients, 16 categories, translated, resolved at read time
- [x] Seeding shared between `onCreate` and an additive migration
- [x] `.proportion` built-in ingredients travel by key
- [x] Added after: search by built-in ingredient name, autocomplete unit-overwrite fix, four
      keyboard/scroll bugs in the ingredient editor, standalone edit button on recipe detail

### Phase 9 — Cross-category unit conversion
- [x] `itemWeightGrams` + density backfill, schema v3
- [x] `UnitConverter` cross-category rewrite, `requirementFor`
- [x] Wired into Editor, Cook, Detail; imperial units added on request
- [x] Editor: unit no longer pre-selected on a fresh line

### Phase 10 — Syncthing folder sync
- [x] Schema v4 (`deletedAt`/`updatedAt`), `.proportion` tombstone support
- [x] `:core:sync` policy module, `SyncRepositoryImpl` (real SAF I/O)
- [x] Settings sync section, periodic `WorkManager` job
- [x] Verified single-device (export, simulated fresh install + re-import) on a Fairphone 3
- [ ] Real two-device Syncthing test: concurrent edits/conflicts, deletion propagation, error
      banner on a revoked folder, job cancellation on toggle-off — Marco's to run

## How to build and check

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"   # or Android Studio's bundled JBR
./gradlew verifyAll        # detekt + every module's unit tests + a debug APK
./gradlew testAll          # just the tests, for the fast loop
./gradlew installDebug     # a Fairphone 3 (Android 13) is usually attached over USB
```

`testAll` asks each module for whichever test task it actually has (`test` for the JVM modules,
`testDebugUnitTest` for the Android ones), so a module added later cannot silently go untested.
Android Studio has both as shared run configurations in `.run/`: **All tests** and **Full check**.

Screenshots for a device walkthrough: `adb shell screencap -p /sdcard/x.png && adb pull /sdcard/x.png`.

## Build environment (verified 2026-09-01)

- Gradle 9.7.1 wrapper, AGP 9.4.0, Kotlin 2.3.21, KSP 2.3.11, Hilt 2.60.1, Room 2.8.4.
- **AGP 9 ships built-in Kotlin support** — convention plugins must NOT apply
  `org.jetbrains.kotlin.android`.
- `build-logic` compiles with Gradle's embedded Kotlin, so it needs `-Xskip-metadata-version-check`.
- compileSdk/targetSdk stay at **36**: `platforms;android-37` is not published in the stable SDK
  channel yet, so AndroidX is pinned to the last versions that allow compiling against 36 (Compose
  BOM 2026.05.01, navigation 2.9.8, core-ktx 1.18.0, lifecycle 2.10.0, activity 1.12.4,
  hilt-navigation-compose 1.3.0).
- Build with JDK 21: `JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew :app:assembleDebug`

## Note on tooling

The `superpowers` Claude Code plugin (used for phases 6–8's brainstorm → spec → plan →
subagent-driven-execution workflow) was disabled by Marco partway through phase 9 — it was
consuming too much context. Its written record survives on disk at `.superpowers/` (full execution
ledgers for phases 6–8) even though the plugin/skills are gone; phases 9 and 10 onward were done
directly, without a separate implementation-plan document, working straight from a design spec (or,
for small changes, straight from conversation) plus this file.
