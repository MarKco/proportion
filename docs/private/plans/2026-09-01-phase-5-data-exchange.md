# ProPortion Phase 5 — Data Exchange Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recipes leave the app and come back intact — as readable text, as a `.proportion` file another ProPortion user can import, and as a full backup the owner can restore.

**Architecture:** A new `:core:transfer` module owns the wire format and nothing else: pure serialisation, no Android, no database. `:core:data` wraps it in a repository that resolves ingredients and tags against the catalogue on import. The settings screen drives the Storage Access Framework, so the app never asks for a storage permission.

**Tech Stack:** kotlinx.serialization, Storage Access Framework (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`), `FileProvider` + `ACTION_SEND`, Hilt, Compose.

**Spec:** `docs/private/specs/2026-09-01-proportion-v1-design.md` (§8)

## Global Constraints

- Package root `com.ilsecondodasinistra.proportion`; author Marco Zanetti; never mention his employer.
- **Never `git commit` or `git push`.** Update `docs/private/IMPLEMENTATION-STATUS.md` at the end of each task.
- minSdk 26, compileSdk/targetSdk 36, JVM target 17.
- Navigation stays type-safe; no hardcoded user-facing strings.
- **The format is a contract.** `version` is checked on read: a future version is refused with a clear message, an older one is migrated. `ignoreUnknownKeys = true`, so a file written by a later version still imports minus what this version cannot read.
- Ids are UUIDs and travel with the recipe: that is what makes duplicate detection possible on import.
- `:core:transfer` must not import `android.*` — asserted by a test, like `:core:domain`.

---

## File structure

```
core/transfer/
  ProportionFile.kt            @Serializable wire model + version constant
  ProportionCodec.kt           encode / decode, version checks, migration hook
  PlainTextFormatter.kt        the human-readable share text
  TransferModels.kt            ImportPreview, ImportMode, ImportOutcome
core/data/repository/
  TransferRepositoryImpl.kt    catalogue resolution, duplicate detection, merge vs replace
core/domain/repository/
  TransferRepository.kt        interface used by the UI
feature/settings/
  SettingsViewModel.kt         backup, restore preview, restore apply, theme/language
  SettingsScreen.kt            the first real settings screen
  RestoreDialog.kt             preview + merge or replace choice
feature/recipes/detail/        share as text / share as file in the overflow menu
app/                           FileProvider, ACTION_VIEW intent filter for .proportion
```

Rationale: the codec is separate from the repository because the format has to be testable without a database, and the repository is where the messy part lives — deciding whether an incoming ingredient is the one already in the catalogue.

---

## Task 1: `:core:transfer` — the wire format

**Files:**
- Create: `core/transfer/build.gradle.kts`, `ProportionFile.kt`, `ProportionCodec.kt`, `TransferModels.kt`
- Modify: `settings.gradle.kts`
- Test: `core/transfer/src/test/kotlin/.../ProportionCodecTest.kt`, `NoAndroidDependencyTest.kt`

**Interfaces:**
- Produces: `ProportionCodec.encode(recipes: List<Recipe>): String`, `ProportionCodec.decode(text: String): DecodeResult`, where `DecodeResult` is `Success(recipes)` / `Failure(reason)` with reasons `NotProportionFile`, `FutureVersion(found, supported)`, `Malformed`.

- [ ] **Step 1: Write the failing tests.** A recipe survives export → import unchanged (title, servings, steps, notes, every line's quantity, unit, display text, tags, variants); a null quantity on an approximate line survives; built-in tags travel as `builtin:<key>` and user tags as literal text; a file from a future version is refused by name; unknown extra fields are ignored rather than fatal; truncated JSON is `Malformed`, not a crash; a file that is valid JSON but not a ProPortion file is `NotProportionFile`; the density field round-trips even though v1 never writes it.
- [ ] **Step 2: Run and watch them fail.**
- [ ] **Step 3: Implement** the `@Serializable` wire model (`ProportionFile`, `WireRecipe`, `WireIngredient`, `WireVariant`) plus the codec with `Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }`.
- [ ] **Step 4:** `./gradlew :core:transfer:test detekt`.

---

## Task 2: Plain-text sharing

**Files:**
- Create: `core/transfer/.../PlainTextFormatter.kt`
- Test: `PlainTextFormatterTest.kt`

**Interfaces:**
- Produces: `PlainTextFormatter.format(recipe: Recipe, scaled: ScaledRecipe?, strings: PlainTextStrings): String`, where `PlainTextStrings` carries the few translated words so the formatter stays free of Android.

- [ ] **Step 1: Failing tests** — the text carries title, servings, aligned ingredients and numbered steps; a scaled recipe exports the scaled quantities and says what it was scaled to; approximate lines read as written; the trailing attribution line names the app.
- [ ] **Step 2: Implement**, then run the tests.

---

## Task 3: `TransferRepository` — import with catalogue resolution

**Files:**
- Create: `core/domain/repository/TransferRepository.kt`, `core/data/repository/TransferRepositoryImpl.kt`
- Modify: `core/data/di/DataModule.kt`, `core/data/build.gradle.kts`
- Test: `core/data/src/test/kotlin/.../TransferRepositoryTest.kt`

**Interfaces:**
- Produces: `exportAll(): String`, `exportOne(recipeId): String`, `preview(text): ImportPreview`, `import(text, mode: ImportMode): ImportOutcome`, with `ImportMode.MERGE` / `ImportMode.REPLACE_ALL`.

- [ ] **Step 1: Failing tests** — a preview reports how many recipes the file holds and how many ids are already present, without writing anything; merge keeps existing recipes and adds the new ones; merge skips a recipe whose id is already present; replace-all empties the library first; an incoming ingredient matching an existing normalised name reuses the catalogue row rather than duplicating it; an incoming built-in tag binds to the seeded tag of the same key; an incoming user tag is created; a malformed file returns a failure and leaves the database untouched.
- [ ] **Step 2: Implement,** then run `./gradlew :core:data:testDebugUnitTest`.

---

## Task 4: Settings screen — backup and restore

**Files:**
- Create: `feature/settings/SettingsUiState.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`, `RestoreDialog.kt`, strings
- Modify: `feature/settings/build.gradle.kts`
- Test: `SettingsViewModelTest.kt`, `SettingsScreenTest.kt`

- [ ] **Step 1: Failing ViewModel tests** — theme mode and dynamic colour write through to preferences; requesting a backup produces the file text; a restore preview moves the state into a confirmation step; confirming with MERGE calls the repository with MERGE; cancelling writes nothing; a failed decode surfaces a message rather than a silent no-op.
- [ ] **Step 2: Implement the ViewModel.**
- [ ] **Step 3: Implement the screen** — theme (system / light / dark), dynamic colour toggle, language, "Back up recipes", "Restore from a backup", about section with version and author. The file pickers are `rememberLauncherForActivityResult` with `CreateDocument`/`OpenDocument`, and the file is read and written on `Dispatchers.IO`.
- [ ] **Step 4: The restore dialog** — states the counts from the preview and offers Merge or Replace all, with a second confirmation for Replace all.
- [ ] **Step 5: Compose tests** — the toggles render and report; the restore dialog shows the counts and reports the chosen mode.

---

## Task 5: Sharing and opening files

**Files:**
- Modify: `feature/recipes/detail/RecipeDetailScreen.kt` and its ViewModel (share as text, share as file), `feature/cook/CookScreen.kt` (share the scaled version)
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/main/AndroidManifest.xml` (FileProvider, `ACTION_VIEW` filter for the extension)
- Test: `app/src/test/kotlin/.../ImportIntentTest.kt`

