# Architecture

ProPortion is an offline Android app. There is no server, no account, and no sync beyond the
optional Syncthing-folder mechanism described in `HISTORY.md` (phase 10); everything the app knows
lives in one Room database on the device.

## The shape

```
:app                    navigation host, DI composition, MainActivity
:feature:*              one module per screen area — home, recipes, editor, cook, shopping, settings
:core:ui                components that know domain models (tag chips, unit picker, warning row)
:core:designsystem      palette, typography, shapes, motion, ProPortionTheme
:core:domain            scaling engine, unit rules, repository interfaces — pure Kotlin
:core:transfer          the .proportion file format and the plain-text share — pure Kotlin
:core:sync              sync policy (insert/overwrite/delete/skip) — pure Kotlin, entity-agnostic
:core:data              repository implementations, mappers, Hilt wiring
:core:database          Room entities, DAOs, migrations, seeding
:core:datastore         user preferences
:core:model             plain data classes shared by every layer
```

Two rules hold the whole thing together:

1. **A feature never depends on another feature.** Anything two features need moves into a core
   module. This is what keeps a screen's blast radius the size of one screen.
2. **`:core:domain`, `:core:transfer` and `:core:sync` never import `android.*` or `androidx.*`.**
   Each has a test that fails the build if they do. That is what makes the scaling rules testable in
   milliseconds and reusable if the app ever grows a second front end.

Dependencies point inwards: features depend on the domain, `:core:data` implements the domain's
repository interfaces. The domain knows nothing about Room, DataStore or Compose.

## Module map

| Module | Depends on | Holds |
|---|---|---|
| `:app` | every feature, `:core:data`, `:core:ui`, `:core:designsystem` | `MainActivity`, `ProPortionApp`, `TopLevelDestination`, Hilt application |
| `:feature:home` | `:core:domain`, `:core:ui`, `:core:designsystem` | dashboard |
| `:feature:recipes` | + `:core:transfer` | list with filters, recipe detail, sharing |
| `:feature:editor` | `:core:domain`, `:core:ui`, `:core:designsystem` | recipe editor and its draft state |
| `:feature:cook` | `:core:domain`, `:core:ui`, `:core:designsystem` | the four constraint modes, warnings, scaled card, cooking mode |
| `:feature:shopping` | `:core:domain`, `:core:ui` | shopping list |
| `:feature:settings` | + `:core:transfer`, `:core:data` | appearance, language, backup/restore, sync |
| `:core:ui` | `:core:domain`, `:core:designsystem` | tag chips, unit picker, warning row, state bodies, `RecipeSharing`, `FileProvider`, `AndroidUnitNamer`, `AndroidIngredientNamer`, `AppCompatLocaleController` |
| `:core:designsystem` | `:core:model` | colours, type, shapes, motion, `ProPortionTheme` |
| `:core:data` | `:core:domain`, `:core:transfer`, `:core:sync`, `:core:database`, `:core:datastore` | repository implementations, mappers, `DataModule`, `SyncRepositoryImpl`, `SyncWorker` |
| `:core:transfer` | `:core:domain`, `:core:model` | `.proportion` codec, plain-text formatter, `TransferRepository` |
| `:core:sync` | `:core:model` | `SyncableState`, `decideSyncAction` — pure policy, no I/O |
| `:core:domain` | `:core:model` | scaling engine, unit rules, repository interfaces, `LocaleController`, `BuiltInIngredientNamer` |
| `:core:database` | `:core:model` | Room entities, DAOs, converters, seeding, migrations |
| `:core:datastore` | `:core:model` | `UserPreferencesDataSource`, `SyncLogDataSource` |
| `:core:model` | — | `Recipe`, `Ingredient`, `MeasureUnit`, `Tag`, … |

Build logic lives in the included build `build-logic`, as convention plugins:
`proportion.android.application`, `proportion.android.library`, `proportion.android.library.compose`,
`proportion.jvm.library`, `proportion.hilt`.

**AGP 9 note:** AGP ships built-in Kotlin support, so a convention plugin must *not* apply
`org.jetbrains.kotlin.android` — doing so fails the build.

## How a screen is built

One `ViewModel` per screen, exposing a single `StateFlow<XUiState>` assembled from repository
flows. Composables are stateless: they take the state and a lambda per event. Every screen has a
stateful `XRoute` composable that talks to Hilt and a stateless `XScreen` that a test can render
with a hand-made state.

Errors are part of the state, never a crash and never a silent no-op — `CookUiState.error` and
`EditorUiState.errors` exist so the screen can say what went wrong.

## Where the rules live

The arithmetic is in `:core:domain` and nowhere else. A composable that computed a quantity would
be a bug: every number on screen comes out of `ScaledRecipe`. See "Scaling engine" below.

