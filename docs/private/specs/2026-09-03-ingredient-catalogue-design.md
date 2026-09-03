# Ingredient catalogue — design spec

**Status:** approved by Marco 2026-09-03, including the export/import fix in §5. Implemented and
shipped 2026-09-03 — see `docs/private/plans/2026-09-03-phase-8-ingredient-catalogue.md` and its
execution ledger for the full build record, including two real bugs the final whole-plan review
found in pre-existing code (§3's placeholder invariant was being silently violated by a recipe
save) and fixed before closing the phase. Two known residual limitations from that review were
deliberately left for a follow-up rather than rushed — see §8 below.
**Phase:** 8.

## Goal

Phase 8 request (verbatim): a comprehensive, correctly-translated pre-populated ingredient
catalogue (roughly enough to cover most sweet or savoury dishes), with fast autocomplete on
ingredient entry — a few letters typed, then pick from a list.

Autocomplete itself already exists and works (`EditorViewModel`, `feature/editor`); it filters
the in-memory catalogue by normalised-name substring match. What is missing is the data: today
`ingredients` starts empty and only grows from what users type. This spec covers seeding ~400-600
built-in ingredients, translated the same way built-in tags are (resolved through `strings.xml` at
read time, so they follow the app language), plus the model and repository changes that make that
possible without breaking anything that already reads `Ingredient`.

## Approved design (recap)

Agreed with Marco before this document was written:

1. `Ingredient` gains `key: String?` and `isBuiltIn: Boolean`, mirroring `Tag` — plus a new
   `category: IngredientCategory?` field, added now but **foundation only**: no UI surfaces it in
   this phase.
2. Built-in ingredient names resolve at the **repository boundary**, not in the UI. Every existing
   consumer of `Ingredient.name` keeps working unchanged.
3. Resolution uses `Resources.getIdentifier("ingredient_$key", "string", packageName)` — a dynamic
   lookup instead of a hand-written `when` over 400-600 entries, which would blow past detekt's
   `LongMethod` threshold (80 lines, see `detekt.yml`). A test guards that every seeded key has a
   matching string resource in **both** `values/` and `values-it/`.
4. Catalogue size: ~400-600 entries ("ampia", chosen over the ~150-250 "essenziali" option).
5. Seeding reads a JSON asset once, the same way `ProPortionDatabase.seedCallback()` already seeds
   the 9 built-in tags, via an **additive** Room migration (current schema version is 1).
6. Translated always, like built-in tags — not seeded once as literal text.

Everything below fills in the mechanics needed to actually build that, grounded in the exact
current code (read in full while preparing this spec, see file list at the end).

## 1. Domain model

`core/model/.../Ingredient.kt`, current shape:

```kotlin
data class Ingredient(
    val id: String,
    val name: String,
    val normalisedName: String,
    val defaultUnit: MeasureUnit = MeasureUnit.GRAM,
    val densityGramsPerMl: Double? = null,
)
```

New shape:

```kotlin
data class Ingredient(
    val id: String,
    val key: String?,
    val name: String,
    val normalisedName: String,
    val isBuiltIn: Boolean,
    val defaultUnit: MeasureUnit = MeasureUnit.GRAM,
    val category: IngredientCategory? = null,
    val densityGramsPerMl: Double? = null,
) {
    init {
        require(isBuiltIn == (key != null)) {
            "a built-in ingredient carries a key and vice versa"
        }
    }
}
```

Unlike `Tag`, `name`/`normalisedName` stay **non-null** for every ingredient, built-in or not —
see §3 for why: they always hold a real, current-language value, not a fallback.

New enum, `core/model/.../IngredientCategory.kt`, foundation-only (no `when` over it anywhere
yet, just a stored classification for later filtering/grouping UI):

```kotlin
enum class IngredientCategory {
    FLOUR_AND_GRAIN,
    DAIRY_AND_EGG,
    FAT_AND_OIL,
    SUGAR_AND_SWEETENER,
    LEAVENING_AND_BAKING,
    CHOCOLATE_AND_COCOA,
    FRUIT,
    VEGETABLE,
    HERB_AND_SPICE,
    MEAT,
    FISH_AND_SEAFOOD,
    LEGUME,
    NUT_AND_SEED,
    CONDIMENT_AND_SAUCE,
    BEVERAGE,
    OTHER,
}
```

