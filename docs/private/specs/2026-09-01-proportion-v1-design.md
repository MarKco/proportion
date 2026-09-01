# ProPortion — v1 Design Spec

- **Author:** Marco Zanetti
- **Date:** 2026-09-01
- **Status:** Approved, ready for implementation planning
- **Package:** `com.ilsecondodasinistra.proportion`
- **Companion:** visual blueprint (architecture + screen flow), published separately

---

## 1. Purpose and scope

ProPortion is an offline Android app that rescales cooking recipes. The user stores a recipe with
its ingredient quantities and its intended number of servings, then re-derives every quantity from a
single constraint: a different serving count, a fixed amount of one ingredient, a plain multiplier,
or the set of ingredients actually available in the pantry.

The value the app adds over mental arithmetic is **culinary correctness**: it knows that eggs cannot
be halved, that "a pinch of salt" does not scale, and that a cake baked at 1.5× does not bake 1.5×
longer.

### In scope for v1

Recipe entry, recipe browsing with combined filters, recipe scaling with four constraint modes,
saved scale variants, plain-text and `.proportion` sharing, full backup and restore, a summary
dashboard, a persistent shopping list, a cooking mode, favourites with a usage counter, Italian and
English localisation, and an oven-scaling advisory.

### Explicitly out of scope for v1

Accounts, cloud sync, recipe photos, ingredient placeholders inside procedure steps, importing a
recipe from pasted free text, and mass↔volume conversion via densities. Sections 12 and 13 describe
how the design stays ready for the deferred items.

### Success criteria

1. A user can enter a recipe and rescale it to a different serving count in under 30 seconds.
2. No rescale ever silently produces an impossible quantity: discrete non-integers are always
   flagged with a proposed fix.
3. A recipe exported on one device imports on another with no data loss.
4. Every domain rule is covered by a JVM unit test written before its implementation.

---

## 2. Fixed decisions

| Area | Decision |
|---|---|
| Ingredients | Normalised catalogue: a reusable `Ingredient` entity, autocompleted during entry |
| Discrete quantities | Flag with a badge and propose a snap that recomputes the whole recipe |
| Saved scalings | Stored as variants; the original recipe is never overwritten |
| Gradle | Multi-module, Now-in-Android layout |
| Scale UI | Mode selector (Servings / Ingredient / Factor / Pantry) with a live-recomputing list |
| Multi-constraint | "With what I have" mode, taking the limiting factor |
| Units | Typed units with categories, conversion inside a category only |
| Extra features | Favourites + usage counter, cooking mode, persistent shopping list |
| Restore | Preview, then user chooses Merge or Replace all |
| Theme | Material You dynamic colour on by default, brand pastel palette as fallback and as a toggle |
| Quality | TDD on the domain layer, GitHub Actions CI |
| minSdk / target | 26 / 36 |
| Oven advisory | In v1 (see §7.4) |
| Density conversion | Deferred to v2, schema and API prepared in v1 (see §12) |

---

## 3. Domain model

Seven entities. All primary keys are **UUID strings**, not autoincrement integers, so that exported
recipes can be de-duplicated on import across devices.

### 3.1 Entities

**Recipe** — `id`, `title`, `servings` (Int, nullable for recipes that are not per-person),
`steps` (ordered list of strings, serialised), `notes`, `isFavourite`, `cookCount`,
`lastCookedAt`, `createdAt`, `updatedAt`.

**RecipeIngredient** — one row per ingredient line: `id`, `recipeId`, `ingredientId`, `position`,
`quantity` (Double, nullable when the unit is approximate), `unit` (MeasureUnit), `displayText`
(nullable, preserves what the user typed, e.g. "½ bustina"), `note` (e.g. "a temperatura ambiente").

**Ingredient** — the catalogue: `id`, `name`, `normalisedName` (lowercase, trimmed, accent-folded —
used for lookup and de-duplication), `defaultUnit`, `densityGramsPerMl` (Double, **nullable, unused
in v1**, see §12).

**Tag** — `id`, `key` (nullable; set for built-ins), `name` (nullable; set for user tags),
`isBuiltIn`, `colorIndex`. Built-in tags carry a stable key resolved through `strings.xml` so they
translate; user tags carry literal text that is never translated. Exactly one of `key`/`name` is
non-null.

**RecipeTagCrossRef** — many-to-many join between Recipe and Tag.

