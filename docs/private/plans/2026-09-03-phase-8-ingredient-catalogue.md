# Ingredient Catalogue (Phase 8) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking. **Do not follow either skill's default git-commit
> step** — see Global Constraints.

**Goal:** seed a comprehensive, translated (Italian/English) built-in ingredient catalogue
(~400-600 entries) so the existing autocomplete has real data to match against, without breaking
any existing consumer of `Ingredient`.

**Architecture:** built-in ingredients carry a stable `key` (mirroring `Tag`) and resolve their
`name`/`normalisedName` through `strings.xml` at the repository boundary via a new
`BuiltInIngredientNamer`, so every existing reader of `Ingredient.name` needs zero changes. Seed
data lives in a JSON asset, inserted once via an additive Room migration (schema 1→2) that also
covers the fresh-install path, so both new and upgrading installs get the catalogue.

**Tech Stack:** Kotlin, Room 2.8, Hilt, kotlinx.serialization, Robolectric/JUnit4/Truth,
`androidx.room:room-testing` (`MigrationTestHelper`).

**Spec:** `docs/private/specs/2026-09-03-ingredient-catalogue-design.md` — read it alongside this
plan; it explains the *why* behind every decision here (in particular §3's explanation of why
`normalisedName` must resolve at read time too, and §5's export/import fix).

## Global Constraints

- **Never `git commit` or `git push`.** This overrides both execution skills' default
  per-task commit step — Marco commits everything himself. Every task below ends after the tests
  pass, with no commit step. (Phases 1-7 of this same project already followed this rule; the
  repository's git history is two commits total.)
- Never mention Marco's employer anywhere (comments, strings, docs).
- `:core:domain` and `:core:transfer` may not import `android.*` — `BuiltInIngredientNamer` is an
  interface there; its Android implementation lives in `:core:ui`.
- No hardcoded user-facing strings — everything through `values/strings.xml` (English) and
  `values-it/strings.xml` (Italian), kept in parity (`scripts/check-string-parity.sh`).
- detekt `maxIssues: 0`, `LongMethod` threshold 80, `MaxLineLength` 120 — this is exactly why
  built-in name resolution uses `Resources.getIdentifier` instead of a 400-600-branch `when`.
- Features never depend on other features; this work only touches `:core:*` modules.
- Update `docs/private/IMPLEMENTATION-STATUS.md` as work proceeds, not only at the end (task 9
  does the final pass, but note anything significant as you go per Marco's standing rule: "ogni
  modifica se significativa va documentata").

---

### Task 1: Domain model — `IngredientCategory` and the new `Ingredient` fields

**Files:**
- Create: `core/model/src/main/kotlin/com/ilsecondodasinistra/proportion/core/model/IngredientCategory.kt`
- Modify: `core/model/src/main/kotlin/com/ilsecondodasinistra/proportion/core/model/Ingredient.kt`
- Test: `core/model/src/test/kotlin/com/ilsecondodasinistra/proportion/core/model/IngredientTest.kt`

**Interfaces:**
- Produces: `IngredientCategory` enum (16 values, see below); `Ingredient(id, key, name,
  normalisedName, isBuiltIn, defaultUnit = MeasureUnit.GRAM, category = null,
  densityGramsPerMl = null)` — note `key` and `isBuiltIn` are now new **required** (non-default)
  constructor parameters inserted after `id`, before `name`.

This task only changes the module with zero other dependencies (`:core:model` sits at the bottom
of the graph) — every other module that constructs an `Ingredient` will fail to compile until
later tasks update them. That is expected; this task's own tests are what must pass here.

- [ ] **Step 1: Create `IngredientCategory.kt`**

```kotlin
package com.ilsecondodasinistra.proportion.core.model

/**
 * Coarse classification of an ingredient, for future catalogue browsing/filtering.
 *
 * Foundation-only in phase 8: every ingredient carries one, but no UI surfaces it yet.
 */
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

- [ ] **Step 2: Rewrite `Ingredient.kt`**

```kotlin
package com.ilsecondodasinistra.proportion.core.model

/**
 * An entry in the reusable ingredient catalogue.
 *
 * Built-in ingredients carry a stable [key] resolved through `strings.xml` (mirrors [Tag]), so
 * [name] and [normalisedName] always hold the current app language by the time an `Ingredient`
 * reaches here — a caller never needs to know whether it read a built-in or a user-created row.
 *
 * @param normalisedName lowercase, trimmed and accent-folded; the key used for lookup, filtering
 * and de-duplication on import.
 * @param densityGramsPerMl **v2 preparation, unused in v1.** The column exists from schema version
 * one so that adding mass <-> volume conversion later needs no migration. Do not remove it.
 */
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

- [ ] **Step 3: Write the failing test — `IngredientTest.kt`**

```kotlin
package com.ilsecondodasinistra.proportion.core.model

import org.junit.Assert.assertThrows
import org.junit.Test

class IngredientTest {

    @Test
    fun `a built in ingredient carries a key`() {
        Ingredient(id = "i1", key = "flour_00", name = "Farina 00", normalisedName = "farina 00", isBuiltIn = true)
    }

    @Test
    fun `a user ingredient carries no key`() {
        Ingredient(id = "i2", key = null, name = "Farina 00", normalisedName = "farina 00", isBuiltIn = false)
    }

    @Test
    fun `a built in ingredient with no key is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            Ingredient(id = "i3", key = null, name = "x", normalisedName = "x", isBuiltIn = true)
        }
    }

    @Test
    fun `a user ingredient with a key is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            Ingredient(id = "i4", key = "flour_00", name = "x", normalisedName = "x", isBuiltIn = false)
        }
    }
}
```

- [ ] **Step 4: Run the test**

Run: `./gradlew :core:model:test --tests "*.IngredientTest"`
Expected: PASS (four tests). This module has no other consumer inside it, so nothing else breaks.

---

### Task 2: Database — `IngredientEntity`, `Converters`, schema v2, `Migration(1, 2)` (columns only)

**Files:**
- Modify: `core/database/src/main/kotlin/com/ilsecondodasinistra/proportion/core/database/entity/Entities.kt`
- Modify: `core/database/src/main/kotlin/com/ilsecondodasinistra/proportion/core/database/Converters.kt`
- Modify: `core/database/src/main/kotlin/com/ilsecondodasinistra/proportion/core/database/ProPortionDatabase.kt`
- Modify: `core/database/build.gradle.kts`
- Modify: `core/database/src/test/kotlin/com/ilsecondodasinistra/proportion/core/database/RecipeDaoTest.kt`
- Test: `core/database/src/test/kotlin/com/ilsecondodasinistra/proportion/core/database/MigrationTest.kt`

**Interfaces:**
- Consumes: nothing outside this module.
- Produces: `IngredientEntity(id, key, name, normalisedName, isBuiltIn = false, defaultUnit =
  MeasureUnit.GRAM, category = null, densityGramsPerMl = null)`; `ProPortionDatabase.seedCallback(context:
  Context): Callback` (signature change — now takes `Context`); `Migration1to2(context: Context):
  Migration` (new, exported as a top-level class in `ProPortionDatabase.kt`); schema version `2`.

This task does the column migration only — seeding the actual ingredient rows is Task 6, once
`BuiltInIngredientNamer` and the seed data exist. Keeping them separate means this task's migration
test only has to prove "the columns show up correctly," and Task 6's only has to prove "the rows
show up correctly."

- [ ] **Step 1: Update `IngredientEntity` in `Entities.kt`**

Replace the current `IngredientEntity` (around line 28) with:

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
    /** v2 preparation: created in schema 1, written by nobody in v1. Do not drop. */
    @ColumnInfo(name = "density_g_per_ml") val densityGramsPerMl: Double? = null,
)
```

Add the import `com.ilsecondodasinistra.proportion.core.model.IngredientCategory` at the top of
`Entities.kt`, alongside the existing `MeasureUnit` import.

- [ ] **Step 2: Add the nullable `IngredientCategory` converter to `Converters.kt`**

```kotlin
@TypeConverter
fun categoryToName(category: IngredientCategory?): String? = category?.name