The Home dashboard follows the same rule for a different kind of number: `DashboardSummariser`
(`:core:domain`) turns the recipe list into counts, course slices and picks once, in one pure
function, so `HomeViewModel` and its composables never total or filter a list themselves. A
donut chart is drawn by `DonutChart` in `:core:designsystem`, which knows nothing about recipes —
it takes plain `DonutSlice` values, so it stays reusable for any other proportional breakdown.

## Cooking mode is a second route, not a second feature

Cooking mode (the screen you actually follow at the stove, screen-always-on, big type, checkable
steps) lives inside `:feature:cook` as `CookingModeRouteKey`, alongside the existing "Cook this
recipe" scaling screen. It is not its own module: the two screens are two views onto the same
`RecipeScaler` output, and a feature module would have had to depend on `:feature:cook` to reuse
the scaling logic, which the "no feature depends on another feature" rule forbids. The scaling the
user chose travels between the two screens as URL-safe Base64 of the `ScaleConstraint`'s JSON in
the navigation route — a raw JSON string as a nav argument is a reliable source of escaping bugs,
so it goes through `Base64.getUrlEncoder().withoutPadding()` first.

## Navigation

Navigation Compose with **type-safe routes**: `@Serializable` route classes, `composable<T>`, and
`savedStateHandle.toRoute<T>()` in the ViewModel. There are no route strings and no
`navArgument` keys to keep in sync. Each feature exposes its graph as a `NavGraphBuilder`
extension; `:app` assembles them.

## Testing

- Domain, transfer and sync: plain JVM tests, written first.
- Database and repositories: Robolectric with an in-memory Room database.
- Screens: Compose tests under Robolectric, rendering the stateless composable.
- `:app`: the real navigation graph over the real Hilt graph, so a screen whose ViewModel cannot be
  constructed fails in CI rather than on a device.

## Data model

Room, current schema version 4 (see `HISTORY.md` for what each migration added), exported to
`core/database/schemas`. Every primary key is a **UUID string**, because ids travel inside
`.proportion` files and have to mean the same thing on another device.

| Table | Notes |
|---|---|
| `recipes` | title, servings (nullable — a jam is not per person), steps as JSON, notes, favourite, cook count, timestamps, `deleted_at` (soft-delete tombstone for sync) |
| `ingredients` | catalogue; `normalised_name` is unique and is what lookup and de-duplication use; `key`/`is_built_in` mirror `Tag`; `density_g_per_ml`/`item_weight_grams` drive cross-category unit conversion; `updated_at` for sync conflict resolution |
| `recipe_ingredients` | the interesting table: quantity, unit, optional display text, position; cascades from the recipe, restricted against the ingredient |
| `tags` | either a built-in `key` or a literal `name`, never both; `updated_at` for sync |
| `recipe_tags` | many-to-many join, cascading both ways |
| `scale_variants` | stores the **constraint**, not the computed numbers, so a saved scaling survives editing the recipe |
| `shopping_items` | one persistent list; source recipe ids as JSON |

### Decisions worth knowing

- **Steps are stored as JSON**, not joined on a separator: a step legitimately contains commas and
  newlines, and losing a boundary would silently corrupt a recipe.
- **Deleting a recipe never deletes ingredients.** They stay in the catalogue but drop out of the
  filter sheet, which queries only ingredients referenced by some recipe.
- **Deleting a recipe is a soft delete** (`deleted_at`), not a row removal — needed so the
  Syncthing-folder sync (phase 10) can propagate the deletion as a tombstone. Every existing read
  query filters `deleted_at IS NULL`; a dedicated `findByIdIncludingDeleted` exists for the one
  place (export) that needs the tombstone itself.
- **Built-in tags and ingredients are seeded on database creation**, with ids derived from their
  key (`builtin-dessert`), so an imported recipe binds to the same row on every install. A
  built-in ingredient's `name`/`normalised_name` columns hold an inert placeholder (the raw key),
  never read: the real, current-language name is resolved at read time through
  `BuiltInIngredientNamer`, because a value frozen at seed time would go stale the moment the app
  language changes.
- **`density_g_per_ml` (schema 1) and `item_weight_grams` (schema 3)** drive `UnitConverter`
  crossing MASS/VOLUME/COUNT — density for mass↔volume, item weight for count↔mass/volume, both
  chained through grams as the hub. `density_g_per_ml` was created a schema version early on
  purpose: adding it later would have meant a migration on databases already in users' hands.

### Filtering

The three filters combine with AND. Within the ingredient filter a recipe must contain **every**
selected ingredient (`COUNT(DISTINCT …) = :ingredientCount`); within the tag filter it needs **any**
of the selected tags, because picking "first course" and "dessert" means either.

## Scaling engine

`:core:domain`, pure Kotlin, tested first. This is the part of the app that has to be right.

### One factor, four ways to reach it

