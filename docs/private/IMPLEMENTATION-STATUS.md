# ProPortion — implementation status

## Resume here (read this first after a break)

**Where things stand:** phases 1 to 8 are done. v1 is feature-complete and polished (phases 1-7),
and phase 8 (2026-09-03) added the comprehensive, translated, autocompleted ingredient catalogue —
see the dedicated entry below. The app builds, installs, and does the whole job: enter a recipe,
find it, rescale it four different ways, share it, back it up and restore it, see a dashboard, keep
a shopping list, follow a recipe in cooking mode, and now enter ingredients from a real 477-entry
bilingual catalogue instead of typing every one from scratch. Translations are complete and
parity-checked, accessibility has had a real on-device pass (TalkBack + large text), the two motion
animations the design system always promised are wired up, `docs/public` and `docs/manual` exist in
both languages, and a release-signing mechanism is ready (no keystore created yet — that's Marco's
own step whenever he has one; see `docs/private/release-checklist.md`). `./gradlew verifyAll` is
green (detekt, lint, every test, a debug APK) and `./gradlew assembleRelease` succeeds unsigned.
Verified end to end on a Fairphone 3 (Android 13) on 2026-09-03, including a real schema migration
against that device's own pre-existing recipes (zero data loss) and live bilingual autocomplete.

**What is next:** phase 8's two known limitations (search by built-in ingredient name, and
autocomplete always overwriting the unit) were both fixed right after shipping — see "Added after
phase 8" below. No phase 9 has been scoped yet.

**Note on tooling, 2026-09-03:** the `superpowers` Claude Code plugin (used for phases 6-8's
brainstorm → spec → plan → subagent-driven-execution workflow) is now disabled — Marco turned it
off, it was consuming too much context. Its written record survives on disk at `.superpowers/`
(specs, plans, full execution ledgers for phases 6-8) even though the plugin/skills are gone;
consult those before re-deriving context on anything they cover.

**Added after phase 7, 2026-09-03: per-app language selection.** Settings now has a language picker
(System / Italiano / English), independent of the device's own language. The old
`PreferencesRepository.setLanguage`/`UserPreferences.language` fields (dead code, phase 7 found them
unused) were removed; `LocaleController` (`:core:domain` interface, `AppCompatLocaleController` in
`:core:ui`) is the one place the choice lives now, via `AppCompatDelegate` + AppCompat's own
persisted storage. **A real gotcha, found only by testing switching the language live on-device**:
`AppCompatDelegate.setApplicationLocales()` alone does not update the *running* app on a plain
`ComponentActivity` — its live-apply hooks target `AppCompatActivity`, and making `MainActivity` one
crashes immediately (`Theme.ProPortion` isn't a `Theme.AppCompat` descendant, and becoming one for a
Compose-only app just for this would be the wrong trade). The fix that actually works, verified on
the Fairphone 3 (API 33) by switching languages back and forth with no restart: call the platform
`LocaleManager` directly on API 33+ (the same mechanism the device's own Settings > Apps > Language
screen uses), and wrap `MainActivity.attachBaseContext` with `AppCompatDelegate`'s persisted choice
for API 26–32, where no such platform API exists — a language change there takes effect on
`recreate()` the same way, just via a different, older mechanism. `docs/public/en/`'s screenshots
are still Italian-locale copies, disclosed in the doc itself, pending a design review Marco wants to
do before recapturing screenshots for both `docs/public` and `docs/manual`.