@TypeConverter
fun nameToCategory(name: String?): IngredientCategory? = name?.let(IngredientCategory::valueOf)
```

Add these inside the existing `Converters` class, after `nameToUnit`. Add the import
`com.ilsecondodasinistra.proportion.core.model.IngredientCategory`.

- [ ] **Step 3: Bump the schema version and add the migration in `ProPortionDatabase.kt`**

Change `version = 1` to `version = 2` in the `@Database` annotation.

Change `seedCallback(): Callback` to take a `Context`:

```kotlin
fun seedCallback(context: Context): Callback = object : Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        Tag.BUILT_IN_KEYS.forEachIndexed { index, key ->
            db.execSQL(
                "INSERT OR IGNORE INTO tags (id, key, name, is_built_in, color_index) " +
                    "VALUES (?, ?, NULL, 1, ?)",
                arrayOf<Any>(builtInTagId(key), key, index),
            )
        }
    }
}
```

(Body unchanged for now — `context` is unused until Task 6 adds ingredient seeding to it. Keep the
parameter anyway so every call site is only updated once across this task and Task 6.)

Add, in the same file, below the `ProPortionDatabase` class:

```kotlin
class Migration1to2(private val context: Context) : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ingredients ADD COLUMN key TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE ingredients ADD COLUMN is_built_in INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ingredients ADD COLUMN category TEXT DEFAULT NULL")
    }
}
```

Add imports: `android.content.Context`, `androidx.room.migration.Migration`.

- [ ] **Step 4: Wire the migration into `DataModule.database()`**

In `core/data/.../di/DataModule.kt`:

```kotlin
@Provides
@Singleton
fun database(@ApplicationContext context: Context): ProPortionDatabase =
    Room.databaseBuilder(context, ProPortionDatabase::class.java, ProPortionDatabase.NAME)
        .addMigrations(Migration1to2(context))
        .addCallback(ProPortionDatabase.seedCallback(context))
        .build()
```

(Two changes: `.addMigrations(...)` is new; `seedCallback()` becomes `seedCallback(context)`.) Add
the import `com.ilsecondodasinistra.proportion.core.database.Migration1to2`.

- [ ] **Step 5: Fix the two existing test call sites that construct `IngredientEntity`/`seedCallback()`**

`core/database/.../RecipeDaoTest.kt` — update `setUp()`'s callback call:

```kotlin
.addCallback(ProPortionDatabase.seedCallback(ApplicationProvider.getApplicationContext()))
```

Update every `IngredientEntity(...)` positional-argument literal in this file (lines ~48, ~49,
~72, ~183 as of this plan) to the new constructor shape — e.g.:

```kotlin
val flour = IngredientEntity("ing-flour", key = null, "Farina 00", "farina 00", isBuiltIn = false, defaultUnit = MeasureUnit.GRAM)
val eggs = IngredientEntity("ing-eggs", key = null, "Uova", "uova", isBuiltIn = false, defaultUnit = MeasureUnit.EGG)
```

and similarly for `"ing-rice"` and `"ing-unused"`. Use named arguments after `id` for `key` and
`isBuiltIn` in every case (the plain positional form no longer type-checks past `id`/`key`/`name`/
`normalisedName`/`isBuiltIn` since `key` now sits before `name`).

- [ ] **Step 6: Run the database module's existing tests to confirm nothing else broke**

Run: `./gradlew :core:database:testDebugUnitTest --tests "*.RecipeDaoTest"`
Expected: PASS. (`:core:data` and other modules will still fail to compile at this point — that's
expected until Tasks 4-6 catch them up. Do not attempt to build those modules yet.)

- [ ] **Step 7: Wire the exported schema directory into the test source set**

`MigrationTestHelper` reads the exported schema JSON files as test assets — add to
`core/database/build.gradle.kts`, inside the existing `android { }` block (after `namespace =
...`):

```kotlin
sourceSets {
    getByName("test") {
        assets.srcDirs("$projectDir/schemas")
    }
}
```

- [ ] **Step 8: Generate the version-2 schema export**

Run: `./gradlew :core:database:compileDebugKotlin`
Expected: a new file appears at
`core/database/schemas/com.ilsecondodasinistra.proportion.core.database.ProPortionDatabase/2.json`
(the `1.json` from schema version 1 stays — Room keeps every version's export). If it does not
appear, the `@Database(version = 2, exportSchema = true)` change from Step 3 did not take, or KSP
did not re-run — try `./gradlew :core:database:clean :core:database:compileDebugKotlin`.

- [ ] **Step 9: Write the failing migration test — `MigrationTest.kt`**

```kotlin
package com.ilsecondodasinistra.proportion.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ProPortionDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `migrating from version 1 adds the ingredient columns without losing existing rows`() {
        val v1 = helper.createDatabase(TEST_DB, 1)
        v1.execSQL(
            "INSERT INTO ingredients (id, name, normalised_name, default_unit) " +
                "VALUES ('ing-flour', 'Farina 00', 'farina 00', 'GRAM')",
        )
        v1.close()

        val v2 = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            Migration1to2(ApplicationProvider.getApplicationContext()),
        )

        val cursor = v2.query("SELECT name, key, is_built_in, category FROM ingredients WHERE id = 'ing-flour'")
        cursor.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(it.getColumnIndexOrThrow("name"))).isEqualTo("Farina 00")
            assertThat(it.isNull(it.getColumnIndexOrThrow("key"))).isTrue()
            assertThat(it.getInt(it.getColumnIndexOrThrow("is_built_in"))).isEqualTo(0)
            assertThat(it.isNull(it.getColumnIndexOrThrow("category"))).isTrue()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
```

- [ ] **Step 10: Run the migration test**

Run: `./gradlew :core:database:testDebugUnitTest --tests "*.MigrationTest"`
Expected: PASS. If `MigrationTestHelper`'s constructor signature does not match what is installed
(Room testing APIs have shifted across versions), check the actual `room-testing:2.8.4` sources
under the Gradle cache for the real constructor and adjust — the shape above is the documented
classic (non-KMP) Room migration-testing API, which this project uses (see `SupportSQLiteDatabase`
already used throughout `ProPortionDatabase.kt`).

---

### Task 3: `BuiltInIngredientNamer` — the resolution mechanism

**Files:**
- Create: `core/domain/src/main/kotlin/com/ilsecondodasinistra/proportion/core/domain/BuiltInIngredientNamer.kt`
- Create: `core/ui/src/main/kotlin/com/ilsecondodasinistra/proportion/core/ui/AndroidIngredientNamer.kt`
- Modify: `core/ui/src/main/res/values/strings.xml`
- Modify: `core/ui/src/main/res/values-it/strings.xml`
- Test: `core/ui/src/test/kotlin/com/ilsecondodasinistra/proportion/core/ui/AndroidIngredientNamerTest.kt`

**Interfaces:**
- Produces: `fun interface BuiltInIngredientNamer { fun name(key: String): String }`;
  `AndroidIngredientNamer` (Hilt-bound to it) and `IngredientNamerModule`.
- Consumes: nothing from earlier tasks (this is a leaf like `AndroidUnitNamer`).

This task also adds the **starter set of 48 ingredient strings** (3 per category, all 16
categories) that Tasks 6 and 7 build on. Writing a realistic, immediately-useful slice now — rather
than a placeholder pair — means the seeding and repository work in Tasks 5-6 is tested against real
data from the start, and Task 7 only has to *extend* an already-correct pattern to reach 400-600.

- [ ] **Step 1: Create the domain interface**

```kotlin
package com.ilsecondodasinistra.proportion.core.domain

/** Resolves a built-in ingredient's [key] to its name in the current app language. */
fun interface BuiltInIngredientNamer {
    fun name(key: String): String
}
```

- [ ] **Step 2: Create the Android implementation and its Hilt module**

```kotlin
package com.ilsecondodasinistra.proportion.core.ui

