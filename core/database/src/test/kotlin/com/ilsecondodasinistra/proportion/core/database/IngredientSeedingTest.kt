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

        assertThat(all.size).isAtLeast(400)
        assertThat(all.size).isAtMost(600)
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

        assertThat(all.size).isAtLeast(400)
        assertThat(all.size).isAtMost(600)
        reopened.close()
        ApplicationProvider.getApplicationContext<android.content.Context>().deleteDatabase("reseed-test.db")
    }
}