16 categories, judged to cover both sweet and savoury without over-splitting. Naming follows
`MeasureUnit`'s existing `SCREAMING_SNAKE_CASE` convention.

## 2. Database

`IngredientEntity` (`core/database/.../entity/Entities.kt`) gains the matching columns:

```kotlin
@Entity(tableName = "ingredients")
data class IngredientEntity(
    @PrimaryKey val id: String,
    val key: String?,
    val name: String,
    @ColumnInfo(name = "normalised_name") val normalisedName: String,
    @ColumnInfo(name = "is_built_in") val isBuiltIn: Boolean = false,
    @ColumnInfo(name = "default_unit") val defaultUnit: MeasureUnit = MeasureUnit.GRAM,
    val category: IngredientCategory? = null,
    @ColumnInfo(name = "density_g_per_ml") val densityGramsPerMl: Double? = null,
)
```

`Converters.kt` needs a nullable pair for the new enum, alongside the existing non-null
`MeasureUnit` converter:

```kotlin
@TypeConverter
fun categoryToName(category: IngredientCategory?): String? = category?.name

@TypeConverter
fun nameToCategory(name: String?): IngredientCategory? = name?.let(IngredientCategory::valueOf)
```

### Schema migration

`@Database(version = 1, ...)` → `version = 2`. Additive, no data loss:

```sql
ALTER TABLE ingredients ADD COLUMN key TEXT DEFAULT NULL;
ALTER TABLE ingredients ADD COLUMN is_built_in INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ingredients ADD COLUMN category TEXT DEFAULT NULL;
```

Existing (user-created) rows land with `key = NULL`, `is_built_in = 0`, `category = NULL` — exactly
the shape they already have conceptually, satisfying the `Ingredient` invariant above with no
backfill needed.

`exportSchema = true` means bumping the version produces a new
`core/database/schemas/.../2.json` the next time the project builds — expected, not something to
hand-write.

### Seeding — and the migration/callback duplication this forces

Today, `ProPortionDatabase.seedCallback()` is a `Room.Callback.onCreate` — it only ever fires for a
**brand-new** database file. Marco's own Fairphone already has a v1 database with recipes on it:
upgrading the installed app runs the *migration*, not `onCreate`. If the 400-600 built-in
ingredients were seeded only from `onCreate`, every existing install would upgrade to schema v2
with the new columns but an **empty** catalogue — silently missing the entire point of this phase
for every device that isn't a fresh install. So seeding must run from both paths, sharing one
function:

```kotlin
private fun seedBuiltInIngredients(db: SupportSQLiteDatabase, context: Context) {
    val seeds: List<IngredientSeed> = context.assets.open("ingredients.json").use {
        Json.decodeFromStream(it)
    }
    seeds.forEach { seed ->
        db.execSQL(
            "INSERT OR IGNORE INTO ingredients " +
                "(id, key, name, normalised_name, is_built_in, default_unit, category) " +
                "VALUES (?, ?, ?, ?, 1, ?, ?)",
            arrayOf<Any>(
                builtInIngredientId(seed.key),
                seed.key,
                seed.key, // placeholder, never read — see below
                seed.key, // placeholder, never read — see below
                seed.defaultUnit.name,
                seed.category.name,
            ),
        )
    }
}

fun builtInIngredientId(key: String): String = "builtin-$key"
```

Called from:
- `seedCallback()` — alongside the existing tag-seeding `onCreate`, for fresh installs.
- A new `Migration(1, 2)` object's `migrate()` — for upgrades. Both need `Context` (to open the
  asset); `Room.databaseBuilder` is already built with `@ApplicationContext context: Context` in
  `DataModule.database()`, so both the callback and the migration are constructed there with
  `context` in scope — no new dependency.

The `name`/`normalised_name` columns are set to the raw `key` at seed time purely to satisfy the
`NOT NULL` constraint; they are **inert placeholders**, never read for `isBuiltIn = true` rows —
see §3. Comment this explicitly in the seeding code so a future reader doesn't mistake it for real
data or "fix" it into English text.

`ingredients.json` lives at `core/database/src/main/assets/ingredients.json` (new directory), same
module as `seedCallback()`. Shape:

```json
[
  { "key": "flour_00", "defaultUnit": "GRAM", "category": "FLOUR_AND_GRAIN" },
  { "key": "egg", "defaultUnit": "EGG", "category": "DAIRY_AND_EGG" }
]
```

```kotlin
@Serializable
data class IngredientSeed(
    val key: String,
    val defaultUnit: MeasureUnit,
    val category: IngredientCategory,
)
```

(`MeasureUnit`/`IngredientCategory` deserialize by enum name automatically with
`kotlinx.serialization` — no custom serializer needed.)

## 3. Resolving built-in names — and why `normalisedName` has to be resolved too

`BuiltInIngredientNamer`, new file, `core/domain/.../catalogue/BuiltInIngredientNamer.kt`:

```kotlin
fun interface BuiltInIngredientNamer {
    fun name(key: String): String
}
```

Android implementation lives in **`core/ui`**, not `core/data`. This corrects an assumption made
earlier in this session's research: `core/data`'s `DataModule` does inject `@ApplicationContext
Context`, but only to build the Room database — it is not where Android-resource-backed domain
interfaces are bound in this codebase. The actual precedent is `AndroidUnitNamer`/
`UnitNamerModule` and `AppCompatLocaleController`/`LocaleControllerModule`, both in `core/ui`:
Dagger/Hilt resolves the binding at the app's component graph regardless of which Gradle module
declares it, exactly as `DataModule`'s `quantityFormatter()` already consumes `UnitNamer` today
without `core/data` depending on `core/ui`. `BuiltInIngredientNamer` follows the same shape:

```kotlin
// core/ui/.../AndroidIngredientNamer.kt
class AndroidIngredientNamer @Inject constructor(
    @ApplicationContext private val context: Context,
) : BuiltInIngredientNamer {

    override fun name(key: String): String {
        val resId = context.resources.getIdentifier("ingredient_$key", "string", context.packageName)
        check(resId != 0) { "no ingredient_$key string resource" }
        return context.getString(resId)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class IngredientNamerModule {
    @Binds
    abstract fun ingredientNamer(impl: AndroidIngredientNamer): BuiltInIngredientNamer
}
```

The `ingredient_<key>` strings live in `core/ui/src/main/res/values/strings.xml` and
`values-it/strings.xml`, colocated with `AndroidIngredientNamer` — same placement as the existing
`unit_*` strings next to `AndroidUnitNamer`.

**Why `normalisedName` must also be resolved at read time, not just `name`:** ingredient lookup by
normalised name happens at the SQL level today
(`IngredientDao.findByNormalisedName`, used by `findOrCreate`). If a built-in row's
`normalised_name` column were fixed at seed time, it would be frozen in whatever language the
device happened to be in the moment the row was created — wrong the instant the user changes the
app language (the very feature just shipped). So for `isBuiltIn = true` rows, **both** `name` and
`normalisedName` are computed at read time from the resolved string:

```kotlin
fun IngredientEntity.toDomain(namer: BuiltInIngredientNamer): Ingredient {
    val resolvedName = if (isBuiltIn) namer.name(key!!) else name
    return Ingredient(
        id = id,
        key = key,
        name = resolvedName,
        normalisedName = if (isBuiltIn) IngredientNames.normalise(resolvedName) else normalisedName,
        isBuiltIn = isBuiltIn,
        defaultUnit = defaultUnit,
        category = category,
        densityGramsPerMl = densityGramsPerMl,
    )
}
```

This is also why the seeded DB columns are inert placeholders (§2): they are never the source of
truth for a built-in row's display or search text, only a `NOT NULL` filler.

### Ripple: every caller of `IngredientEntity.toDomain()` needs the namer

Grepping the current codebase, `IngredientEntity.toDomain()` (parameterless today) is called from
three places, all needing the new `namer` parameter threaded through:

- `IngredientRepositoryImpl.observeAll()` / `.observeInUse()` — direct.
- `LineWithIngredient.toDomain()` → `RecipeWithRelations.toDomain()` — used by
  `RecipeRepositoryImpl` (`observeRecipes`, `observeRecipe`) to build every `Recipe.ingredients`
  line. **`RecipeRepositoryImpl` needs `BuiltInIngredientNamer` injected too**, and its two
  `.toDomain()` call sites become `.toDomain(namer)`.

Both mapper functions become:

```kotlin
fun LineWithIngredient.toDomain(namer: BuiltInIngredientNamer) = RecipeIngredient(
    id = line.id,
    ingredient = ingredient.toDomain(namer),
    /* ...unchanged... */
)

fun RecipeWithRelations.toDomain(namer: BuiltInIngredientNamer) = Recipe(
    /* ...unchanged... */
    ingredients = lines.sortedBy { it.line.position }.map { it.toDomain(namer) },
    /* ...unchanged... */
)
```

`ShoppingItemEntity.toDomain(ingredient: Ingredient)` is unaffected — it already takes a
pre-resolved `Ingredient`, supplied by callers that go through `IngredientRepository`.

## 4. Repository changes

`IngredientRepositoryImpl` gains the namer and a changed `findOrCreate`:

```kotlin
class IngredientRepositoryImpl @Inject constructor(
    private val dao: IngredientDao,
    private val namer: BuiltInIngredientNamer,
) : IngredientRepository {

    override fun observeAll(): Flow<List<Ingredient>> =
        dao.observeAll().map { list -> list.map { it.toDomain(namer) } }

    override fun observeInUse(): Flow<List<Ingredient>> =
        dao.observeInUse().map { list -> list.map { it.toDomain(namer) } }

    override suspend fun findOrCreate(name: String, defaultUnit: MeasureUnit): Ingredient {
        val normalised = IngredientNames.normalise(name)
        observeAll().first().firstOrNull { it.normalisedName == normalised }?.let { return it }

        val created = IngredientEntity(
            id = UUID.randomUUID().toString(),
            key = null,
            name = name.trim(),
            normalisedName = normalised,
            isBuiltIn = false,
            defaultUnit = defaultUnit,
        )
        dao.upsertAll(listOf(created))
        return created.toDomain(namer)
    }
}
```

**Why `findOrCreate` changes internally** (its public signature does not): the current
implementation dedups via `dao.findByNormalisedName()`, a raw SQL lookup against the stored
column. For a built-in row, that stored column is the inert placeholder from §3 — a SQL-level
lookup can never match a user-typed, currently-resolved name against it. Switching to filtering
the already-resolved `observeAll()` list in Kotlin — exactly the pattern
`TagRepositoryImpl.findOrCreateUserTag()` already uses for the same reason — fixes this. Once
this change lands, `IngredientDao.findByNormalisedName()` has no remaining caller and should be
deleted rather than left unused.

`RecipeRepositoryImpl` gains the namer too, passed to its two `.toDomain(namer)` calls
(`observeRecipes`, `observeRecipe`); nothing else in that class changes.