import android.content.Context
import com.ilsecondodasinistra.proportion.core.domain.BuiltInIngredientNamer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Resolves built-in ingredient names from `strings.xml` via a dynamic lookup rather than a
 * hand-written `when`: with 400-600 entries, a `when` block would blow past detekt's `LongMethod`
 * threshold (80 lines). [AndroidIngredientNamerTest] plus a resource-consistency test guard the
 * one risk this trades in — a seed key with no matching string resource — at build time.
 */
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

    /** The domain asks for names through [BuiltInIngredientNamer]; only this layer knows resources. */
    @Binds
    abstract fun ingredientNamer(impl: AndroidIngredientNamer): BuiltInIngredientNamer
}
```

- [ ] **Step 3: Add the 48 starter strings to `core/ui/src/main/res/values/strings.xml`**

Add this block (a new section, anywhere in the file — after the existing `unit_*` strings is a
reasonable spot):

```xml
<!-- Built-in ingredient catalogue: resolved by AndroidIngredientNamer via ingredient_<key>. -->
<string name="ingredient_flour_00">Type 00 flour</string>
<string name="ingredient_rice">Rice</string>
<string name="ingredient_breadcrumbs">Breadcrumbs</string>
<string name="ingredient_egg">Egg</string>
<string name="ingredient_milk">Milk</string>
<string name="ingredient_parmesan">Parmesan</string>
<string name="ingredient_olive_oil">Olive oil</string>
<string name="ingredient_butter">Butter</string>
<string name="ingredient_lard">Lard</string>
<string name="ingredient_sugar">Sugar</string>
<string name="ingredient_honey">Honey</string>
<string name="ingredient_brown_sugar">Brown sugar</string>
<string name="ingredient_baking_powder">Baking powder</string>
<string name="ingredient_yeast">Fresh yeast</string>
<string name="ingredient_baking_soda">Baking soda</string>
<string name="ingredient_dark_chocolate">Dark chocolate</string>
<string name="ingredient_cocoa_powder">Cocoa powder</string>
<string name="ingredient_white_chocolate">White chocolate</string>
<string name="ingredient_apple">Apple</string>
<string name="ingredient_lemon">Lemon</string>
<string name="ingredient_banana">Banana</string>
<string name="ingredient_onion">Onion</string>
<string name="ingredient_tomato">Tomato</string>
<string name="ingredient_potato">Potato</string>
<string name="ingredient_basil">Basil</string>
<string name="ingredient_black_pepper">Black pepper</string>
<string name="ingredient_cinnamon">Cinnamon</string>
<string name="ingredient_chicken_breast">Chicken breast</string>
<string name="ingredient_ground_beef">Ground beef</string>
<string name="ingredient_pancetta">Pancetta</string>
<string name="ingredient_tuna">Tuna</string>
<string name="ingredient_shrimp">Shrimp</string>
<string name="ingredient_anchovy">Anchovy</string>
<string name="ingredient_chickpea">Chickpeas</string>
<string name="ingredient_lentil">Lentils</string>
<string name="ingredient_borlotti_bean">Borlotti beans</string>
<string name="ingredient_almond">Almonds</string>
<string name="ingredient_walnut">Walnuts</string>
<string name="ingredient_pine_nut">Pine nuts</string>
<string name="ingredient_salt">Salt</string>
<string name="ingredient_vinegar">Vinegar</string>
<string name="ingredient_mayonnaise">Mayonnaise</string>
<string name="ingredient_red_wine">Red wine</string>
<string name="ingredient_espresso_coffee">Espresso coffee</string>
<string name="ingredient_sparkling_water">Sparkling water</string>
<string name="ingredient_vanilla_extract">Vanilla extract</string>
<string name="ingredient_gelatin_sheet">Gelatin sheet</string>
<string name="ingredient_water">Water</string>
```

- [ ] **Step 4: Add the matching Italian strings to `core/ui/src/main/res/values-it/strings.xml`**

```xml
<!-- Built-in ingredient catalogue: resolved by AndroidIngredientNamer via ingredient_<key>. -->
<string name="ingredient_flour_00">Farina 00</string>
<string name="ingredient_rice">Riso</string>
<string name="ingredient_breadcrumbs">Pangrattato</string>
<string name="ingredient_egg">Uovo</string>
<string name="ingredient_milk">Latte</string>
<string name="ingredient_parmesan">Parmigiano</string>
<string name="ingredient_olive_oil">Olio d\'oliva</string>
<string name="ingredient_butter">Burro</string>
<string name="ingredient_lard">Strutto</string>
<string name="ingredient_sugar">Zucchero</string>
<string name="ingredient_honey">Miele</string>
<string name="ingredient_brown_sugar">Zucchero di canna</string>
<string name="ingredient_baking_powder">Lievito in polvere</string>
<string name="ingredient_yeast">Lievito di birra</string>
<string name="ingredient_baking_soda">Bicarbonato di sodio</string>
<string name="ingredient_dark_chocolate">Cioccolato fondente</string>
<string name="ingredient_cocoa_powder">Cacao amaro</string>
<string name="ingredient_white_chocolate">Cioccolato bianco</string>
<string name="ingredient_apple">Mela</string>
<string name="ingredient_lemon">Limone</string>
<string name="ingredient_banana">Banana</string>
<string name="ingredient_onion">Cipolla</string>
<string name="ingredient_tomato">Pomodoro</string>
<string name="ingredient_potato">Patata</string>
<string name="ingredient_basil">Basilico</string>
<string name="ingredient_black_pepper">Pepe nero</string>
<string name="ingredient_cinnamon">Cannella</string>
<string name="ingredient_chicken_breast">Petto di pollo</string>
<string name="ingredient_ground_beef">Carne macinata</string>
<string name="ingredient_pancetta">Pancetta</string>
<string name="ingredient_tuna">Tonno</string>
<string name="ingredient_shrimp">Gamberetti</string>
<string name="ingredient_anchovy">Acciuga</string>
<string name="ingredient_chickpea">Ceci</string>
<string name="ingredient_lentil">Lenticchie</string>
<string name="ingredient_borlotti_bean">Fagioli borlotti</string>
<string name="ingredient_almond">Mandorle</string>
<string name="ingredient_walnut">Noci</string>
<string name="ingredient_pine_nut">Pinoli</string>
<string name="ingredient_salt">Sale</string>
<string name="ingredient_vinegar">Aceto</string>
<string name="ingredient_mayonnaise">Maionese</string>
<string name="ingredient_red_wine">Vino rosso</string>
<string name="ingredient_espresso_coffee">Caffè espresso</string>
<string name="ingredient_sparkling_water">Acqua frizzante</string>
<string name="ingredient_vanilla_extract">Estratto di vaniglia</string>
<string name="ingredient_gelatin_sheet">Colla di pesce</string>
<string name="ingredient_water">Acqua</string>
```

- [ ] **Step 5: Write the failing test — `AndroidIngredientNamerTest.kt`**

```kotlin
package com.ilsecondodasinistra.proportion.core.ui

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AndroidIngredientNamerTest {

    private val namer = AndroidIngredientNamer(ApplicationProvider.getApplicationContext())

    @Test
    fun `resolves a known key in the default language`() {
        assertThat(namer.name("flour_00")).isEqualTo("Type 00 flour")
    }

    @Test
    @Config(qualifiers = "it")
    fun `resolves a known key in Italian`() {
        assertThat(namer.name("flour_00")).isEqualTo("Farina 00")
    }

    @Test
    fun `an unknown key fails loudly rather than silently`() {
        assertThrows(IllegalStateException::class.java) { namer.name("no_such_key") }
    }

    private fun assertThrows(expected: Class<out Throwable>, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            if (expected.isInstance(t)) return
            throw t
        }
        throw AssertionError("expected $expected to be thrown")
    }
}
```

- [ ] **Step 6: Run the test**

Run: `./gradlew :core:ui:testDebugUnitTest --tests "*.AndroidIngredientNamerTest"`
Expected: PASS (three tests, including the Italian-qualifier one — `core:ui`'s existing tests
already rely on Robolectric resolving `values-it` via `@Config(qualifiers = "it")`, same mechanism
used elsewhere in this module).

---

### Task 4: `EntityMappers` — thread the namer through `toDomain()`

**Files:**
- Modify: `core/data/src/main/kotlin/com/ilsecondodasinistra/proportion/core/data/EntityMappers.kt`

**Interfaces:**
- Consumes: `BuiltInIngredientNamer` (Task 3), `IngredientEntity`/`Ingredient` new shapes (Tasks
  1-2), `IngredientNames.normalise` (existing).
- Produces: `IngredientEntity.toDomain(namer: BuiltInIngredientNamer): Ingredient` (signature
  change — was parameterless); `Ingredient.toEntity(): IngredientEntity` (body updated, signature
  unchanged); `LineWithIngredient.toDomain(namer: BuiltInIngredientNamer): RecipeIngredient`
  (signature change); `RecipeWithRelations.toDomain(namer: BuiltInIngredientNamer): Recipe`
  (signature change).

This task only edits the mapper file — it does not yet update the repositories that call these
mappers (Task 5 does). Expect `:core:data` to still fail to compile after this task; that is
resolved by the end of Task 5. There is no isolated unit test for this file today (it has none),
so this task's correctness is verified indirectly by Task 5's repository tests — do not add a new
test file here, follow the existing pattern.

- [ ] **Step 1: Replace the `IngredientEntity.toDomain()` / `Ingredient.toEntity()` pair**

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

fun Ingredient.toEntity() = IngredientEntity(
    id = id,
    key = key,
    name = name,
    normalisedName = normalisedName,
    isBuiltIn = isBuiltIn,
    defaultUnit = defaultUnit,
    category = category,
    densityGramsPerMl = densityGramsPerMl,
)
```

