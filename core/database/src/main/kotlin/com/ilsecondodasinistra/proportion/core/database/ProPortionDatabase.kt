package com.ilsecondodasinistra.proportion.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ilsecondodasinistra.proportion.core.database.dao.IngredientDao
import com.ilsecondodasinistra.proportion.core.database.dao.RecipeDao
import com.ilsecondodasinistra.proportion.core.database.dao.ScaleVariantDao
import com.ilsecondodasinistra.proportion.core.database.dao.ShoppingDao
import com.ilsecondodasinistra.proportion.core.database.dao.TagDao
import com.ilsecondodasinistra.proportion.core.database.entity.IngredientEntity
import com.ilsecondodasinistra.proportion.core.database.entity.RecipeEntity
import com.ilsecondodasinistra.proportion.core.database.entity.RecipeIngredientEntity
import com.ilsecondodasinistra.proportion.core.database.entity.RecipeTagCrossRef
import com.ilsecondodasinistra.proportion.core.database.entity.ScaleVariantEntity
import com.ilsecondodasinistra.proportion.core.database.entity.ShoppingItemEntity
import com.ilsecondodasinistra.proportion.core.database.entity.TagEntity
import com.ilsecondodasinistra.proportion.core.model.Tag
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

@Database(
    entities = [
        RecipeEntity::class,
        IngredientEntity::class,
        RecipeIngredientEntity::class,
        TagEntity::class,
        RecipeTagCrossRef::class,
        ScaleVariantEntity::class,
        ShoppingItemEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ProPortionDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao
    abstract fun ingredientDao(): IngredientDao
    abstract fun tagDao(): TagDao
    abstract fun scaleVariantDao(): ScaleVariantDao
    abstract fun shoppingDao(): ShoppingDao

    companion object {

        const val NAME = "proportion.db"

        /**
         * Seeds the built-in tags on first creation.
         *
         * Ids are derived from the key, so importing a recipe that references `builtin:dessert`
         * lands on the same row on every device.
         */
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
                seedIngredientDensities(db, context)
            }
        }

        fun builtInTagId(key: String): String = "builtin-$key"

        fun builtInIngredientId(key: String): String = "builtin-$key"

        /**
         * Seeds the built-in ingredient catalogue from the bundled JSON asset.
         *
         * Called from both [seedCallback] (fresh installs) and [Migration1to2] (existing installs
         * upgrading from schema 1): `Callback.onCreate` never fires for a database that already
         * exists, so an upgrading install would otherwise keep an empty catalogue forever.
         */
        @OptIn(ExperimentalSerializationApi::class)
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

        /**
         * Backfills `density_g_per_ml`/`item_weight_grams` on the built-in catalogue rows from the
         * same bundled asset. Split from [seedBuiltInIngredients] because it needs the
         * `item_weight_grams` column (added in schema 3) and must also run against rows that were
         * already inserted by an earlier migration/`onCreate` — an `UPDATE`, not an `INSERT`.
         */
        @OptIn(ExperimentalSerializationApi::class)
        internal fun seedIngredientDensities(db: SupportSQLiteDatabase, context: Context) {
            val seeds: List<IngredientSeed> = context.assets.open("ingredients.json").use { stream ->
                Json.decodeFromStream(stream)
            }
            seeds.forEach { seed ->
                db.execSQL(
                    "UPDATE ingredients SET density_g_per_ml = ?, item_weight_grams = ? WHERE id = ?",
                    arrayOf<Any?>(seed.density, seed.itemWeightGrams, builtInIngredientId(seed.key)),
                )
            }
        }
    }
}

class Migration1to2(private val context: Context) : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ingredients ADD COLUMN key TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE ingredients ADD COLUMN is_built_in INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ingredients ADD COLUMN category TEXT DEFAULT NULL")
        ProPortionDatabase.seedBuiltInIngredients(db, context)
    }
}

class Migration2to3(private val context: Context) : Migration(SCHEMA_2, SCHEMA_3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ingredients ADD COLUMN item_weight_grams REAL DEFAULT NULL")
        ProPortionDatabase.seedIngredientDensities(db, context)
    }

    private companion object {
        const val SCHEMA_2 = 2
        const val SCHEMA_3 = 3
    }
}

/**
 * Adds what phase 10 (folder sync) needs: a soft-delete tombstone on recipes (a hard `DELETE`
 * would give the other device no way to tell "never existed" from "deleted after they last
 * synced"), and an `updated_at` on the literal (non built-in) catalogue rows so a sync conflict
 * can be resolved by recency. Built-in rows are seeded identically on every install and never
 * sync, so they get the column but never a meaningful value in it.
 */
class Migration3to4 : Migration(SCHEMA_3, SCHEMA_4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipes ADD COLUMN deleted_at INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE ingredients ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tags ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
    }

    private companion object {
        const val SCHEMA_3 = 3
        const val SCHEMA_4 = 4
    }
}