**ScaleVariant** — `id`, `recipeId`, `label`, `constraintType`, `constraintPayload` (serialised
`ScaleConstraint`), `isDefault`, `createdAt`. A variant stores **the constraint, not the computed
quantities**, so it stays correct if the recipe is later edited. At most one variant per recipe has
`isDefault = true`: when set, opening the recipe shows that scaling instead of the original, with a
persistent "showing: For 6 · view original" affordance. Setting a new default clears the previous
one.

**ShoppingItem** — `id`, `ingredientId`, `quantity`, `unit`, `isChecked`, `sourceRecipeIds`
(serialised list). One persistent list; no list entity is needed in v1.

### 3.2 Built-in tags

Seeded on first run with `isBuiltIn = true` and these keys: `appetizer`, `first_course`,
`main_course`, `side_dish`, `dessert`, `bread_and_leavened`, `preserves`, `drinks`, **`oven`**.
The `oven` tag is not cosmetic — §7.4 keys the baking advisory off it.

### 3.3 Deletion rules

Deleting a recipe cascades to its ingredient lines, tag cross-refs and variants. Ingredients are
never cascade-deleted: they stay in the catalogue but disappear from filter lists, because the
filter list is derived from ingredients actually referenced by at least one recipe. Deleting a
built-in tag is not permitted; deleting a user tag removes its cross-refs.

---

## 4. Units and discrete quantities

`MeasureUnit` is a Kotlin enum. Each entry carries a `category` and a `baseFactor` expressed in the
category's base unit.

| Category | Base | Units (baseFactor) | Scaling behaviour |
|---|---|---|---|
| `MASS` | gram | `GRAM` 1, `KILOGRAM` 1000 | Continuous. Rounded for readability: to 1 g below 100 g, to 5 g above. |
| `VOLUME` | millilitre | `MILLILITRE` 1, `LITRE` 1000, `TEASPOON` 5, `TABLESPOON` 15, `CUP` 240, `GLASS` 200 | Continuous. Domestic units are volume units, so **cup ↔ ml already converts in v1**. Displayed with human fractions (½, ⅓, ¼) rather than decimals. |
| `COUNT` | piece | `PIECE`, `EGG`, `CLOVE`, `SLICE`, `LEAF`, `SACHET`, `JAR` (all 1) | **Discrete.** A non-integer result raises a warning and a snap proposal. |
| `APPROXIMATE` | — | `TO_TASTE`, `PINCH`, `DRIZZLE` | **Never scaled**, never used to derive a factor, passed through unchanged. |

**Conversion rule:** only within a category. Mass↔volume is refused in v1 (returns `null`), because
without a density it is guesswork. Internally every quantity is normalised to its category base
unit; the chosen display unit is presentation only.

**Snap tolerance:** if the scaled value of a discrete unit is within 5% of an integer, it is snapped
silently. Beyond 5%, the warning and the snap proposal are shown.

**Too-small threshold:** below 0.5 g / 0.5 ml, the line renders as "less than a pinch" and raises
`TooSmallToMeasure`.

---

## 5. Scaling engine

Lives in `:core:domain` as pure Kotlin — no `android.*` imports — so it is unit-testable in
milliseconds. It is the first thing built and the most heavily tested.

### 5.1 API

```kotlin
sealed interface ScaleConstraint {
    data class ByServings(val target: Double) : ScaleConstraint   // Double: pantry mode yields 4.3
    data class ByIngredient(val lineId: String, val qty: Double, val unit: MeasureUnit) : ScaleConstraint
    data class ByFactor(val factor: Double) : ScaleConstraint
    data class ByAvailability(val have: List<AvailableAmount>) : ScaleConstraint
}

data class AvailableAmount(val lineId: String, val qty: Double, val unit: MeasureUnit)
data class Leftover(val lineId: String, val qty: Double, val unit: MeasureUnit)

fun interface RecipeScaler {
    fun scale(recipe: Recipe, constraint: ScaleConstraint): ScaleResult
}

sealed interface ScaleResult {
    data class Success(val scaled: ScaledRecipe) : ScaleResult
    data class Failure(val reason: ScaleError) : ScaleResult
}

data class ScaledRecipe(
    val factor: Double,
    val servings: Double?,
    val lines: List<ScaledLine>,
    val warnings: List<ScaleWarning>,
    val bottleneckLineId: String?,      // ByAvailability only
    val leftovers: List<Leftover>,      // ByAvailability only
    val snapSuggestions: List<SnapOption>,
)

data class ScaledLine(
    val lineId: String,
    val ingredientName: String,
    val originalQty: Double?,
    val originalUnit: MeasureUnit,
    val scaledQty: Double?,
    val displayUnit: MeasureUnit,
    val displayText: String,
    val isScaled: Boolean,              // false for APPROXIMATE lines
)

sealed interface ScaleWarning {
    data class NonIntegerDiscrete(val lineId: String, val exact: Double) : ScaleWarning
    data class TooSmallToMeasure(val lineId: String) : ScaleWarning
    data class NotScalable(val lineId: String) : ScaleWarning
    data class BakingTimeCaution(val factor: Double, val suggestedTinDiameterRatio: Double) : ScaleWarning
}

// Each option carries the alternative factor, not a per-line override:
// accepting a snap re-runs the whole pipeline so the recipe stays in proportion.
data class SnapOption(val lineId: String, val targetQty: Double, val resultingFactor: Double)

sealed interface ScaleError {
    object NonPositiveFactor : ScaleError
    object ConstraintOnApproximateUnit : ScaleError
    object IncompatibleUnit : ScaleError
    object NoServingsDefined : ScaleError
    object EmptyRecipe : ScaleError
}
```