Add the import `com.ilsecondodasinistra.proportion.core.domain.BuiltInIngredientNamer`.

- [ ] **Step 2: Update `LineWithIngredient.toDomain()` and `RecipeWithRelations.toDomain()`**

```kotlin
fun LineWithIngredient.toDomain(namer: BuiltInIngredientNamer) = RecipeIngredient(
    id = line.id,
    ingredient = ingredient.toDomain(namer),
    position = line.position,
    quantity = line.quantity,
    unit = line.unit,
    displayText = line.displayText,
    note = line.note,
)
```

```kotlin
fun RecipeWithRelations.toDomain(namer: BuiltInIngredientNamer) = Recipe(
    id = recipe.id,
    title = recipe.title,
    servings = recipe.servings,
    steps = recipe.steps,
    ingredients = lines.sortedBy { it.line.position }.map { it.toDomain(namer) },
    tags = tags.map { it.toDomain() },
    notes = recipe.notes,
    isFavourite = recipe.isFavourite,
    cookCount = recipe.cookCount,
    lastCookedAt = recipe.lastCookedAt,
    createdAt = recipe.createdAt,
    updatedAt = recipe.updatedAt,
)
```

(Every other function in this file — `RecipeIngredient.toEntity()`, `Recipe.toEntity()`,
`ScaleVariantEntity.toDomain()`/`toEntity()`, `ShoppingItemEntity.toDomain(ingredient)` — is
untouched.)

- [ ] **Step 3: Confirm the file's own syntax is valid**

Run: `./gradlew :core:data:compileKotlin`
Expected: FAILS — but only with errors in `CatalogueRepositoriesImpl.kt` and
`RecipeRepositoryImpl.kt` about missing arguments to `toDomain()`/wrong constructor arity for
`IngredientEntity`. If the failure is instead inside `EntityMappers.kt` itself, fix it before
continuing — that would mean a typo in this task's own edit.

---

### Task 5: Repository layer — `IngredientRepositoryImpl` and `RecipeRepositoryImpl`

**Files:**
- Modify: `core/data/src/main/kotlin/com/ilsecondodasinistra/proportion/core/data/repository/CatalogueRepositoriesImpl.kt`
- Modify: `core/data/src/main/kotlin/com/ilsecondodasinistra/proportion/core/data/repository/RecipeRepositoryImpl.kt`
- Modify: `core/database/src/main/kotlin/com/ilsecondodasinistra/proportion/core/database/dao/IngredientDao.kt`
- Modify: `core/data/src/test/kotlin/com/ilsecondodasinistra/proportion/core/data/RepositoryTest.kt`

**Interfaces:**
- Consumes: `BuiltInIngredientNamer` (Task 3), `EntityMappers` new signatures (Task 4).
- Produces: `IngredientRepositoryImpl(dao: IngredientDao, namer: BuiltInIngredientNamer)` (Hilt
  constructor — extra param); `RecipeRepositoryImpl(recipeDao, ingredientDao, namer:
  BuiltInIngredientNamer, time)` (extra param inserted before `time`); `IngredientDao.findByKey(key:
  String): IngredientEntity?` (new — needed by Task 8's transfer resolver, added here since it is
  an `IngredientDao` change); `IngredientDao.findByNormalisedName` — **removed**, no longer called
  anywhere after this task.

- [ ] **Step 1: Add `findByKey` and remove the now-dead `findByNormalisedName` in `IngredientDao.kt`**

```kotlin
@Query("SELECT * FROM ingredients WHERE key = :key LIMIT 1")
suspend fun findByKey(key: String): IngredientEntity?
```

Delete the `findByNormalisedName` method entirely (its only caller is rewritten in Step 2 below).

- [ ] **Step 2: Rewrite `IngredientRepositoryImpl` in `CatalogueRepositoriesImpl.kt`**

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

(`TagRepositoryImpl` in the same file is untouched.) Add the import
`com.ilsecondodasinistra.proportion.core.domain.BuiltInIngredientNamer`.

**Why `findOrCreate` changed from a SQL lookup to filtering `observeAll()`:** a built-in row's
stored `normalised_name` column is an inert placeholder (Task 6 explains why) — only the resolved,
in-memory value from `observeAll()` is ever correct to dedup against. This mirrors
`TagRepositoryImpl.findOrCreateUserTag()`, already in this same file, which does the same
in-Kotlin filtering for the same reason.

- [ ] **Step 3: Update `RecipeRepositoryImpl`**

```kotlin
class RecipeRepositoryImpl @Inject constructor(
    private val recipeDao: RecipeDao,
    private val ingredientDao: IngredientDao,
    private val namer: BuiltInIngredientNamer,
    private val time: TimeProvider,
) : RecipeRepository {

    override fun observeRecipes(filter: RecipeFilter): Flow<List<Recipe>> =
        recipeDao.filtered(
            query = filter.query.lowercase().trim(),
            tagIds = filter.tagIds,
            tagCount = filter.tagIds.size,
            ingredientIds = filter.ingredientIds,
            ingredientCount = filter.ingredientIds.size,
            sort = filter.sort.name,
        ).map { rows -> rows.map { it.toDomain(namer) } }

    override fun observeRecipe(id: String): Flow<Recipe?> =
        recipeDao.observeById(id).map { it?.toDomain(namer) }

    override fun observeRecipeCount(): Flow<Int> = recipeDao.observeRecipeCount()

    override suspend fun upsert(recipe: Recipe): String {
        val now = time.now()
        val stamped = recipe.copy(
            createdAt = if (recipe.createdAt == 0L) now else recipe.createdAt,
            updatedAt = now,
        )

        ingredientDao.upsertAll(stamped.ingredients.map { it.ingredient.toEntity() })

        recipeDao.upsertRecipe(
            recipe = stamped.toEntity(),
            lines = stamped.ingredients.mapIndexed { index, line ->
                line.copy(position = index).toEntity(stamped.id)
            },
            tagIds = stamped.tags.map { it.id },
        )
        return stamped.id
    }

    override suspend fun delete(id: String) = recipeDao.deleteRecipe(id)

    override suspend fun markCooked(id: String, at: Long) = recipeDao.markCooked(id, at)

    override suspend fun setFavourite(id: String, favourite: Boolean) =
        recipeDao.setFavourite(id, favourite, time.now())
}
```

(Only the constructor and the two `.toDomain(namer)` call sites changed; everything else in the
class — including the `// An ingredient line can carry an ingredient the catalogue has not seen
yet.` comment on `upsertAll` — is untouched.) Add the import
`com.ilsecondodasinistra.proportion.core.domain.BuiltInIngredientNamer`.