**Added after phase 7, 2026-09-03: app theme picker.** Settings now has a second appearance choice,
enabled only while "Colours from the wallpaper" (dynamic colour / Material You) is off: four named
static themes — Pastel, Vivid, Playful, High contrast — each with its own light and dark variant.
`UserPreferences.appTheme: AppTheme` (`:core:model`) persists through `UserPreferencesDataSource`
(key `app_theme`, string-encoded enum, falls back to `PASTEL` on a missing/corrupt value, same
pattern as `themeMode`), threaded through `PreferencesRepository`/`PreferencesRepositoryImpl`, and
consumed by `ProPortionTheme(appTheme = ...)` in `:core:designsystem/theme/Theme.kt`, which now picks
one of eight `ColorScheme`s (four themes × light/dark) instead of the old single static
`ProPortionLightColors`/`ProPortionDarkColors` pair — that pair (and the now-unused `GreenInk`
constant) is gone; `Pistachio`/`Apricot`/`Butter`/`Blueberry`/`Rose` remain, now used only for chart
colours. All eight schemes are checked in `ColorContrastTest.kt` against WCAG AA (4.5:1) on every
text-on-fill role pair; the high-contrast theme is additionally checked against the stricter AAA bar
(7:1) on its surface and primary roles, since accessibility is that theme's whole purpose. The
Settings row (`SettingsScreen.kt`) follows the existing radio-group pattern used for theme mode and
language, stays visible but disabled (`alpha = 0.38f`, matching Material's standard disabled-content
opacity) while dynamic colour is on, rather than disappearing — chosen so the setting's existence
isn't hidden from someone who might want to turn dynamic colour off. New strings
(`settings_app_theme*`) added to both `values/` and `values-it/strings.xml` in `:feature:settings`;
parity confirmed with `scripts/check-string-parity.sh`.

**Phase 8, 2026-09-03: ingredient catalogue.** 477 built-in ingredients, translated Italian/English,
covering all 16 categories of a new `IngredientCategory` enum (foundation-only — no UI surfaces it
yet). `Ingredient`/`IngredientEntity` gained `key`/`isBuiltIn` (mirroring `Tag`) plus `category`;
built-in names resolve at the repository boundary via a new `BuiltInIngredientNamer`
(`core/domain` interface, `AndroidIngredientNamer` in `core/ui`, using `Resources.getIdentifier`
rather than a 477-branch `when`, which detekt's `LongMethod` limit would reject). Seed data lives
in `core/database/src/main/assets/ingredients.json`, loaded once via a shared function called from
*both* `Room.Callback.onCreate` (fresh installs) and a new additive `Migration(1, 2)` (existing
installs) — `onCreate` alone would have left every upgrading install with an empty catalogue
forever. Spec: `docs/private/specs/2026-09-03-ingredient-catalogue-design.md`. Plan and full
execution ledger (every task, every ruling, every gap found mid-execution — three real ones the
original spec/plan missed, all fixed): `docs/private/plans/2026-09-03-phase-8-ingredient-catalogue.md`
and `.superpowers/sdd/2026-09-03-phase-8-ingredient-catalogue/`.

The `.proportion` export/import format got a matching fix in the same phase (Marco's own call,
made when reviewing the spec): built-in ingredients now travel by key (`builtin:<key>`, mirroring
how built-in tags already worked), not by their current-language literal name — otherwise sharing a
recipe between two devices set to different app languages would have duplicated a built-in
ingredient under a literal name on import.

**Two real bugs found by the final whole-plan review, in pre-existing code this phase's own tasks
never touched, fixed before shipping:** `RecipeRepositoryImpl.upsert()` was silently overwriting a
built-in ingredient's seeded placeholder row with current-language text on every recipe save
(violating the "the placeholder is never read or written" design assumption); and `findOrCreate`
could silently fail — and later crash a recipe save with a foreign-key error — for ingredients
whose raw catalogue key differs from its displayed name in both languages (e.g. typing "almond"
when the catalogue shows "Almonds"/"Mandorle"). Both are fixed, with two new regression tests that
were proven to actually catch the bugs (reverted the fix, watched the test fail, restored it,
watched it pass — done independently by both the implementer and a second reviewer).

**Two known limitations, left for Marco to decide on rather than fixed in a rush:** searching
recipes by ingredient name doesn't find a built-in ingredient by its localised name (only
autocomplete/entry is affected — `RecipeDao.filtered`'s SQL reads the catalogue's raw English-key
column directly; a proper fix needs that query restructured to resolve names in Kotlin, judged too
risky to rush through the one fix wave a final-review cap allows); and picking an autocomplete
suggestion always overwrites the ingredient line's unit with that ingredient's default, even if the
user had already typed a different one (pre-existing behaviour, more noticeable now that 477
curated defaults exist). See the spec's §8 for the full detail on both.