### 5.2 Pipeline

1. **Derive the factor** from the constraint.
   - `ByServings` → `target / recipe.servings`; fails with `NoServingsDefined` if servings is null.
     `target` is a `Double` because the pantry mode reports fractional servings (4.3), but the
     servings stepper only ever produces whole numbers.
   - `ByIngredient` → converts the requested quantity into the line's unit, then divides.
     Fails with `ConstraintOnApproximateUnit` or `IncompatibleUnit`.
   - `ByFactor` → used directly.
   - `ByAvailability` → computes a candidate factor per supplied amount and takes the **minimum**;
     that line becomes `bottleneckLineId`. Approximate lines are excluded. Leftovers are computed
     for every other supplied amount.
2. **Scale each line**, skipping `APPROXIMATE` lines (they emit `NotScalable` only when the user
   explicitly tried to constrain on them).
3. **Normalise and format**: pick the most readable unit within the category, apply the rounding
   rules of §4, render domestic volumes as human fractions.
4. **Check and warn**: discrete non-integers beyond tolerance produce `NonIntegerDiscrete` plus a
   `SnapOption` for each plausible integer (floor and ceiling); below-threshold values produce
   `TooSmallToMeasure`; §7.4 adds `BakingTimeCaution`.

Accepting a snap does not mutate one line: it feeds `ByFactor(option.resultingFactor)` back into the
pipeline.

### 5.3 Edge cases (each a test written first)

Factor ≤ 0; recipe with no servings under `ByServings`; constraint on an approximate unit; recipe
with a single ingredient; recipe with only approximate ingredients; `ByAvailability` with an empty
list, or with an amount larger than needed for every line (factor > 1 is legitimate); an ingredient
line with a null quantity; rounding that would push a discrete value to zero (clamped to 1 with a
warning).

---

## 6. Module structure

```
:app                      navigation host, DI composition, MainActivity
:core:model               plain data classes shared across layers
:core:domain              scaling engine, unit rules, use cases — pure Kotlin
:core:data                repository implementations of :core:domain interfaces
:core:database            Room entities, DAOs, migrations, type converters
:core:datastore           preferences (theme, dynamic colour, language, last-used scale)
:core:designsystem        theme, palette, typography, motion, reusable atoms
:core:ui                  shared Compose components that know about domain models
:core:transfer            .proportion serialisation, plain-text export, backup/restore
:feature:home             dashboard
:feature:recipes          list, search, filters, recipe detail
:feature:editor           create and edit a recipe
:feature:cook             scale screen, scaled card, cooking mode
:feature:shopping         shopping list
:feature:settings         theme, language, backup/restore, tag and ingredient management
```

**Dependency rules.** `:app` depends on every feature. Features depend on `:core:domain`,
`:core:ui`, `:core:designsystem`, `:core:model` — **never on each other**. `:core:data` implements
interfaces declared in `:core:domain` (dependency inversion) and depends on `:core:database`,
`:core:datastore`, `:core:transfer`. `:core:domain` depends only on `:core:model`.

If two features ever need to share code, the shared piece moves into a core module; a horizontal
feature-to-feature dependency is never the answer.

### 6.1 Technical stack

Kotlin 2.x, Compose BOM with Material 3, Navigation Compose (type-safe routes), Hilt for DI, Room
with KSP, DataStore Preferences, kotlinx.serialization, Coroutines and Flow, Gradle version catalog
(`gradle/libs.versions.toml`), detekt, Android Lint.