- [ ] **Step 4: Update `RepositoryTest.kt`'s `setUp()` and the one `.toEntity()`-on-a-copy test**

Add a fake namer at the top of the class:

```kotlin
private val namer = BuiltInIngredientNamer { key -> "[$key]" }
```

(Never actually exercised by this file's scenarios — every ingredient here is user-created — but a
required constructor argument now.) Add the import
`com.ilsecondodasinistra.proportion.core.domain.BuiltInIngredientNamer`.

Update `setUp()`:

```kotlin
db = Room.inMemoryDatabaseBuilder(
    ApplicationProvider.getApplicationContext(),
    ProPortionDatabase::class.java,
)
    .addCallback(ProPortionDatabase.seedCallback(ApplicationProvider.getApplicationContext()))
    .allowMainThreadQueries()
    .build()

recipes = RecipeRepositoryImpl(db.recipeDao(), db.ingredientDao(), namer, time)
ingredients = IngredientRepositoryImpl(db.ingredientDao(), namer)
```

No other line in this file needs to change — `flour.copy(densityGramsPerMl = 0.55).toEntity()` in
the `` `density survives the round trip...` `` test keeps compiling unchanged, since `toEntity()`'s
signature did not change, only its body (Task 4).

- [ ] **Step 5: Run the full test suite for this module**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: PASS, including every existing `RepositoryTest` scenario (`TransferRepositoryTest` will
still fail to compile at this point — that is Task 8's job to fix; if the build tool refuses to run
one test class while another fails to compile in the same module, run
`./gradlew :core:data:testDebugUnitTest --tests "*.RepositoryTest"` explicitly for this step and
defer the full-module run to the end of Task 8).

---

### Task 6: Seeding — `IngredientSeed`, the shared seed function, and the starter JSON asset

**Files:**
- Create: `core/database/src/main/assets/ingredients.json`
- Create: `core/database/src/main/kotlin/com/ilsecondodasinistra/proportion/core/database/IngredientSeed.kt`
- Modify: `core/database/src/main/kotlin/com/ilsecondodasinistra/proportion/core/database/ProPortionDatabase.kt`
- Modify: `core/database/src/test/kotlin/com/ilsecondodasinistra/proportion/core/database/MigrationTest.kt`
- Test: `core/database/src/test/kotlin/com/ilsecondodasinistra/proportion/core/database/IngredientSeedingTest.kt`

**Interfaces:**
- Consumes: `IngredientCategory`, `MeasureUnit` (Task 1, existing).
- Produces: `IngredientSeed(key: String, defaultUnit: MeasureUnit, category: IngredientCategory)`;
  `builtInIngredientId(key: String): String` (companion function on `ProPortionDatabase`, mirrors
  `builtInTagId`); seeding now runs from both `seedCallback(context)` and `Migration1to2`.

- [ ] **Step 1: Create the starter `ingredients.json` (the same 48 keys added to `strings.xml` in Task 3)**

```json
[
  { "key": "flour_00", "defaultUnit": "GRAM", "category": "FLOUR_AND_GRAIN" },
  { "key": "rice", "defaultUnit": "GRAM", "category": "FLOUR_AND_GRAIN" },
  { "key": "breadcrumbs", "defaultUnit": "GRAM", "category": "FLOUR_AND_GRAIN" },
  { "key": "egg", "defaultUnit": "EGG", "category": "DAIRY_AND_EGG" },
  { "key": "milk", "defaultUnit": "MILLILITRE", "category": "DAIRY_AND_EGG" },
  { "key": "parmesan", "defaultUnit": "GRAM", "category": "DAIRY_AND_EGG" },
  { "key": "olive_oil", "defaultUnit": "MILLILITRE", "category": "FAT_AND_OIL" },
  { "key": "butter", "defaultUnit": "GRAM", "category": "FAT_AND_OIL" },
  { "key": "lard", "defaultUnit": "GRAM", "category": "FAT_AND_OIL" },
  { "key": "sugar", "defaultUnit": "GRAM", "category": "SUGAR_AND_SWEETENER" },
  { "key": "honey", "defaultUnit": "GRAM", "category": "SUGAR_AND_SWEETENER" },
  { "key": "brown_sugar", "defaultUnit": "GRAM", "category": "SUGAR_AND_SWEETENER" },
  { "key": "baking_powder", "defaultUnit": "GRAM", "category": "LEAVENING_AND_BAKING" },
  { "key": "yeast", "defaultUnit": "GRAM", "category": "LEAVENING_AND_BAKING" },
  { "key": "baking_soda", "defaultUnit": "GRAM", "category": "LEAVENING_AND_BAKING" },
  { "key": "dark_chocolate", "defaultUnit": "GRAM", "category": "CHOCOLATE_AND_COCOA" },
  { "key": "cocoa_powder", "defaultUnit": "GRAM", "category": "CHOCOLATE_AND_COCOA" },
  { "key": "white_chocolate", "defaultUnit": "GRAM", "category": "CHOCOLATE_AND_COCOA" },
  { "key": "apple", "defaultUnit": "PIECE", "category": "FRUIT" },
  { "key": "lemon", "defaultUnit": "PIECE", "category": "FRUIT" },
  { "key": "banana", "defaultUnit": "PIECE", "category": "FRUIT" },
  { "key": "onion", "defaultUnit": "PIECE", "category": "VEGETABLE" },
  { "key": "tomato", "defaultUnit": "PIECE", "category": "VEGETABLE" },
  { "key": "potato", "defaultUnit": "PIECE", "category": "VEGETABLE" },
  { "key": "basil", "defaultUnit": "LEAF", "category": "HERB_AND_SPICE" },
  { "key": "black_pepper", "defaultUnit": "PINCH", "category": "HERB_AND_SPICE" },
  { "key": "cinnamon", "defaultUnit": "TEASPOON", "category": "HERB_AND_SPICE" },
  { "key": "chicken_breast", "defaultUnit": "GRAM", "category": "MEAT" },
  { "key": "ground_beef", "defaultUnit": "GRAM", "category": "MEAT" },
  { "key": "pancetta", "defaultUnit": "GRAM", "category": "MEAT" },
  { "key": "tuna", "defaultUnit": "GRAM", "category": "FISH_AND_SEAFOOD" },
  { "key": "shrimp", "defaultUnit": "GRAM", "category": "FISH_AND_SEAFOOD" },
  { "key": "anchovy", "defaultUnit": "PIECE", "category": "FISH_AND_SEAFOOD" },
  { "key": "chickpea", "defaultUnit": "GRAM", "category": "LEGUME" },
  { "key": "lentil", "defaultUnit": "GRAM", "category": "LEGUME" },
  { "key": "borlotti_bean", "defaultUnit": "GRAM", "category": "LEGUME" },
  { "key": "almond", "defaultUnit": "GRAM", "category": "NUT_AND_SEED" },
  { "key": "walnut", "defaultUnit": "GRAM", "category": "NUT_AND_SEED" },
  { "key": "pine_nut", "defaultUnit": "GRAM", "category": "NUT_AND_SEED" },
  { "key": "salt", "defaultUnit": "PINCH", "category": "CONDIMENT_AND_SAUCE" },
  { "key": "vinegar", "defaultUnit": "MILLILITRE", "category": "CONDIMENT_AND_SAUCE" },
  { "key": "mayonnaise", "defaultUnit": "GRAM", "category": "CONDIMENT_AND_SAUCE" },
  { "key": "red_wine", "defaultUnit": "MILLILITRE", "category": "BEVERAGE" },
  { "key": "espresso_coffee", "defaultUnit": "MILLILITRE", "category": "BEVERAGE" },
  { "key": "sparkling_water", "defaultUnit": "MILLILITRE", "category": "BEVERAGE" },
  { "key": "vanilla_extract", "defaultUnit": "TEASPOON", "category": "OTHER" },
  { "key": "gelatin_sheet", "defaultUnit": "PIECE", "category": "OTHER" },
  { "key": "water", "defaultUnit": "MILLILITRE", "category": "OTHER" }
]
```

