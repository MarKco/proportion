# History

A phase-by-phase record of what shipped and why, condensed from the original per-phase design specs
and implementation plans. Those originals are not kept in the working tree any more — recover them
from git history if the full step-by-step ever matters. For phases 6, 7 and 8, the complete
per-task execution ledger (every ruling made mid-implementation, every review verdict) still lives
at `.superpowers/sdd/2026-09-0{1,2,3}-phase-{6,7,8}-*/progress.md`.

For the living checklist and what's next, see `STATUS.md`. For the standing architectural
decisions, see `DECISIONS.md`. For the current shape of the system, see `ARCHITECTURE.md`.

## Phase 0 — Design

Brainstorming, a visual blueprint (published separately as a design artifact), and the approved
v1 design spec: seven entities (all UUID-keyed, so exported recipes de-duplicate across devices),
typed units with four categories (MASS/VOLUME/COUNT/APPROXIMATE), a scaling engine with four
constraint modes, an oven-scaling advisory, and density conversion explicitly deferred to v2 but
schema-prepared (see `DECISIONS.md` §4). Success criteria: rescale a recipe in under 30 seconds; no
rescale ever silently produces an impossible quantity; a recipe exported on one device imports
losslessly on another; every domain rule TDD'd.

## Phase 1–2 — Foundations and domain

Gradle multi-module scaffolding (AGP 9, Now-in-Android layout), the design system (pastel palette,
Material You + WCAG contrast tests), an adaptive/monochrome app icon (a clipart 3D pie with
candles), four-tab navigation, green CI. Then the domain layer, test-first: the model and typed
units, the scaling engine (converter, formatter, four constraint modes, discrete snaps), the oven
advisory rule, Room schema/DAOs/migrations with built-in tags seeded, and repositories with Hilt
wiring. No UI beyond empty tabs — the deliverable was a passing test suite and a scaling engine
that was already right.

## Phase 3 — Enter and browse

`:core:ui` (tag labels, unit picker, state bodies, ingredient rows); the recipe editor
(`:feature:editor`) with validation, ingredient autocomplete and a discard-changes dialog; the
recipe list with debounced search, tag chips, an ingredient filter sheet and sort; recipe detail
with formatted quantities. This is where the app became genuinely usable: enter a recipe, find it
again.

## Phase 4 — Cook this recipe

`:feature:cook`: the four constraint modes (servings / ingredient / factor / pantry) over a
live-recomputing list, warnings inline with snap chips, the oven advisory banner with its
tin-diameter hint, the scaled card (same shape as the detail, new quantities), saving a scaling as
a variant, and marking the recipe cooked. No arithmetic was added in this phase or after — every
number on screen has always come from `ScaledRecipe`.

## Phase 5 — Data exchange

`:core:transfer`: the `.proportion` codec (version checks, tolerant parsing on unknown fields,
refusal of unknown units), the plain-text share formatter (aligned quantities, scaled-variant
attribution), `TransferRepository` (preview without writing, merge vs. replace-all, catalogue and
tag resolution). The settings screen gained backup/restore through the Storage Access Framework,
and sharing/receiving `.proportion` files was wired end to end. This phase also produced the first
version of the developer docs now condensed into this file and `ARCHITECTURE.md`.

## Phase 6 — Home, shopping, cooking mode

