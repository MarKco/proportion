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

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