**Confirmed unaffected:** `EditorViewModel`'s autocomplete filter (`catalogue.filter {
it.normalisedName.contains(needle) }`) and its dedup-by-normalisedName expectations — both keep
working unmodified, because `catalogue` already comes from `ingredientRepository.observeAll()`,
which now returns fully-resolved, current-language `Ingredient` values.

## 5. `.proportion` export/import of built-in ingredients — in scope

Found while reading `TransferRepositoryImpl` and `ProportionFile` to ground this spec, not part of
the original request — Marco confirmed on 2026-09-03 this fix is in scope for phase 8.

Built-in **tags** already round-trip by key: `ProportionFile.BUILT_IN_TAG_PREFIX` ("`builtin:`")
lets an exported recipe carry `builtin:dessert` instead of a literal name, and
`TransferRepositoryImpl.resolveTag()` re-resolves it against the *importing* device's own seeded
tag — so a tag exported from an Italian device and imported on an English one still shows up
correctly as "Dessert", not as a new literal tag called "Dolce".

Built-in **ingredients** have no equivalent today, and after this phase they will need one:
`ProportionCodec` writes `WireIngredient.name = line.ingredient.name` — the *already-resolved*
literal string. Export a recipe with "Farina 00" from an Italian device, import it on an
English-language device, and `TransferRepositoryImpl.toRecipe()` calls
`ingredientRepository.findOrCreate("Farina 00", GRAM)`, which will not match the English-resolved
"Type 00 flour" built-in row — it creates a **new, duplicate, mislabelled user ingredient**
instead. This did not exist before (ingredient names weren't translated), so it is a regression
this phase introduces, not a pre-existing one.

**Fix — mirrors the tag mechanism exactly.** `WireIngredient.name` and `WireRecipe.tags` are
separate fields of separate types, so there is no real namespace collision in reusing the same
literal prefix string across both — it gets its own named constant purely for clarity at each call
site:

```kotlin
// ProportionFile companion object, alongside BUILT_IN_TAG_PREFIX
const val BUILT_IN_INGREDIENT_PREFIX = "builtin:"
```

- `ProportionCodec`'s ingredient-encoding side (where `WireIngredient(name = line.ingredient.name,
  ...)` is built today) becomes `name = line.ingredient.key?.let { "$BUILT_IN_INGREDIENT_PREFIX$it" }
  ?: line.ingredient.name` — same shape as `toWireTag()`.
- `IngredientDao` gains `findByKey(key: String): IngredientEntity?`
  (`SELECT * FROM ingredients WHERE key = :key LIMIT 1`), mirroring `TagDao.findByKey`.
- `TransferRepositoryImpl.toRecipe()` gains an ingredient-side resolver mirroring `resolveTag()`:

  ```kotlin
  private suspend fun resolveIngredient(wire: WireIngredient): Ingredient {
      val unit = MeasureUnit.valueOf(wire.unit)
      return if (wire.name.startsWith(ProportionFile.BUILT_IN_INGREDIENT_PREFIX)) {
          val key = wire.name.removePrefix(ProportionFile.BUILT_IN_INGREDIENT_PREFIX)
          // An unknown built-in key comes from a newer app: fall back to a literal ingredient
          // rather than dropping the line outright (an ingredient line, unlike a tag, is not
          // optional — the recipe needs *something* here).
          ingredientDao.findByKey(key)?.toDomain(namer) ?: ingredientRepository.findOrCreate(key, unit)
      } else {
          ingredientRepository.findOrCreate(wire.name, unit)
      }
  }
  ```

  `TransferRepositoryImpl` needs `IngredientDao` and `BuiltInIngredientNamer` added to its
  constructor for this (it currently only holds `IngredientRepository`, not the DAO directly).
  `RecipeIngredient`'s `ingredient = ingredientRepository.findOrCreate(wire.name, unit)` call in
  `toRecipe()` becomes `ingredient = resolveIngredient(wire)`.

Unlike a missing built-in *tag* (dropped, since a recipe can have zero tags), a missing built-in
*ingredient* key falls back to treating the bare key as a literal name rather than dropping the
ingredient line — losing an entire ingredient from an imported recipe (e.g. silently importing a
cake recipe with no flour) is a much worse failure than a line temporarily labelled with its raw
key text (`"flour_00"`) until the app is updated to recognise it.

## 6. Testing

- **Resource-consistency test** (new): every `key` in `ingredients.json` has a matching
  `ingredient_<key>` entry in both `core/ui/src/main/res/values/strings.xml` and
  `values-it/strings.xml`. A plain JVM test (no Robolectric needed) parsing both XML files and the
  JSON asset. Lives in `core/ui` (next to `AndroidIngredientNamer` and the strings), reading
  `core/database`'s asset via the fixed relative path between the two sibling module directories —
  flagged here as a real decision to carry into the plan; if it proves brittle in practice, the
  fallback is exposing the seed key list from a small shared object instead of parsing JSON
  cross-module in tests.
- **Migration test**: `MigrationTestHelper`-based test asserting `Migration(1, 2)` succeeds against
  a real pre-migration v1 database file and that the ingredient table gets seeded — this is the
  test that would have caught the callback/migration duplication gap in §2 if it hadn't been
  caught during grounding.
- **`AndroidIngredientNamer`** unit test (Robolectric): a handful of representative keys resolve to
  the correct string per language; an unknown key throws.
- **`IngredientRepositoryImpl`** unit test: `findOrCreate` dedups against a resolved built-in name
  case-insensitively/accent-insensitively (reusing the existing `IngredientNames.normalise` test
  fixtures style), and does not create a duplicate for an already-seeded key typed by the user.
- **`RecipeRepositoryImpl`** existing tests need a fake `BuiltInIngredientNamer` added to their
  constructor call, same shape as `SettingsViewModelTest`'s `FakeLocaleController` addition
  earlier this session.
- Existing `RecipeDaoTest` ingredient literals (`IngredientEntity("ing-flour", "Farina 00", ...)`)
  need updating for the new constructor shape (`key`, `isBuiltIn` positional/named args added).
- **`TransferRepositoryImpl`** tests (§5): round-trip a recipe containing a built-in ingredient
  through `exportAll()`/`import()` and assert the wire text carries `builtin:<key>`, not a literal
  name; importing a `builtin:<key>` wire ingredient resolves to the seeded row by key; importing an
  unknown `builtin:<key>` (simulating an older catalogue) falls back to a literal ingredient named
  after the bare key rather than throwing or dropping the line.

## 7. Out of scope for this phase

- Any UI for `IngredientCategory` (filtering, grouping, icons) — foundation only, per Marco's
  explicit choice.
- Density-based mass↔volume conversion (`densityGramsPerMl`) — pre-existing v2 preparation,
  untouched here.
- Deduplicating or merging pre-existing user-created ingredients against the newly-seeded built-in
  catalogue (e.g. a user who already typed "farina 00" by hand before this phase shipped, now
  sitting alongside the seeded built-in one as two separate rows) — no migration reconciles these;
  flagged here in case Marco wants a follow-up, not handled now.

## 8. Known limitations after implementation (found by the final whole-plan review, 2026-09-03)

Two real bugs in pre-existing code (not touched by this plan, but whose behaviour changed as a
side effect of §3's new resolution semantics) were found and fixed before shipping — see the
plan's execution ledger for the full detail:

- `RecipeRepositoryImpl.upsert()` was silently rewriting a built-in row's seeded placeholder
  name/normalisedName with the current-language text on every recipe save, violating this
  section's "inert placeholder, never written" claim in practice. Fixed by excluding built-in
  ingredients from that re-upsert (they never need it — their canonical data lives in the seed
  asset).
- `findOrCreate` could silently fail (and cause a later foreign-key crash) for the ~46 seeded keys
  whose raw key text differs from its resolved display name in both languages (e.g. `almond` →
  "Almonds"/"Mandorle") — typing the raw key collided with the built-in row's frozen placeholder at
  the database's unique-index level. Fixed by restoring a DAO-level lookup against the raw
  `normalised_name` column as a second check.

Two smaller items were deliberately left unfixed, as a considered choice rather than an oversight:

- **Recipe search does not find a built-in ingredient by its localised name.** `RecipeDao.filtered`
  matches the search box against the ingredients table's `normalised_name` column directly in SQL —
  which, now that the placeholder-rewrite bug above is fixed, is *permanently* the raw English key
  for a built-in row. Searching "farina" will not surface a recipe using the built-in `flour_00`
  ingredient. A real fix needs `RecipeDao.filtered` restructured to resolve names in Kotlin instead
  of matching raw SQL text — judged too large and risky to rush through the one fix wave a
  final-review cap allows. Ingredient *entry* (autocomplete) is unaffected; only searching an
  existing recipe by a built-in ingredient's name is limited.
- **Picking an autocomplete suggestion always overwrites the line's unit** with the ingredient's
  `defaultUnit` (`EditorViewModel.onSuggestionPick`), even if the user already typed a different
  one. This predates this phase but is more noticeable now that 477 curated defaults exist. A
  product/UX call, not a correctness bug, left for Marco to decide on.

## Files read in full to ground this spec

`core/model/Ingredient.kt`, `Tag.kt`, `MeasureUnit.kt`, `RecipeIngredient.kt`;
`core/database/entity/Entities.kt` (`IngredientEntity`, `TagEntity`),
`RecipeWithRelations.kt`, `LineWithIngredient.kt`, `Converters.kt`, `ProPortionDatabase.kt`,
`dao/IngredientDao.kt`; `core/data/EntityMappers.kt` (full),
`repository/CatalogueRepositoriesImpl.kt`, `repository/RecipeRepositoryImpl.kt`,
`repository/TransferRepositoryImpl.kt`, `di/DataModule.kt`;
`core/domain/unit/QuantityFormatter.kt` (`UnitNamer`),
`repository/CatalogueRepositories.kt` (`IngredientRepository`);
`core/ui/AndroidUnitNamer.kt`, `AppCompatLocaleController.kt`;
`core/transfer/ProportionFile.kt`, `TransferModels.kt`, `ProportionCodec.kt` (grep);
`feature/editor/EditorViewModel.kt` (autocomplete section); `detekt.yml` (thresholds).
