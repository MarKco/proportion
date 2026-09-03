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
    version = 2,
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