- [ ] **Step 2: Create `IngredientSeed.kt`**

```kotlin
package com.ilsecondodasinistra.proportion.core.database

import com.ilsecondodasinistra.proportion.core.model.IngredientCategory
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import kotlinx.serialization.Serializable

@Serializable
data class IngredientSeed(
    val key: String,
    val defaultUnit: MeasureUnit,
    val category: IngredientCategory,
)
```

- [ ] **Step 3: Add the shared seeding function and wire it into both `onCreate` and the migration, in `ProPortionDatabase.kt`**

Add, inside the `companion object`, alongside `builtInTagId`:

```kotlin
fun builtInIngredientId(key: String): String = "builtin-$key"

/**
 * Seeds the built-in ingredient catalogue from the bundled JSON asset.
 *
 * Called from both [seedCallback] (fresh installs) and [Migration1to2] (existing installs
 * upgrading from schema 1): `Callback.onCreate` never fires for a database that already exists,
 * so an upgrading install would otherwise keep an empty catalogue forever.
 */
internal fun seedBuiltInIngredients(db: SupportSQLiteDatabase, context: Context) {
    val seeds: List<IngredientSeed> = context.assets.open("ingredients.json").use { stream ->
        Json.decodeFromStream(stream)
    }
    seeds.forEach { seed ->
        db.execSQL(
            "INSERT OR IGNORE INTO ingredients " +
                "(id, key, name, normalised_name, is_built_in, default_unit, category) " +
                "VALUES (?, ?, ?, ?, 1, ?, ?)",
            arrayOf<Any>(
                builtInIngredientId(seed.key),
                seed.key,
                seed.key,
                seed.key,
                seed.defaultUnit.name,
                seed.category.name,
            ),
        )
    }
}
```

The `name`/`normalised_name` columns are set to the raw `key` only to satisfy the `NOT NULL`
constraint — they are placeholders, never read for `isBuiltIn = true` rows; both are recomputed
from the resolved string every time `IngredientEntity.toDomain(namer)` runs (Task 4).

Add the imports `kotlinx.serialization.json.Json` and
`kotlinx.serialization.json.decodeFromStream`.

Update `seedCallback`'s body to also call it:

```kotlin
fun seedCallback(context: Context): Callback = object : Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        Tag.BUILT_IN_KEYS.forEachIndexed { index, key ->
            db.execSQL(
                "INSERT OR IGNORE INTO tags (id, key, name, is_built_in, color_index) " +
                    "VALUES (?, ?, NULL, 1, ?)",
                arrayOf<Any>(builtInTagId(key), key, index),
            )
        }
        seedBuiltInIngredients(db, context)
    }
}
```

Update `Migration1to2.migrate()` to also call it, after the three `ALTER TABLE` statements:

```kotlin
class Migration1to2(private val context: Context) : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ingredients ADD COLUMN key TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE ingredients ADD COLUMN is_built_in INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ingredients ADD COLUMN category TEXT DEFAULT NULL")
        seedBuiltInIngredients(db, context)
    }
}
```

- [ ] **Step 4: Extend `MigrationTest.kt` to assert seeding happened too**

Add a second test to the same class from Task 2:

```kotlin
@Test
fun `migrating from version 1 also seeds the built-in ingredient catalogue`() {
    helper.createDatabase(TEST_DB, 1).close()

    val v2 = helper.runMigrationsAndValidate(
        TEST_DB,
        2,
        true,
        Migration1to2(ApplicationProvider.getApplicationContext()),
    )

    val cursor = v2.query("SELECT COUNT(*) FROM ingredients WHERE is_built_in = 1")
    cursor.use {
        it.moveToFirst()
        assertThat(it.getInt(0)).isEqualTo(48)
    }
}
```

(This count will need updating to the final total at the end of Task 7 — noted there.)

- [ ] **Step 5: Run the migration tests**

Run: `./gradlew :core:database:testDebugUnitTest --tests "*.MigrationTest"`
Expected: PASS (both tests).

- [ ] **Step 6: Write the failing test for the fresh-install path — `IngredientSeedingTest.kt`**

```kotlin
package com.ilsecondodasinistra.proportion.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IngredientSeedingTest {

    private lateinit var db: ProPortionDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ProPortionDatabase::class.java,
        )
            .addCallback(ProPortionDatabase.seedCallback(ApplicationProvider.getApplicationContext()))
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a fresh database is seeded with the built-in ingredient catalogue`() = runTest {
        val all = db.ingredientDao().observeAll().first()

        assertThat(all).hasSize(48)
        assertThat(all.all { it.isBuiltIn }).isTrue()
        assertThat(all.map { it.id }).contains(ProPortionDatabase.builtInIngredientId("flour_00"))
    }

    @Test
    fun `seeding twice does not duplicate rows`() = runTest {
        // onCreate only ever runs once per database file in real use; this proves the INSERT OR
        // IGNORE guard holds if it were ever invoked again against the same file.
        val db2 = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ProPortionDatabase::class.java,
            "reseed-test.db",
        )
            .addCallback(ProPortionDatabase.seedCallback(ApplicationProvider.getApplicationContext()))
            .allowMainThreadQueries()
            .build()
        db2.close()
        val reopened = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ProPortionDatabase::class.java,
            "reseed-test.db",
        )
            .addCallback(ProPortionDatabase.seedCallback(ApplicationProvider.getApplicationContext()))
            .allowMainThreadQueries()
            .build()

        val all = reopened.ingredientDao().observeAll().first()

        assertThat(all).hasSize(48)
        reopened.close()
        ApplicationProvider.getApplicationContext<android.content.Context>().deleteDatabase("reseed-test.db")
    }
}
```

- [ ] **Step 7: Run it**

Run: `./gradlew :core:database:testDebugUnitTest --tests "*.IngredientSeedingTest"`
Expected: PASS.

---

### Task 7: Content generation — expand the catalogue to 400-600 entries

**Files:**
- Modify: `core/database/src/main/assets/ingredients.json`
- Modify: `core/ui/src/main/res/values/strings.xml`
- Modify: `core/ui/src/main/res/values-it/strings.xml`
- Modify: `core/database/src/test/kotlin/com/ilsecondodasinistra/proportion/core/database/MigrationTest.kt`
- Modify: `core/database/src/test/kotlin/com/ilsecondodasinistra/proportion/core/database/IngredientSeedingTest.kt`
- Test: `core/ui/src/test/kotlin/com/ilsecondodasinistra/proportion/core/ui/IngredientResourceConsistencyTest.kt`

**Interfaces:** none new — this task only adds data following the exact format Task 6 already
proved works end to end (seeding, migration, repository resolution, autocomplete).

This is a content task, not a code task: extend the same three files (`ingredients.json`,
`values/strings.xml`, `values-it/strings.xml`) from 48 entries to somewhere in the 400-600 range,
covering common sweet and savoury ingredients across all 16 `IngredientCategory` values — the same
shape as the 48 already there (see Task 6 Step 1 and Task 3 Steps 3-4 for the exact format and a
worked example in every category). Keep `key` values `snake_case`, English and unique; keep every
Italian translation natural, not machine-literal (e.g. `"baking_soda"` → *"Bicarbonato di sodio"*,
not a literal word-for-word rendering). Do not duplicate a key that already exists in the 48.

The **hard completion gate** is the resource-consistency test below, plus the count assertion —
finish the content, then make the test pass, rather than guessing when "enough" has been written.

- [ ] **Step 1: Write the failing resource-consistency test — `IngredientResourceConsistencyTest.kt`**

This is a plain JVM test (no Robolectric/Android needed): it parses `core/ui`'s own two
`strings.xml` files and `core/database`'s `ingredients.json` directly off disk, using the fixed
relative path between the two sibling Gradle modules.

```kotlin
package com.ilsecondodasinistra.proportion.core.ui