**Presentation pattern:** one `ViewModel` per screen, exposing `StateFlow<XUiState>` where
`XUiState` is a `sealed interface` covering Loading / Empty / Content / Error. Events go up as
function calls; one-shot effects (navigation, snackbars) travel through a `Channel`. Composables
are stateless and take state plus lambdas.

---

## 7. Screens and flows

Bottom navigation with four destinations: **Home**, **Recipes**, **Shopping**, **Settings**.
Everything else is a detail screen stacked above them.

### 7.1 Recipes (list)

Search field, tag chips, and an ingredient filter sheet. The three filters combine with AND:

- **Free text** — matches recipe title, ingredient names and notes; 200 ms debounce; case- and
  accent-insensitive.
- **Tags** — multi-select chips.
- **Ingredients** — bottom sheet listing every ingredient referenced by at least one recipe, each
  selectable; a recipe matches when it contains **all** selected ingredients.

The result count is always visible ("12 recipes") and a single action clears all filters. Sort
options: recently updated (default), alphabetical, most cooked. A FAB opens the editor.

### 7.2 Recipe editor

Title, servings, tags (built-in chips plus free entry), ingredient lines, and steps. Each ingredient
line is name (autocompleted from the catalogue, creating a new entry when unmatched) + quantity +
unit picker grouped by category. Lines are reorderable and removable. Steps are a reorderable list
of text fields. Validation: title required, at least one ingredient, quantity required unless the
unit is approximate. Unsaved-changes confirmation on back.

### 7.3 Recipe detail

Title, tags, servings, ingredient list, numbered steps, saved variants, favourite toggle, overflow
menu (edit, duplicate, share as text, share as `.proportion`, delete). Primary action: **Cook this
recipe**.

### 7.4 Scale screen ("Cook this recipe")

A segmented control at the top selects the constraint mode; the ingredient list below recomputes
live as the input changes.

- **Servings** — stepper plus direct entry; subtitle shows "from 4 servings · factor ×1.50".
- **Ingredient** — tapping a row opens a numeric input; that row becomes the constraint and every
  other row rescales.
- **Factor** — direct multiplier entry with quick presets (×0.5, ×2, ×3).
- **Pantry** — the user enters how much they have of one or more ingredients; the app takes the
  limiting factor, marks the bottleneck row, states the achievable servings, and lists leftovers.

Warnings render inline on their row: an amber badge, the exact value, and a snap chip
("round to 1 sachet"). Accepting the chip recomputes the whole list, animating the numbers to their
new values.

**Oven advisory.** When the recipe carries the built-in `oven` tag and the factor falls outside the
0.7–1.4 band, the engine emits `BakingTimeCaution` and the scaled card shows a non-blocking notice:
baking time and temperature do not scale proportionally, check for doneness early. It includes a tin
suggestion computed at constant batter depth — **new diameter ≈ current diameter × √factor** (24 cm
at ×1.5 ≈ 29 cm). The notice is informational, never blocking, and never rewrites the steps.

From the scale screen the user can: view the **scaled card** (same layout as the recipe detail, new
quantities, unchanged steps), **save the scaling as a variant** (prompted with a suggested label
such as "For 6"), **add the scaled quantities to the shopping list**, or **enter cooking mode**.

### 7.5 Cooking mode

Keeps the screen on (`FLAG_KEEP_SCREEN_ON`), enlarges type, shows checkable steps, and keeps the
current scaled ingredient quantities one tap away. Increments `cookCount` and sets `lastCookedAt` on
entry.

### 7.6 Home dashboard

Four cards, animated on entry: key numbers plus an animated donut of recipes per course tag;
"Continue cooking" (last cooked recipe with the scale in use); "Most cooked and favourites";
"What shall I cook?" (random pick with a reshuffle animation, filterable by tag). Empty state
invites the user to add a first recipe.

### 7.7 Shopping list

A single persistent list. Items merge per ingredient when units are compatible (300 g + 0.2 kg =
500 g) and stay separate when they are not. Each item remembers which recipes it came from, is
checkable, and the whole list shares as plain text. Actions: clear checked, clear all.

### 7.8 Settings

Theme (system / light / dark), dynamic colour toggle, language (system / Italian / English), backup,
restore, tag management, ingredient management, about (author, version, licence, link to the manual).

---

## 8. Data exchange: `.proportion`, sharing and backup