**Added after phase 8, 2026-09-03: fixed both known limitations, plus two UI requests.** All four
done manually (the `superpowers` plugin was disabled partway through this work — see the tooling
note above), with real unit tests and a live on-device check for each:

- **Recipe search now finds built-in ingredients by their localised name.**
  `RecipeDao.filtered` gained a `matchingBuiltInIds: List<String>` parameter (defaults to empty, so
  every existing caller is unaffected); its ingredient-name `EXISTS` clause now also matches
  `ri.ingredient_id IN (:matchingBuiltInIds)`. `RecipeRepositoryImpl.observeRecipes` computes that
  list in Kotlin (resolving each built-in ingredient's name via the namer and checking it against
  the search query) before calling `filtered`, only when the query is non-blank.
- **Picking an autocomplete suggestion no longer overwrites a unit already in the same measurement
  category.** `EditorViewModel.onSuggestionPick` now compares `line.unit.category` against
  `ingredient.defaultUnit.category` (`MeasureUnit`'s existing `UnitCategory` — MASS/VOLUME/COUNT/
  APPROXIMATE) and only replaces the unit when they differ — e.g. picking a gram-default ingredient
  while kilograms is already set keeps kilograms.
- **The keyboard no longer hides the suggestion list right after "Aggiungi ingrediente".** The
  newly-added row requests focus once and scrolls itself into view once, with ~420dp of clearance
  reserved below it (three rows of suggestion chips' worth) — calculated a single time, right when
  the keyboard finishes opening, deliberately *before* any suggestions exist yet, so later
  suggestion-driven resizes never trigger another scroll. Three real bugs found and fixed while
  getting this right, all confirmed live on-device (screenshots taken, not just reasoned about):
  (1) a zero-width `Rect` passed to `BringIntoViewRequester` is treated as nothing to bring into
  view and silently does nothing — must use the row's real measured width; (2)
  `WindowInsets.isImeVisible` flips true as soon as the keyboard *starts* animating in, not once it
  finishes, so scrolling immediately measures against a still-shrinking viewport and undershoots —
  fixed by polling the live `WindowInsets.ime` inset for a few consecutive stable frames rather than
  guessing a fixed delay; (3) the worst one — keying the scroll `LaunchedEffect` on the row's
  measured size caused it to re-fire on *every keystroke*, because the card keeps resizing as the
  suggestion list appears/changes while typing, producing a visible scroll glitch on every letter
  typed. Fixed by reading the size as a plain polled value inside the effect instead of as a
  recomposition key, so the effect runs exactly once per focus request no matter how many times the
  card resizes afterward. **A fourth bug in the same area, found right after** (on-device, editing
  an existing recipe): opening the editor for a saved recipe focused and over-scrolled to its last
  ingredient line, sometimes pushing the field off-screen entirely. Cause: "newly added" was being
  *inferred* from the line count going up, which also fires the moment an existing recipe's saved
  lines finish loading (the draft starts as one placeholder line, then gets replaced by the
  recipe's real ones — a legitimate increase, but not a user-triggered add). Fixed by having
  `EditorViewModel.onAddLine` set an explicit `justAddedLineId` in the state instead of the screen
  guessing from a size change; loading a recipe's saved lines never touches that field, so it can
  no longer misfire. Two regression tests guard this: one confirms loading a recipe never sets it,
  the other confirms `onAddLine` sets it to the right line and it clears once handled.
- **The recipe detail screen has a standalone edit (pencil) button, top-right, always visible** —
  no need to open the overflow (⋮) menu first. The redundant "Modifica" entry was removed from that
  menu, which now holds only share/delete.

Phase 6's, phase 7's and phase 8's per-task briefs, reports and full progress ledgers (including
every ruling made while executing them) are kept at
`.superpowers/sdd/2026-09-01-phase-6-home-shopping-cooking-mode/`,
`.superpowers/sdd/2026-09-02-phase-7-polish/` and
`.superpowers/sdd/2026-09-03-phase-8-ingredient-catalogue/` for the record; nothing there needs
restoring or resuming any more.

**How to build and check:**

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

**Read next, in this order:** `specs/2026-09-01-proportion-v1-design.md` (the approved design),
`plans/` (one per phase), `architecture.md`, `scaling-engine.md`, `data-model.md`,
`proportion-format.md`, `localization.md`, `adr/`, `release-checklist.md`. Then, for the
user-facing (not developer) side: `docs/public/` and `docs/manual/`.

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

Last updated: 2026-09-03 (phases 1–8 complete; phase 8 added the 477-entry translated ingredient
catalogue, then both its known limitations were fixed plus two UI requests (search by built-in
ingredient name, unit-keeps-if-compatible, editor scroll clearance, top-level edit button);
`verifyAll` and `assembleRelease` both green, verified on a Fairphone 3 / Android 13 against real
pre-existing data)

## Phase 0 — Design
- [x] Brainstorming and decisions
- [x] Visual blueprint (artifact)
- [x] Design spec — `docs/private/specs/2026-09-01-proportion-v1-design.md`
- [x] Implementation plan, phases 1–2 — `docs/private/plans/2026-09-01-phase-1-2-foundations-and-domain.md`
- [x] Implementation plan, phase 3 — `docs/private/plans/2026-09-01-phase-3-enter-and-browse.md`
- [x] Implementation plan, phase 4 — `docs/private/plans/2026-09-01-phase-4-cook-this-recipe.md`
- [x] Implementation plan, phase 5 — `docs/private/plans/2026-09-01-phase-5-data-exchange.md`
- [x] Implementation plan, phase 6 — `docs/private/plans/2026-09-01-phase-6-home-shopping-cooking-mode.md`
- [x] Implementation plan, phase 7 — `docs/private/plans/2026-09-02-phase-7-polish.md`

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

## Phase 6 — Home, shopping, cooking mode  <-- DONE
- [x] Task 1 — `DashboardSummariser` + `RecipePicker` in `:core:domain` (15 tests, reviewed clean)
- [x] Task 2 — `DonutChart` + `sweepAngles` in `:core:designsystem` (3 tests, reviewed; the entrance
      animation needed a state flag, `animateFloatAsState` never animates towards a constant target)
- [x] Task 3 — dashboard cards in `:feature:home` (8 tests; `Random` needed a Hilt qualifier)
- [x] Task 4 — `ShoppingListFormatter` in `:core:transfer` (5 tests; row alignment now shared with
      `PlainTextFormatter` through `AlignedRow.kt`)
- [x] Task 5 — shopping screen: check/clear-checked/clear-all with a real cancel button, share (11 tests)
- [x] Task 6 — "add to shopping list" from the scale screen, confirmed by a Snackbar (22+10 tests)
- [x] Task 7 — cooking mode: keep-screen-on, checkable steps, scaled ingredients sheet, the scaling
      travels the route as Base64 JSON (51 tests)
- [x] Task 8 — default variant shown on the recipe detail ("showing: <label> · view original") +
      cook counter (14 tests)
- [x] Task 9 — verified end to end on a Fairphone 3; found and fixed a double-cook-count bug live,
      and (post-walkthrough) the missing "set as default" checkbox in the save-scaling dialog

## Phase 7 — Polish  <-- DONE
Plan: `docs/private/plans/2026-09-02-phase-7-polish.md`. It opens with a direct audit of the current
tree — translations turned out to already be complete (a scripted key-parity check found zero
mismatches across every module); the real gaps are two hardcoded `Text("OK")` calls, 23 icons with
`contentDescription = null` (some legitimately decorative, several genuinely missing), two declared
but unused motion constants, empty `docs/public`/`docs/manual` folders, a stale README, a CI
workflow with a hand-maintained test list, and no release signing config.

- [x] Task 1 — the last translation/CI-hygiene gaps (2 hardcoded strings, CI uses `verifyAll`, README refresh)
- [x] Task 2 — accessibility pass. Added real content descriptions on 4 icon-only controls
      (`cook_decrease_servings`/`cook_increase_servings`, `shopping_more_actions`,
      `recipe_detail_more_actions`); left icons beside visible text as `null` on purpose (adding a
      description there would double-announce). On-device TalkBack walkthrough on the Fairphone 3
      (via `uiautomator dump`, the same accessibility-node data TalkBack reads) found and fixed
      **three real bugs invisible to a static grep**: Settings' theme radio rows and its
      dynamic-colour switch had no merged semantics (TalkBack announced "Radio button, not checked"
      with no label) — fixed with `Modifier.selectable`/`toggleable` + `role` on the row and the
      inner control's own click handler set to `null`; cooking mode's "Ingredienti" FAB text was
      visible but never exposed to accessibility at all — fixed with an explicit
      `contentDescription` reusing the existing string. Large-text tested at `font_scale` 1.3 (the
      device's real ceiling) and 2.0 (a harder stress test): nothing truncates, clips, or pushes a
      primary action off-screen at either scale; the only cosmetic issue is the three action-button
      labels on the scale screen wrapping awkwardly at 200% (not a phase-7 blocker — flagged as a
      possible follow-up polish item).
- [x] Task 3 — the two animations `ProPortionMotion` already promised. `WarningRow`'s three call
      sites (oven advisory ×2, per-line warning) now animate in/out with `AnimatedVisibility`
      (`BADGE_ENTER_MILLIS`); the scaled-quantity text crossfades between two pre-formatted strings
      via `AnimatedContent` (`QUANTITY_COUNT_MILLIS`) — never an interpolated number, so no
      arithmetic entered the composable. A `remember`-scoped "last known text" (keyed per line for
      the per-line warning) makes the exit animation fade out the real prior message instead of
      blanking. Verified live on device: mid-crossfade screenshots captured, no stale content.
- [x] Task 4 — `docs/public/{it,en}/{README,privacy,changelog}.md` — every feature claim traced
      back to the design spec and this file's own completed phases; GPLv3 stated; no employer
      mention. Screenshots reused between `it/` and `en/` since both show the Italian-locale UI —
      a real English-locale recapture is a fair follow-up, not a phase-7 blocker.
- [x] Task 5 — `docs/manual/{it,en}/{manuale,manual}.md`, all ten flows, one worked example (a
      simple apple cake) throughout, every quoted button/label checked against the real
      `values-it/strings.xml`. **Screenshots deliberately deferred**: Marco wants the visual design
      reviewed first, so capturing now would mean redoing them after a revision. ~20 placeholders
      mark exactly where each screenshot goes and what it must show; the English manual's intro
      notes the real screenshots need an English-locale device build to match, not a relabelled
      Italian one. Next step whenever the design settles: connect the Fairphone 3 unlocked, follow
      each placeholder's description, drop the PNGs into `docs/manual/{it,en}/screenshots/`.
- [x] Task 6 — `docs/private/localization.md`, `release-checklist.md`. Found a real gap while
      writing it: `AppCompatDelegate.setApplicationLocales` is never called anywhere and Settings
      has no language picker — the spec's "per-app language independent of system" is only
      half-built (`PreferencesRepository.setLanguage`/`UserPreferences.language` exist, unused by
      any screen). `docs/public/{it,en}/README.md` and `changelog.md` (task 4) claimed this worked;
      corrected all four files to say the language follows the system, with the per-app override
      "ready at the data layer but not yet reachable from any screen." A UI + wiring for this is a
      fair v1.1 follow-up, not a phase-7 blocker.
- [x] Task 7 — release signing mechanism. `app/build.gradle.kts` reads
      `RELEASE_STORE_FILE`/`RELEASE_STORE_PASSWORD`/`RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD` from
      `local.properties` (already gitignored, machine-specific — Marco's call to fill in with a
      real keystore whenever he has one); absent or incomplete, `assembleRelease` stays unsigned
      exactly as before. No keystore, password, or template file was created.
- [x] docs/private (architecture, data model, scaling engine, format, ADRs) — done in phase 5

## Phase 8 — Ingredient catalogue  <-- DONE
Spec: `docs/private/specs/2026-09-03-ingredient-catalogue-design.md`. Plan:
`docs/private/plans/2026-09-03-phase-8-ingredient-catalogue.md`. Marco's original ask: a
comprehensive, correctly-translated pre-populated ingredient list, fast autocomplete on entry.

- [x] Task 1 — `IngredientCategory` enum (16 values, foundation-only) + `Ingredient` gains
      `key`/`isBuiltIn` (mirrors `Tag`) (4 tests, reviewed clean)
- [x] Task 2 — `IngredientEntity`/`Converters` mirror the model change; schema bumped to version 2;
      additive `Migration(1, 2)` (columns only at this point) + `MigrationTestHelper`-based test —
      the brief's guessed 3-arg constructor was deprecated in the real `room-testing:2.8.4`, the
      implementer found and used the real 2-arg one instead
- [x] Task 3b (ad hoc, not in the plan) — Task 1's constructor change broke 8 test fixtures in
      `:core:domain`/`:core:transfer`/three feature modules the spec never scoped; fixed as one
      batched dispatch
- [x] Task 3 — `BuiltInIngredientNamer` (`:core:domain`) + `AndroidIngredientNamer` (`:core:ui`,
      `Resources.getIdentifier` rather than a 477-branch `when`, which detekt's `LongMethod` limit
      would reject) + the first 48 real translated entries
- [x] Task 4 — `EntityMappers`: built-in rows resolve both `name` *and* `normalisedName` at read
      time (not just `name`) — the stored `normalised_name` would otherwise go stale the moment the
      app language changes
- [x] Task 5 — `IngredientRepositoryImpl`/`RecipeRepositoryImpl` take the namer; `findOrCreate`
      rewritten to dedupe against the resolved catalogue in Kotlin instead of raw SQL (mirrors
      `TagRepositoryImpl.findOrCreateUserTag`); found mid-task that `ShoppingRepositoryImpl` also
      called `IngredientEntity.toDomain()` directly — a fourth call site the spec's ripple analysis
      missed — folded into this task
- [x] Task 6 — seed data (`ingredients.json`) + shared seeding function called from *both*
      `Room.Callback.onCreate` and `Migration(1, 2)` — `onCreate` never fires for a database that
      already exists, so seeding only from there would leave every upgrading install (including
      Marco's own Fairphone) with an empty catalogue forever
- [x] Task 7 — expanded the catalogue from 48 to **477 entries** across all 16 categories, each
      with a natural (not literal/machine-translated) Italian name; guarded by a new
      `IngredientResourceConsistencyTest` asserting every seeded key resolves in both languages
- [x] Task 8 — `.proportion` export/import: built-in ingredients now travel by `builtin:<key>`
      (mirrors the tags' existing mechanism) instead of by their current-language literal name, so
      sharing a recipe between two devices set to different app languages binds to the same seeded
      row instead of creating a duplicate; an unrecognised key falls back to a literal ingredient
      (an ingredient line, unlike a tag, isn't optional, so it can't just be dropped)
- [x] Task 9 — `verifyAll` and `assembleRelease` both green; verified live on the Fairphone 3
      **against its own real pre-existing recipes**: schema migration ran with zero data loss,
      autocomplete surfaced real seeded suggestions in Italian ("farin" → Farina 00, Farina di
      mandorle, ...) and English ("choc" → Chocolate chips, Dark chocolate, ...) with an instant
      language switch, built-in tags translated correctly too, no crash in logcat
- [x] Final whole-plan review (the step this project runs once per phase, after every task's own
      review) — found two real Critical bugs in pre-existing code no single task's diff could have
      caught: recipe saves were silently overwriting a built-in ingredient's seeded placeholder row
      with current-language text, and typing certain ingredients' raw English key (e.g. "almond",
      when the catalogue displays "Almonds"/"Mandorle") could silently fail and later crash a save
      with a foreign-key error. Both fixed in one dispatched fix wave, with two new regression tests
      each independently proven to actually catch its bug (revert the fix, watch the test fail,
      restore it, watch it pass — done twice, by both the fixer and a second reviewer). Also
      corrected 7 translation nits and triaged every minor finding parked during the 9 tasks.
      Two things were surfaced to Marco rather than fixed in the same rush — see the spec's §8 and
      the "What is next" note above.

Full execution ledger (every ruling, every gap found mid-execution, every review verdict):
`.superpowers/sdd/2026-09-03-phase-8-ingredient-catalogue/progress.md`.

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