```
ByServings(target)      target / recipe.servings
ByIngredient(line, qty) requested / original, after converting into the line's unit
ByFactor(factor)        used as is
ByAvailability(have)    a candidate factor per amount; the minimum wins and marks the bottleneck
```

Everything downstream of the factor is shared, which is why the rules live in one place rather than
four.

### Units

`MeasureUnit` carries a `category` and a `baseFactor`:

- `MASS` (g, kg, plus imperial oz/lb) and `VOLUME` (ml, l, tsp, tbsp, glass, cup, plus imperial fl
  oz/pint/quart/gallon) are continuous.
- Domestic measures are **volume units with a factor in millilitres**, which is what makes
  cup ↔ ml work with no density involved.
- `COUNT` (piece, egg, clove, slice, leaf, sachet, jar) is **discrete**.
- `APPROXIMATE` (to taste, pinch, drizzle) **never scales** and never contributes to a factor.

Conversion crosses categories through the ingredient's own data: MASS ↔ VOLUME via
`Ingredient.densityGramsPerMl`, COUNT ↔ MASS/VOLUME via `Ingredient.itemWeightGrams` (only when the
COUNT unit is that ingredient's own `defaultUnit` — a clove is not a slice, whatever its weight),
both chained through grams as the hub for COUNT ↔ VOLUME. With no density known, mass ↔ volume is
refused rather than guessed: 100 g of flour is not 100 ml. `requirementFor(from, to, ingredient)`
tells a caller what's missing (density, item weight, both, or nothing an answer would fix) — the UI
layer uses it to offer a "density unknown" prompt exactly when it would help.

### Impractical results

`DiscreteAnalyser` compares each discrete quantity with the nearest whole number:

- within 5%, it snaps silently — 2.02 eggs is 2 eggs and nobody needs to be told;
- beyond that, it emits `NonIntegerDiscrete` with the exact value plus a `SnapOption` for the whole
  numbers on either side, each carrying **the factor that amount implies**;
- a discrete result below 1 is clamped to 1: an ingredient must not vanish.

Accepting a snap re-runs the whole pipeline with the new factor. It never edits one line, which is
what keeps the recipe in proportion. The exact factor travels as a `Double` beside its display text
— rounding 4/3 to "1,33" and reading it back would drift the recipe.

Continuous quantities below half a gram or half a millilitre raise `TooSmallToMeasure`.

### Baking

`BakingAdvisor` fires when a recipe carries the built-in `oven` tag and the factor leaves the
0.7–1.4 band. It is advisory, never blocking, and carries a tin suggestion at constant batter depth:
**new diameter ≈ current × √factor** (a 24 cm tin at ×1.5 is about 29 cm).

## The `.proportion` format

One JSON format, three uses: sharing a single recipe, backing up the whole library, and (one file
per entity) the Syncthing-folder sync all reuse it — they differ only in how many recipes the file
holds and, for sync, in carrying a single ingredient or tag entry instead of a recipe.

```json
{
  "format": "proportion",
  "version": 1,
  "exportedAt": "…",
  "recipes": [{
    "id": "9f2c…",
    "title": "Torta di mele",
    "servings": 4,
    "tags": ["builtin:dessert", "merenda"],
    "ingredients": [
      { "name": "Farina 00", "qty": 300, "unit": "GRAM" },
      { "name": "Sale", "qty": null, "unit": "TO_TASTE", "display": "q.b." }
    ],
    "steps": ["…"],
    "notes": null
  }]
}
```

### Rules the codec enforces

- **`format` must be `proportion`**, or the file is rejected as somebody else's.
- **A newer `version` is refused**, by number, rather than half-read.
- **Unknown fields are ignored** (`ignoreUnknownKeys`), so a file written by a later version still
  imports minus what this version cannot understand.
- **An unknown unit is refused.** Every other unreadable detail can be dropped, but silently
  substituting a unit would change a recipe.
- **Tags and built-in ingredients travel by kind**: `builtin:<key>` for a built-in tag or
  ingredient, so both stay bound to the same seeded row (and translated) on the receiving device,
  and literal text otherwise. An unrecognised built-in ingredient key falls back to a literal
  ingredient rather than dropping the line — a missing ingredient is a worse failure than one
  temporarily labelled with its raw key.
- **Ids travel with the recipe**, which is what lets the receiving app tell a duplicate from a new
  recipe, and what the Syncthing sync matches files against.

### Import

Two steps, always. `preview()` reads the file and reports how many recipes it holds and how many ids
are already present, touching nothing. Then `import(text, mode)` runs with `MERGE` (skip ids already
here) or `REPLACE_ALL` (empty the library first; the UI asks twice).

On the way in, each ingredient name is resolved against the catalogue by its normalised name — so
importing a friend's recipe does not create a second "Farina 00" — and each built-in tag or
ingredient key binds to the seeded row of the same key.