One JSON format, two uses. A `.proportion` file contains one or more recipes; sharing a single
recipe and exporting the whole database are the same operation with different content. A full backup
additionally carries tags, the ingredient catalogue and variants.

```json
{
  "format": "proportion",
  "version": 1,
  "exportedAt": "2026-09-01T18:20:00Z",
  "recipes": [{
    "id": "9f2c…",
    "title": "Torta di mele",
    "servings": 4,
    "tags": ["builtin:dessert", "builtin:oven", "merenda"],
    "ingredients": [
      { "name": "Farina 00", "qty": 300, "unit": "GRAM" },
      { "name": "Uova", "qty": 2, "unit": "EGG" },
      { "name": "Sale", "qty": null, "unit": "TO_TASTE", "display": "q.b." }
    ],
    "steps": ["Sbatti le uova con lo zucchero.", "…"],
    "variants": [{ "label": "Per 6", "constraint": { "type": "servings", "value": 6 } }]
  }]
}
```

- **Plain-text sharing** — formatted text ready for a messaging app: title, servings, aligned
  ingredients, numbered steps, and a discreet trailing line naming the app. When a scaling is
  active, the scaled quantities are exported.
- **File sharing** — `FileProvider` + `ACTION_SEND` with a dedicated MIME type. The app registers an
  `ACTION_VIEW` intent filter for the extension, so opening an attachment triggers import.
- **Backup** — `ACTION_CREATE_DOCUMENT`: the user picks the destination. No storage permission, no
  hidden folders.