import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class IngredientResourceConsistencyTest {

    @Test
    fun `every seeded ingredient key has a matching string resource in every supported language`() {
        val keys = seededKeys()

        assertThat(keys.size).isAtLeast(400)
        assertThat(keys.size).isAtMost(600)

        val englishNames = stringNames(File("src/main/res/values/strings.xml"))
        val italianNames = stringNames(File("src/main/res/values-it/strings.xml"))

        keys.forEach { key ->
            assertThat(englishNames).contains("ingredient_$key")
            assertThat(italianNames).contains("ingredient_$key")
        }
    }

    private fun seededKeys(): List<String> {
        val jsonFile = File("../database/src/main/assets/ingredients.json")
        val parsed = Json.parseToJsonElement(jsonFile.readText()) as JsonArray
        return parsed.map { it.jsonObject.getValue("key").jsonPrimitive.content }
    }

    private fun stringNames(file: File): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("name").nodeValue }.toSet()
    }
}
```

Add `testImplementation(libs.kotlinx.serialization.json)` to `core/ui/build.gradle.kts` if it is
not already a test dependency there (check first — `core/database` already depends on it as a main
dependency, `core/ui` may not).

- [ ] **Step 2: Run it to see it fail on count (before adding content)**

Run: `./gradlew :core:ui:test --tests "*.IngredientResourceConsistencyTest"`
Expected: FAIL — `keys.size` is 48, less than the required 400 minimum.

- [ ] **Step 3: Expand `ingredients.json`, `values/strings.xml` and `values-it/strings.xml` together**

Add entries in batches, covering (non-exhaustively — use judgement to round out each category to a
realistic real-world size): more flours and grains (semolina, cornstarch, oats, barley,
breadsticks...), more dairy (mozzarella, ricotta, mascarpone, cream, yogurt, gorgonzola...), more
vegetables and fruit (courgette, aubergine, carrot, spinach, garlic, celery, orange, strawberry,
peach, pear, apricot, fig, grape...), more meat and cured meats (pork loin, sausage, prosciutto,
turkey, rabbit, lamb...), more fish (salmon, cod, sea bream, mussels, clams, squid...), more herbs
and spices (rosemary, thyme, sage, oregano, parsley, saffron, nutmeg, chili, paprika...), more
legumes and grains (barley, farro, quinoa, split peas...), more nuts (hazelnuts, pistachios,
cashews, pumpkin seeds...), more condiments (mustard, soy sauce, tomato paste, capers, olives,
stock cube...), more beverages (white wine, beer, orange juice, tea...), and enough of everything
else to comfortably land the total between 400 and 600. Every entry needs all three: the JSON seed
line, the English string, the Italian string — add them together per ingredient, not in three
separate passes, to avoid drifting out of sync.

- [ ] **Step 4: Run the consistency test again, iterate until it passes**

Run: `./gradlew :core:ui:test --tests "*.IngredientResourceConsistencyTest"`
Expected: PASS once the count is within 400-600 and every key resolves in both languages.

- [ ] **Step 5: Update the two `MigrationTest`/`IngredientSeedingTest` row-count assertions**

The `.isEqualTo(48)` assertions added in Task 6 (one in
`core/database/.../MigrationTest.kt`'s second test, one in
`core/database/.../IngredientSeedingTest.kt`'s first test) now need the real final count. Replace
both with the same range check used above:

```kotlin
assertThat(it.getInt(0)).isAtLeast(400)
assertThat(it.getInt(0)).isAtMost(600)
```

(and correspondingly `assertThat(all.size).isAtLeast(400)` / `.isAtMost(600)` in
`IngredientSeedingTest`, replacing `hasSize(48)`).

- [ ] **Step 6: Run the full `core:database` and `core:ui` test suites**

Run: `./gradlew :core:database:testDebugUnitTest :core:ui:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 7: Confirm string parity across languages with the project's own script**

Run: `./scripts/check-string-parity.sh`
Expected: no missing-key report for the new `ingredient_*` strings (this script is the project's
existing, independent check — a second guard alongside the new consistency test, catching e.g. a
key present in `values-it` but missing in `values`, which the consistency test alone would not
notice if `values-it` happened to have extras).

---

### Task 8: `.proportion` export/import fix for built-in ingredients

**Files:**
- Modify: `core/transfer/src/main/kotlin/com/ilsecondodasinistra/proportion/core/transfer/ProportionFile.kt`
- Modify: `core/transfer/src/main/kotlin/com/ilsecondodasinistra/proportion/core/transfer/ProportionCodec.kt`
- Modify: `core/data/src/main/kotlin/com/ilsecondodasinistra/proportion/core/data/repository/TransferRepositoryImpl.kt`
- Modify: `core/data/src/test/kotlin/com/ilsecondodasinistra/proportion/core/data/TransferRepositoryTest.kt`

**Interfaces:**
- Consumes: `IngredientDao.findByKey` (Task 5), `BuiltInIngredientNamer` (Task 3),
  `IngredientEntity.toDomain(namer)` (Task 4).
- Produces: `ProportionFile.BUILT_IN_INGREDIENT_PREFIX: String`;
  `TransferRepositoryImpl(recipeRepository, ingredientRepository, ingredientDao: IngredientDao,
  namer: BuiltInIngredientNamer, tagRepository, recipeDao, tagDao, time)` (two new constructor
  params).

- [ ] **Step 1: Add the prefix constant to `ProportionFile.kt`**

```kotlin
/** Built-in ingredients travel by key too, same reason as [BUILT_IN_TAG_PREFIX]. */
const val BUILT_IN_INGREDIENT_PREFIX = "builtin:"
```

Add this line in the companion object, right after `BUILT_IN_TAG_PREFIX`.

- [ ] **Step 2: Write the ingredient's key into the wire format in `ProportionCodec.kt`**

Find the block that currently builds `WireIngredient(name = line.ingredient.name, ...)` and change
the `name` line to:

```kotlin
name = line.ingredient.key?.let { "${ProportionFile.BUILT_IN_INGREDIENT_PREFIX}$it" }
    ?: line.ingredient.name,
```

(Every other field of that `WireIngredient(...)` construction is untouched.)

- [ ] **Step 3: Add `resolveIngredient()` to `TransferRepositoryImpl` and update its constructor**

```kotlin
class TransferRepositoryImpl @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
    private val ingredientDao: IngredientDao,
    private val namer: BuiltInIngredientNamer,
    private val tagRepository: TagRepository,
    private val recipeDao: RecipeDao,
    private val tagDao: TagDao,
    private val time: TimeProvider,
) : TransferRepository {
```

Add the imports `com.ilsecondodasinistra.proportion.core.database.dao.IngredientDao` and
`com.ilsecondodasinistra.proportion.core.domain.BuiltInIngredientNamer`.

Inside `WireRecipe.toRecipe()`, change:

```kotlin
ingredient = ingredientRepository.findOrCreate(wire.name, unit),
```

to:

```kotlin
ingredient = resolveIngredient(wire, unit),
```

Add the new private function, near `resolveTag()`:

```kotlin
private suspend fun resolveIngredient(wire: WireIngredient, unit: MeasureUnit): Ingredient =
    if (wire.name.startsWith(ProportionFile.BUILT_IN_INGREDIENT_PREFIX)) {
        val key = wire.name.removePrefix(ProportionFile.BUILT_IN_INGREDIENT_PREFIX)
        // An unknown built-in key comes from a newer app: fall back to a literal ingredient rather
        // than dropping the line — unlike a tag, an ingredient line is not optional.
        ingredientDao.findByKey(key)?.toDomain(namer) ?: ingredientRepository.findOrCreate(key, unit)
    } else {
        ingredientRepository.findOrCreate(wire.name, unit)
    }
```

Add the import `com.ilsecondodasinistra.proportion.core.model.Ingredient`.

- [ ] **Step 4: Update `TransferRepositoryTest.kt`'s `setUp()` for the new constructor**