`DashboardSummariser` + `RecipePicker` (`:core:domain`) turn the recipe list into dashboard numbers,
course slices and a random pick in one pure function; `DonutChart` (`:core:designsystem`) draws it.
Home's four cards, the shopping list (`ShoppingListFormatter` shares row-alignment code with the
plain-text recipe formatter via `AlignedRow.kt`), "add to shopping list" from the scale screen,
cooking mode (screen-always-on, checkable steps, a scaled-ingredients sheet — the scaling itself
travels the nav route as Base64 JSON), and the default-variant banner on the recipe detail with a
cook counter. End-to-end device verification on a Fairphone 3 found and fixed a double-cook-count
bug (`markCooked` was firing both when the scaled card opened and when cooking mode was entered;
kept only the cooking-mode call, per the spec's own attribution) and a missing "set as default"
checkbox in the save-scaling dialog.

## Phase 7 — Polish

Opened with a direct audit of the tree rather than assuming it was clean, and found: translations
already complete (a scripted key-parity check found zero mismatches) but two hardcoded
`Text("OK")` calls; 23 icons with `contentDescription = null` (several legitimately missing, not
just decorative); two declared-but-unused motion constants; empty `docs/public`/`docs/manual`
folders; a stale README; a CI workflow with a hand-maintained test list already drifted from
reality; no release signing config.

- The last translation/CI-hygiene gaps closed; CI switched to the `verifyAll` aggregate task.
- Accessibility: content descriptions added to 4 icon-only controls. A live on-device TalkBack
  walkthrough (via `uiautomator dump`) found three bugs no static grep would have: Settings' theme
  radio rows and dynamic-colour switch had no merged semantics (fixed with
  `Modifier.selectable`/`toggleable` + `role`), and cooking mode's "Ingredienti" FAB text was never
  exposed to accessibility at all. Large-text tested at scale 1.3 (the device's real ceiling) and
  2.0 (stress test): nothing clipped or fell off-screen except cosmetic wrapping on Cook's three
  action buttons, flagged rather than fixed here.
- The two animations `ProPortionMotion` already promised but nothing used: `WarningRow` now
  animates in/out (`AnimatedVisibility`), scaled quantities crossfade instead of jumping
  (`AnimatedContent`), verified with mid-crossfade screenshots.
- `docs/public/{it,en}` written, every claim traced back to the spec and to what had actually
  shipped; GPLv3 stated.
- `docs/manual/{it,en}` written for all ten user flows around one worked example (a simple apple
  cake), screenshots deliberately deferred pending a visual-design review — captured later,
  alongside this consolidation, once real device screenshots were taken.
- `docs/private/localization.md` and `release-checklist.md` written. Writing `localization.md`
  found a real gap: the spec's "per-app language independent of system" was only half-built — the
  manifest declared `localeConfig` (enough for Android 13+'s own per-app language picker) but no
  in-app picker called `AppCompatDelegate.setApplicationLocales`. Corrected the public docs to say
  the language followed the system, with the override "ready at the data layer but not reachable
  from any screen" — later built for real, see "Added after phase 7" below.
- Release signing: `app/build.gradle.kts` reads four `RELEASE_*` properties from
  `local.properties` (gitignored); `assembleRelease` falls back to unsigned when any is missing.

### Added after phase 7, 2026-09-03: per-app language selection

Settings gained a real language picker (System / Italiano / English). The dead
`PreferencesRepository.setLanguage`/`UserPreferences.language` fields phase 7 found unused were
removed; `LocaleController` (`:core:domain` interface, `AppCompatLocaleController` in `:core:ui`)
is the one place the choice lives now. Real gotcha, found only by switching the language live on a
Fairphone 3: `AppCompatDelegate.setApplicationLocales()` alone does not update the *running* app on
a plain `ComponentActivity` — its live-apply hooks target `AppCompatActivity`, and making
`MainActivity` one crashes immediately (`Theme.ProPortion` is not a `Theme.AppCompat` descendant).
The fix that actually works: call the platform `android.app.LocaleManager` directly on API 33+ (the
same mechanism the device's own Settings > Apps > Language screen uses — applies to the running
process immediately after the activity recreates), and rely on `AppCompatDelegate`'s own persisted
storage for API 26–32, where a language change takes effect from the next cold start since no
platform API exists to apply it live.

### Added after phase 7, 2026-09-03: app theme picker

A second appearance choice, enabled only while dynamic colour (Material You) is off: four named
static themes — Pastel, Vivid, Playful, and a High Contrast theme built to the stricter WCAG AAA
bar (7:1) rather than the AA bar (4.5:1) the other three meet — each with light/dark variants.
`ProPortionTheme` picks one of eight `ColorScheme`s instead of one static pair; all eight are
checked in `ColorContrastTest.kt`. The Settings row stays visible but disabled while dynamic colour
is on (Material's standard 0.38 alpha), rather than disappearing, so the setting's existence isn't
hidden from someone who might want dynamic colour off.

## Phase 8 — Ingredient catalogue

Marco's ask: a comprehensive, correctly-translated pre-populated ingredient list, fast autocomplete
on entry. 477 built-in ingredients across 16 categories (`IngredientCategory`, foundation-only — no
UI surfaces it yet), translated Italian/English the same way built-in tags already were:
`Ingredient` gained `key`/`isBuiltIn` (mirroring `Tag`), resolved at the **repository boundary**
through a new `BuiltInIngredientNamer` using `Resources.getIdentifier` (a 477-branch `when` would
have blown detekt's `LongMethod` limit). Both `name` and `normalisedName` are resolved at read time,
not frozen at seed time, because a lookup frozen in one language would go stale the moment the app
language changes — the seeded DB columns hold an inert placeholder, never read. Seed data loads via
a function shared between `Room.Callback.onCreate` (fresh installs) and an additive `Migration(1,
2)` (upgrades) — `onCreate` alone would have left every upgrading install, including Marco's own,
with an empty catalogue forever. The `.proportion` format gained the matching fix in the same phase
(Marco's own call while reviewing the spec): built-in ingredients travel by `builtin:<key>`, not by
their current-language literal name, mirroring how built-in tags already worked.

**Two real Critical bugs found by the final whole-plan review**, in pre-existing code no single
task's diff touched: `RecipeRepositoryImpl.upsert()` was silently overwriting a built-in
ingredient's seeded placeholder row with current-language text on every recipe save; and
`findOrCreate` could silently fail — later crashing a save with a foreign-key error — for
ingredients whose raw catalogue key differs from its displayed name in both languages (e.g.
"almond" vs. "Almonds"/"Mandorle"). Both fixed, each with a regression test proven (by reverting the
fix, watching the test fail, restoring it) to actually catch its bug.

**Two known limitations surfaced rather than rushed**: searching recipes by ingredient name doesn't
find a built-in ingredient by its localised name (`RecipeDao.filtered` reads the catalogue's raw
key column directly in SQL; a proper fix needs it restructured to resolve names in Kotlin); and
picking an autocomplete suggestion always overwrote the line's unit with the ingredient's default,
even over one the user had already typed. Both fixed shortly after, see below.

### Added after phase 8, 2026-09-03: fixed both known limitations, plus UI requests

- Recipe search now resolves built-in ingredient names in Kotlin before querying, so it finds them
  by their localised name.
- Picking an autocomplete suggestion no longer overwrites a unit already in the same category.
- The keyboard no longer hides the suggestion list right after "Aggiungi ingrediente" — the newly
  added row requests focus and scrolls into view exactly once. Getting this right on a real device
  found four real bugs: a zero-width `Rect` passed to `BringIntoViewRequester` silently does
  nothing (must use the row's real measured width); `WindowInsets.isImeVisible` flips true as soon
  as the keyboard *starts* animating in, not once it finishes, so scrolling against a still-shrinking
  viewport undershoots (fixed by polling `WindowInsets.ime` for a few stable frames); keying the
  scroll `LaunchedEffect` on the row's measured size re-fired it on every keystroke since the card
  keeps resizing as suggestions change (fixed by reading the size as a plain polled value instead of
  a recomposition key); and "newly added" was being inferred from the line count going up, which
  also fires when an existing recipe's saved lines finish loading — fixed by having
  `EditorViewModel.onAddLine` set an explicit `justAddedLineId` instead of the screen guessing from
  a size change.
- Tapping an ingredient name no longer glitches upward on every keystroke. Three more real bugs:
  the row was being kept in place by *re-asserting* an oversized `bringIntoView` rect after every
  keystroke, fighting `BasicTextField`'s own per-keystroke scroll — fixed by reserving real
  laid-out space (a `Spacer` that shrinks as suggestion chips grow into it) instead of fighting the
  built-in behaviour; `WindowInsets.ime` includes the navigation bar, which `Scaffold` had already
  subtracted from the content padding, so the raw inset double-counted it; and the actual root
  cause of a row flying off the top — `MainActivity` had no `windowSoftInputMode`, so the system
  panned the whole window on keyboard-open, which a from-scratch offset calculation couldn't see.
  Fixed with `adjustResize` in the manifest and an offset computed against the content's own first
  item rather than the viewport node (whose reported position excludes `Scaffold`'s padding).
- The recipe detail screen gained a standalone edit (pencil) button, always visible, instead of
  requiring the overflow menu.

## Phase 9 — Cross-category unit conversion

Marco's v2 headline feature, named explicitly back when v1 shipped: open a recipe written in cups
and say "I have 200g", or a recipe in ml and ask "how many g". Split across two sessions (the first
ran out of budget mid-flight and shipped only the engine and the Editor flow, skipping the usual
planning-document step by Marco's explicit request to prioritise working code that day).

`Ingredient` gained `itemWeightGrams` (mirrors `densityGramsPerMl`); schema bumped to version 3,
backfilling density and item weight onto existing built-in rows from `docs/densities.json` (477
entries, Marco's own data). `UnitConverter` rewritten to chain through grams as the hub — MASS↔VOLUME
via density, COUNT↔MASS/VOLUME via item weight — with `requirementFor(from, to, ingredient)`
telling a caller exactly what's missing so the UI can offer a "density unknown" prompt only when it
would help. A real bug in `requirementFor` itself: the first version answered structurally (what a
unit pair needs in general) rather than checking what the given ingredient actually had, so Cook's
proactive check offered the prompt even when density was already known — caught by a Cook
regression test. A second, pre-existing bug: `DefaultRecipeScaler` was calling `convert(...)`
without ever passing the ingredient, so cross-category Cook constraints were silently impossible
even with density known.

The conversion rule now applies in all three places it was promised: the Editor (changing a line's
unit re-expresses the quantity, prompting for density on demand), Cook's "I have" ingredient
constraint (gained a `UnitPicker` it didn't have before), and the recipe detail (tap a row to open a
read-only conversion sheet). One real bug found live on-device: after answering the density prompt
in the Detail sheet, the conversion didn't refresh, because the reactive `Recipe` flow didn't
re-emit fast enough for the sheet to see the new density on the next frame — fixed with an explicit
override map applied immediately on confirm, the same "trust the local write, don't wait for the
round trip" pattern Editor/Cook already used, verified to survive an app restart.

Imperial units (`OUNCE`, `POUND`, `FLUID_OUNCE`, `PINT`, `QUART`, `GALLON`) were added on request
mid-session, wired with no call-site changes beyond naming, since every unit picker already iterates
`MeasureUnit.entries` and the `.proportion` codec accepts any entry by name.

**Also fixed live this session, unrelated to conversion:** Cook's three action buttons, which
wrapped onto three lines each at a third of the screen width, are now stacked full-width;
`DonutChart`'s centre caption, which overlapped the ring on Home, is now optional and the numbers
moved to a row below the chart.

### Editor: no unit pinned in advance, 2026-09-03

A fresh ingredient line used to open with `GRAM` already selected, so typing "300" and then picking
cups converted a number that had never actually been in grams. `EditorLine.unit` is now nullable and
starts unset; `isUnitChosen` separates the user's own pick from a catalogue-suggested hint, so the
first deliberate pick takes the typed quantity at face value instead of converting it. Saving now
requires a unit on every named line rather than silently defaulting to one — Marco's explicit choice.

## Phase 10 — Sync via a Syncthing-watched folder

In progress. Design: raw SQLite file explicitly rejected — Room's WAL mode plus Syncthing's
file-level (not transaction-level) sync would corrupt the database on any sync mid-write, and
last-write-wins at the whole-file level could lose an entire session's edits, not just a real
conflict. Reuses the `.proportion` wire format instead: one file per entity
(`recipe-<id>.proportion`, `ingredient-<id>.proportion`, `tag-<id>.proportion`) in a SAF-picked
folder that an external app like Syncthing keeps in sync, conflicts resolved by `updatedAt`
(silent, last-write-wins), deletions propagated via a new `Recipe.deletedAt` tombstone (ingredients
and tags have no delete UI today, so no tombstone needed for them). A periodic `WorkManager` job
(~4h, Marco's explicit choice over an on-resume trigger — there's no push notification between
devices either way, so a period is equivalent to a more aggressive on-resume check but cheaper on
battery) plus a manual "Sync now" button, and an in-app sync error log shareable via the system
share sheet.

- Schema migration 3→4: `Recipe.deletedAt`, `Ingredient.updatedAt`, `Tag.updatedAt`. Every existing
  read query now filters `deleted_at IS NULL`; a real bug found in the process:
  `IngredientDao.observeInUse`/`observeInUseCount` counted an ingredient as in-use even when its
  only referencing recipe was soft-deleted.
  `RecipeRepositoryImpl.delete` became a soft delete.
- New pure module `:core:sync` (zero dependencies, not even `:core:model` — entirely
  entity-agnostic): `SyncableState(updatedAt, deletedAt)` plus
  `decideSyncAction(local, remote): SyncAction` (`Insert`/`Overwrite`/`Delete`/`Skip`) covers every
  case, including "undelete" (a locally-tombstoned row revived by a newer incoming write) with no
  special case — it falls out of simply not inspecting `local.deletedAt`.
- `SyncRepositoryImpl` (`:core:data`, real SAF I/O via `androidx.documentfile`): `syncNow()` pulls
  before it pushes, then fully pushes every local recipe/literal-ingredient/literal-tag — this
  single deliberate design choice is what makes both "turn sync on" and "a fresh install pointed at
  an already-populated folder" work with no separate first-run code path. Three real bugs found by
  this task's own tests (not `:core:sync`'s, already green in isolation): `syncNow()` originally
  pushed before pulling, clobbering a newer remote file with an older local one before the pull
  could ever read it; `WireRecipe.toRecipe()` never mapped the newly-added `updatedAt`/`createdAt`
  fields, so every resolved recipe came back with `updatedAt = 0` and **every conflict would have
  been decided as if the incoming file were always the oldest possible** — sync would have looked
  like it worked while silently never applying an update; and `DocumentFile.createFile(mimeType,
  name)` can append an extension derived from a generic mime type (`.bin`, observed under
  Robolectric and a plausible real-provider behaviour too), breaking the `findFile(name)` lookup a
  re-export needs to overwrite instead of duplicate — fixed with a vendor-specific mime type used
  only for `createFile`, leaving the share-intent mime type untouched. Injecting `SyncRepository`
  directly into the three repositories it hooks into (`RecipeRepositoryImpl.upsert`/`delete`, etc.)
  creates a Dagger cycle back through `TransferRepository`; broken with `Provider<SyncRepository>`,
  the standard way to defer a dependency that's only needed at call time.
- Settings gained a "Sincronizzazione" section: toggle, folder picker, "Sincronizza ora", an error
  banner with "Condividi log" reusing the existing `RecipeSharing.shareText` mechanism.
- `SyncScheduler` (`:core:domain` interface) / `WorkManagerSyncScheduler` + `SyncWorker`
  (`:core:data/sync/`, co-located with `SyncRepositoryImpl` rather than `:app`, which has no other
  reason to know WorkManager exists). Manifest gotcha: lint's `RemoveWorkManagerInitializer` forced
  removing the default `androidx.startup` WorkManager initializer via `tools:node="remove"` —
  required whenever `Application` implements `Configuration.Provider`, and only lint catches it,
  not a runtime crash.
- Verified on a Fairphone 3 against its own real pre-existing data: enabling sync and picking a real
  folder exported everything immediately (content confirmed via `adb shell cat`, correct filenames
  with no `.bin` suffix — the mime-type fix holds on a real SAF provider, not just Robolectric); a
  simulated fresh install (`pm clear`, empty DB, revoked SAF grant) followed by re-enabling sync and
  re-picking the same now-populated folder correctly re-imported everything.
- **Not part of the wire format, worth remembering**: `cookCount`/`lastCookedAt`/`isFavourite` never
  travelled through `.proportion` (true for backup/restore too, predates this phase) — after a
  sync-driven import those reset on the receiving device. Recipe content syncs; local usage stats
  don't.
- **Still open, explicitly Marco's to run**: the real two-device Syncthing test — concurrent edits
  and conflict resolution, deletion propagation, the error banner on a revoked/missing folder, and
  confirming the periodic job actually cancels when the toggle goes off.
