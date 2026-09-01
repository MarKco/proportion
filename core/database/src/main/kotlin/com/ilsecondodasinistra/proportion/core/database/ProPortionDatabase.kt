package com.ilsecondodasinistra.proportion.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    version = 1,
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
        fun seedCallback(): Callback = object : Callback() {
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

        fun builtInTagId(key: String): String = "builtin-$key"
    }
}
