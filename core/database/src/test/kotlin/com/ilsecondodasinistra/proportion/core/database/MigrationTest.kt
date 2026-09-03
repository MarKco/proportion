package com.ilsecondodasinistra.proportion.core.database

import androidx.room.testing.MigrationTestHelper
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
        ProPortionDatabase::class.java,
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
            assertThat(it.getInt(0)).isAtLeast(400)
            assertThat(it.getInt(0)).isAtMost(600)
        }
    }

    @Test
    fun `migrating from version 2 adds item_weight_grams and backfills density on built-in rows`() {
        val v2 = helper.createDatabase(TEST_DB, 2)
        v2.execSQL(
            "INSERT INTO ingredients (id, key, name, normalised_name, is_built_in, default_unit, category) " +
                "VALUES ('builtin-flour_00', 'flour_00', 'flour_00', 'flour_00', 1, 'GRAM', 'FLOUR_AND_GRAIN')",
        )
        v2.execSQL(
            "INSERT INTO ingredients (id, name, normalised_name, is_built_in, default_unit) " +
                "VALUES ('ing-user', 'La mia farina', 'la mia farina', 0, 'GRAM')",
        )
        v2.close()

        val v3 = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            Migration2to3(ApplicationProvider.getApplicationContext()),
        )

        val builtIn = v3.query(
            "SELECT density_g_per_ml, item_weight_grams FROM ingredients WHERE id = 'builtin-flour_00'",
        )
        builtIn.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getDouble(it.getColumnIndexOrThrow("density_g_per_ml"))).isEqualTo(0.53)
            assertThat(it.isNull(it.getColumnIndexOrThrow("item_weight_grams"))).isTrue()
        }

        // A user-created row was never in the seed asset: it must stay untouched, not zeroed out.
        val user = v3.query(
            "SELECT density_g_per_ml, item_weight_grams FROM ingredients WHERE id = 'ing-user'",
        )
        user.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.isNull(it.getColumnIndexOrThrow("density_g_per_ml"))).isTrue()
            assertThat(it.isNull(it.getColumnIndexOrThrow("item_weight_grams"))).isTrue()
        }
    }

    @Test
    fun `migrating from version 3 adds deleted_at on recipes without losing existing rows`() {
        val v3 = helper.createDatabase(TEST_DB, 3)
        v3.execSQL(
            "INSERT INTO recipes " +
                "(id, title, servings, steps, is_favourite, cook_count, created_at, updated_at) " +
                "VALUES ('r-cake', 'Torta', 4, '[]', 0, 0, 100, 100)",
        )
        v3.close()

        val v4 = helper.runMigrationsAndValidate(TEST_DB, 4, true, Migration3to4())

        val cursor = v4.query("SELECT title, deleted_at FROM recipes WHERE id = 'r-cake'")
        cursor.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(it.getColumnIndexOrThrow("title"))).isEqualTo("Torta")
            assertThat(it.isNull(it.getColumnIndexOrThrow("deleted_at"))).isTrue()
        }
    }

    @Test
    fun `migrating from version 3 adds updated_at on ingredients and tags, defaulting to zero`() {
        val v3 = helper.createDatabase(TEST_DB, 3)
        v3.execSQL(
            "INSERT INTO ingredients (id, name, normalised_name, is_built_in, default_unit) " +
                "VALUES ('ing-user', 'La mia farina', 'la mia farina', 0, 'GRAM')",
        )
        v3.execSQL(
            "INSERT INTO tags (id, key, name, is_built_in, color_index) " +
                "VALUES ('tag-user', NULL, 'Ricette di famiglia', 0, 1)",
        )
        v3.close()

        val v4 = helper.runMigrationsAndValidate(TEST_DB, 4, true, Migration3to4())

        val ingredient = v4.query("SELECT updated_at FROM ingredients WHERE id = 'ing-user'")
        ingredient.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getLong(it.getColumnIndexOrThrow("updated_at"))).isEqualTo(0L)
        }

        val tag = v4.query("SELECT updated_at FROM tags WHERE id = 'tag-user'")
        tag.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getLong(it.getColumnIndexOrThrow("updated_at"))).isEqualTo(0L)
        }
    }

    @Test
    fun `migrating from version 4 creates the sync cache tables, empty and usable`() {
        helper.createDatabase(TEST_DB, 4).close()

        val v5 = helper.runMigrationsAndValidate(TEST_DB, 5, true, Migration4to5())

        v5.execSQL(
            "INSERT INTO sync_export_cache (entity_id, exported_updated_at) VALUES ('r-cake', 100)",
        )
        v5.execSQL(
            "INSERT INTO sync_seen_file (file_name, last_modified) VALUES ('recipe-r-cake.proportion', 200)",
        )

        val exportCache = v5.query("SELECT exported_updated_at FROM sync_export_cache WHERE entity_id = 'r-cake'")
        exportCache.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getLong(it.getColumnIndexOrThrow("exported_updated_at"))).isEqualTo(100L)
        }

        val seenFile = v5.query(
            "SELECT last_modified FROM sync_seen_file WHERE file_name = 'recipe-r-cake.proportion'",
        )
        seenFile.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getLong(it.getColumnIndexOrThrow("last_modified"))).isEqualTo(200L)
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