```kotlin
val namer = BuiltInIngredientNamer { key -> "[$key]" }
recipes = RecipeRepositoryImpl(db.recipeDao(), db.ingredientDao(), namer, time)
ingredients = IngredientRepositoryImpl(db.ingredientDao(), namer)
transfer = TransferRepositoryImpl(
    recipeRepository = recipes,
    ingredientRepository = ingredients,
    ingredientDao = db.ingredientDao(),
    namer = namer,
    tagRepository = TagRepositoryImpl(db.tagDao()),
    recipeDao = db.recipeDao(),
    tagDao = db.tagDao(),
    time = time,
)
```

Add the import `com.ilsecondodasinistra.proportion.core.domain.BuiltInIngredientNamer`. Also fix
this file's own `.addCallback(ProPortionDatabase.seedCallback())` call the same way as Task 2 Step
5 and Task 5 Step 4 (`seedCallback(ApplicationProvider.getApplicationContext())`).

- [ ] **Step 5: Write the failing tests for the new round-trip behaviour**

Add to `TransferRepositoryTest.kt`:

```kotlin
@Test
fun `a built-in ingredient exports by key, not by its localised name`() = runTest {
    val flour = ingredients.findOrCreate("flour_00", MeasureUnit.GRAM) // seeded row, found by key search below
    // The seeded row's actual key-based id is what matters here, not this literal lookup — assert
    // directly against a known seeded key instead.
    val builtIn = db.ingredientDao().findByKey("flour_00")!!.toDomain(namer)
    recipes.upsert(
        Recipe(
            id = "r-flour",
            title = "Pane",
            servings = 1,
            steps = emptyList(),
            ingredients = listOf(RecipeIngredient("l-1", builtIn, 0, 500.0, MeasureUnit.GRAM)),
            tags = emptyList(),
        ),
    )

    val text = transfer.exportRecipe("r-flour")!!

    assertThat(text).contains("builtin:flour_00")
    assertThat(text).doesNotContain(builtIn.name)
}

@Test
fun `importing a built-in ingredient by key binds to the seeded row`() = runTest {
    val builtIn = db.ingredientDao().findByKey("flour_00")!!.toDomain(namer)
    recipes.upsert(
        Recipe(
            id = "r-flour",
            title = "Pane",
            servings = 1,
            steps = emptyList(),
            ingredients = listOf(RecipeIngredient("l-1", builtIn, 0, 500.0, MeasureUnit.GRAM)),
            tags = emptyList(),
        ),
    )
    val text = transfer.exportRecipe("r-flour")!!
    db.recipeDao().deleteAllRecipes()

    transfer.import(text, ImportMode.MERGE)

    val restored = recipes.observeRecipes().first().single()
    assertThat(restored.ingredients.single().ingredient.id).isEqualTo(builtIn.id)
    assertThat(ingredients.observeAll().first().count { it.isBuiltIn }).isAtLeast(1)
}

@Test
fun `importing an unrecognised built-in key falls back to a literal ingredient`() = runTest {
    val text = transfer.exportAll().let {
        // simulate a wire recipe referencing a built-in key this catalogue does not have
        it.replace("\"builtin:flour_00\"", "\"builtin:no_such_key_yet\"")
    }
    // exportAll on an empty library produces an empty recipes list, so build the wire text by hand
    // instead: reuse storeCake()'s shape but with a hand-crafted future-key ingredient.
    storeCake()
    val exported = transfer.exportRecipe("r-cake")!!
        .replace("\"Farina 00\"", "\"builtin:no_such_key_yet\"")
    db.recipeDao().deleteAllRecipes()

    transfer.import(exported, ImportMode.MERGE)

    val restored = recipes.observeRecipes().first().single()
    val fallback = restored.ingredients.first { it.ingredient.normalisedName.contains("no_such_key_yet") }
    assertThat(fallback.ingredient.isBuiltIn).isFalse()
}
```

Remove the unused first `val text = ...` line in the last test above before running it — it is
dead code left over from drafting; the real setup is the `storeCake()` line that follows. (Keeping
this note explicit since it is exactly the kind of leftover a fresh implementer should delete
rather than ship.)

- [ ] **Step 6: Run the full `core:data` and `core:transfer` test suites**

Run: `./gradlew :core:data:testDebugUnitTest :core:transfer:test`
Expected: PASS.

---

### Task 9: Final integration — full check, on-device verification, docs

**Files:**
- Modify: `docs/private/IMPLEMENTATION-STATUS.md`

No new production code in this task — it closes out the phase.

- [ ] **Step 1: Run the full verification suite**

Run:
```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
./gradlew verifyAll
```
Expected: green — detekt (`maxIssues: 0`), every module's tests, lint, a debug APK. Pay particular
attention to detekt on `AndroidIngredientNamer.kt` (no accidental `LongMethod`) and on the expanded
`ingredients.json`/`strings.xml` files (`MaxLineLength` does not apply to XML/JSON, but any new
Kotlin touched in Task 7's edits does still need to pass).

- [ ] **Step 2: Install and walk through the feature on the attached device**

Run: `./gradlew installDebug` (Fairphone 3 / Android 13, per this project's established device).
In the app: open the editor, start typing an ingredient name a few letters in (e.g. "farin",
"choc"), confirm the autocomplete now surfaces built-in catalogue matches, not just previously
user-typed ones; pick one, save the recipe, reopen it, confirm the name round-trips correctly.
Switch the app language (Settings, per the language selector shipped just before this phase) and
confirm the same ingredient now shows its English/Italian name as appropriate, both in the
autocomplete list and inside a previously-saved recipe using it. Export that recipe via Settings'
existing share/backup flow, note the exported text contains `builtin:<key>`, not a literal name
(open the exported file/text and check by eye). Take screenshots of the autocomplete list and of
the language-switch result, per this project's standing verification practice.

- [ ] **Step 3: Update `docs/private/IMPLEMENTATION-STATUS.md`**

Move phase 8 from "what is next" into the completed-work narrative, following the exact style of
the "Added after phase 7, 2026-09-03: ..." entries already in that file: what shipped, the final
ingredient count, the migration/seeding duplication gotcha and why it exists, the export/import
key-based fix, and the screenshots/verification just performed. Update the "Last updated" line at
the bottom of the file to the current date and this phase's summary, matching the existing format.

- [ ] **Step 4: Decide whether this phase also touches `docs/public`/`docs/manual`**

Per Marco's standing rule (`docs/private/plans/../proportion-working-agreement` memory, restated
2026-09-03: "ogni modifica se significativa va documentata"), check whether the ingredient
catalogue changes user-visible behaviour enough to need a manual update (the autocomplete UI itself
is unchanged — only the data behind it grew — so this is likely a private-docs-only change, but
confirm against the current `docs/manual` ingredient-entry walkthrough before assuming so; update
it if it undersells or misdescribes the now much richer autocomplete).

---

## Self-review notes (writing-plans skill, run against the spec)

**Spec coverage:** §1 (model) → Task 1. §2 (database/migration/seeding) → Tasks 2, 6, 7. §3
(namer + normalisedName-at-read-time + ripple through mappers) → Tasks 3, 4. §4 (repository
changes, `findOrCreate` rewrite) → Task 5. §5 (transfer fix) → Task 8. §6 (testing) → a test step
inside every task above, plus the dedicated migration/seeding/resource-consistency tests. §7
(out of scope) → nothing in this plan touches `IngredientCategory` UI, density conversion, or
reconciling pre-existing user-typed duplicates against the new seed — confirmed absent by design.

**Type consistency check:** `Ingredient`'s constructor order (`id, key, name, normalisedName,
isBuiltIn, defaultUnit, category, densityGramsPerMl`) is used identically in Tasks 1, 4, 5, 8.
`BuiltInIngredientNamer.name(key: String): String` is the same signature everywhere it is called
(Tasks 3-6, 8). `ProPortionDatabase.seedCallback(context: Context)` and
`ProPortionDatabase.builtInIngredientId(key: String)` are named and typed consistently from their
introduction (Task 2 / Task 6) through every later call site.

**Placeholder scan:** the only intentionally-open-ended step is Task 7's content expansion, which
is not a placeholder — it has an exact format (proven by Task 6), an exact range (400-600), and an
automated pass/fail gate (`IngredientResourceConsistencyTest`) rather than a vague instruction.