- **Restore** — `ACTION_OPEN_DOCUMENT`, then a preview before anything is written ("42 recipes in
  this file, 12 already present"), then a choice of **Merge** (match by UUID: skip or duplicate) or
  **Replace all** (explicit second confirmation).

**Versioning.** `version` is enforced: a file from a future version is rejected with a clear message;
files from earlier versions are migrated on read. The parser is configured with
`ignoreUnknownKeys = true`, so fields introduced by later versions — starting with `density` — are
ignored instead of failing the import.

---

## 9. Design system

Material 3 with **dynamic colour on by default** on Android 12+. The brand pastel palette is the
fallback below Android 12 and can be forced from Settings.

| Role | Colour | Hex |
|---|---|---|
| Seed / primary | Pistachio | `#A8D5BA` |
| Secondary | Apricot | `#F4B393` |
| Tertiary | Butter | `#F2D48A` |
| Chart series 4 | Blueberry | `#A9BEEA` |
| Warning (advisories only) | Amber | `#B4762B` |
| Ink | Green-black | `#1D2621` |

Light and dark schemes are both defined explicitly. Amber is reserved for impractical-quantity and
oven advisories and is never used decoratively.

**Motion, used sparingly and always with a reason:** shared-element transition from list card to
recipe detail; numbers counting up to their new value when the scale changes; donut arcs drawing on
dashboard entry; a small bounce on a warning badge appearing. All of it respects
`prefers-reduced-motion` / animator duration scale.

**App icon:** a **clipart-style 3D pie chart** with candles — an elliptical top face in perspective
with an extruded side wall (darker shade of each wedge), thick rounded outlines, flat pastel fills,
no gradients beyond the single side-wall shade. Three candles with flames sit on the top face.
Adaptive icon in two layers (solid pistachio background, pie + candles foreground) that survives
circular, squircle and teardrop masks, plus a monochrome layer for themed icons on Android 13+ —
the silhouette stays readable because the wedge outlines and candles are thick.

---

## 10. Localisation

Italian and English at launch, structured so a third language is additive. No hardcoded user-facing
strings anywhere: `values/strings.xml` is English (default), `values-it/strings.xml` is Italian.
Plurals use `<plurals>`. Numbers and dates are formatted through locale-aware formatters; decimal
separators follow the locale (`1,5` in Italian). Per-app language selection via
`AndroidManifest` `localeConfig` and `AppCompatDelegate.setApplicationLocales`.

Built-in tag names are string resources resolved from the tag key; user tags are never translated.

---

## 11. Documentation and repository layout

```
proportion/
├─ app/
├─ core/       model · domain · data · database · datastore · designsystem · ui · transfer
├─ feature/    home · recipes · editor · cook · shopping · settings
├─ docs/
│  ├─ public/    it/ en/   what the app is, features, screenshots, privacy, changelog
│  ├─ manual/    it/ en/   step-by-step user manual
│  └─ private/             English only, for developers
│      ├─ architecture.md, module-map.md, data-model.md, scaling-engine.md
│      ├─ specs/, adr/
│      └─ contributing.md, release-checklist.md, localization.md
├─ gradle/libs.versions.toml
├─ .github/workflows/ci.yml
└─ README.md
```

The user manual carries **real screenshots taken from a physical device**, not mockups, and walks
through each flow with a worked example (a recipe entered, then rescaled by servings, by a fixed
ingredient, and by what is in the cupboard).

Italian is the source language for `public/` and `manual/`; English is the reference translation.
Each additional language is a sibling folder with identical filenames, so a missing translation is
visible at a glance. `docs/private/` is English only.

`README.md` stays short and in English: what ProPortion is, a screenshot, how to build, where the
docs live, licence, author. It links to `docs/` rather than duplicating it.

---

## 12. Prepared for v2: mass ↔ volume conversion via density

Density conversion is **not implemented in v1**, but v1 is built so that adding it requires no
migration and no signature change:

1. **Column created up front.** `Ingredient.density_g_per_ml REAL NULL` exists in schema version 1
   and is ignored by v1 code.
2. **Signature complete from the start.** The converter takes the ingredient even though v1 does not
   look at it:

```kotlin
interface UnitConverter {
    fun convert(qty: Double, from: MeasureUnit, to: MeasureUnit, ingredient: IngredientRef? = null): Double?
}

interface DensityRepository {
    suspend fun densityGramsPerMl(ingredient: IngredientRef): Double?
}

class NoDensityRepository : DensityRepository {           // v1 binding
    override suspend fun densityGramsPerMl(i: IngredientRef): Double? = null
}
```

   v1's converter returns `null` as soon as the categories differ; v2 swaps the Hilt binding.
3. **Tolerant exchange format.** `ignoreUnknownKeys = true` means a v2 file carrying `density`
   imports into a v1 app without error.

The known-density table (water 1.00, flour ≈ 0.55, sugar ≈ 0.85, oil ≈ 0.92, milk ≈ 1.03, …) will
ship as a versioned JSON asset, with the user able to override the value on a single ingredient —
which is why density lives on the ingredient and not only in a static table.

Note that **cup ↔ ml already works in v1**, because domestic units are modelled as volume units.
Only mass↔volume needs density.

---

## 13. Other deferred work

| Item | Why it is cheap later |
|---|---|
| Ingredient placeholders in steps (`{flour}`) | Steps are already a serialised `List<String>`; adding placeholders is additive |
| Import from pasted text | Lives entirely in `:core:transfer` beside the `.proportion` parser, as another `RecipeImporter` |
| Recipe photo | One nullable `image_uri` column and one component in the detail screen |

---

## 14. Testing and CI

- **Domain (strict TDD).** Scaling engine, unit rules, discrete detection, limiting-factor
  computation, fraction formatting, oven advisory thresholds. Pure JVM, no Android.
- **Serialisation.** Round-trip export → import → compare, plus malformed inputs: future version,
  missing fields, unknown unit, truncated JSON, unknown extra keys (must be ignored).
- **Persistence.** Room in-memory DAO tests for combined filters, text search and variant
  integrity. Schema migration tests from the first release onward.
- **UI.** Compose UI tests on the three paths that must never break: enter a recipe, rescale it,
  export and re-import it.

**CI (GitHub Actions), on every push:** `assembleDebug`, unit tests, `lint`, `detekt`. The build
fails when tests fail.

---

## 15. Implementation phases

Each phase ends with something installable and demonstrable.

1. **Foundations** — multi-module scaffolding, version catalog, design system and theme, adaptive
   icon, navigation with the four empty tabs, green CI.
2. **Domain and data** — model, units, scaling engine via TDD, Room with DAOs and migrations,
   repositories. No UI; the deliverable is a passing test suite.
3. **Enter and browse** — recipe editor with ingredient and tag autocomplete, list with search and
   combined filters, recipe detail. The app becomes usable here.
4. **Cook this recipe** — four constraint modes, live list, warnings and snaps, oven advisory with
   tin suggestion, scaled card, variant saving. The core of the product.
5. **Data exchange** — plain-text and `.proportion` sharing, opening from an attachment, backup and
   restore with preview and merge/replace choice.
6. **Home, shopping, cooking mode** — dashboard cards, persistent shopping list, cooking mode,
   favourites and usage counter.
7. **Polish** — complete translations, documentation in the three folders, accessibility (TalkBack,
   large text), final animations, release preparation.