- [ ] **Step 1:** Share as text and share as `.proportion` from the recipe overflow menu, through `FileProvider` and `ACTION_SEND`.
- [ ] **Step 2:** Register the intent filter so opening an attachment lands in the app, and handle the incoming `Uri` in `MainActivity` by routing to the restore preview.
- [ ] **Step 3: Test** that an incoming `content://` intent produces an import preview.
- [ ] **Step 4:** Full check, install on the device, export a recipe and re-import it by hand.

---

## Task 6: Developer documentation

**Files:**
- Create: `docs/private/architecture.md`, `module-map.md`, `data-model.md`, `scaling-engine.md`, `proportion-format.md`, `contributing.md`
- Create: `docs/private/adr/0001-multi-module.md`, `0002-scaling-engine-in-domain.md`, `0003-type-safe-navigation.md`, `0004-density-deferred-to-v2.md`

- [ ] **Step 1:** Write them in English, short and specific, describing what exists rather than what was planned.
- [ ] **Step 2:** Link them from the README and from `IMPLEMENTATION-STATUS.md`.

---

## Self-review notes

- Spec coverage: §8 format → Task 1; plain text → Task 2; import semantics → Task 3; backup/restore UI → Task 4; sharing and attachment opening → Task 5; §11 developer docs → Task 6.
- Deliberately deferred: `docs/public` and `docs/manual` stay for phase 7, when the screens they describe are final.
