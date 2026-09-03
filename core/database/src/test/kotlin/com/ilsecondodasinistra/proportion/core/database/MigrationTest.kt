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

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
