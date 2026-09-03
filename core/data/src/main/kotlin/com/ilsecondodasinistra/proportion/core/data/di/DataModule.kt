package com.ilsecondodasinistra.proportion.core.data.di

import android.content.Context
import androidx.room.Room
import com.ilsecondodasinistra.proportion.core.data.repository.IngredientRepositoryImpl
import com.ilsecondodasinistra.proportion.core.data.repository.PreferencesRepositoryImpl
import com.ilsecondodasinistra.proportion.core.data.repository.RecipeRepositoryImpl
import com.ilsecondodasinistra.proportion.core.data.repository.ScaleVariantRepositoryImpl
import com.ilsecondodasinistra.proportion.core.data.repository.ShoppingRepositoryImpl
import com.ilsecondodasinistra.proportion.core.data.repository.SyncRepositoryImpl
import com.ilsecondodasinistra.proportion.core.data.repository.TagRepositoryImpl
import com.ilsecondodasinistra.proportion.core.data.sync.WorkManagerSyncScheduler
import com.ilsecondodasinistra.proportion.core.data.repository.TransferRepositoryImpl
import com.ilsecondodasinistra.proportion.core.database.Migration1to2
import com.ilsecondodasinistra.proportion.core.database.Migration2to3
import com.ilsecondodasinistra.proportion.core.database.Migration3to4
import com.ilsecondodasinistra.proportion.core.database.Migration4to5
import com.ilsecondodasinistra.proportion.core.database.ProPortionDatabase
import com.ilsecondodasinistra.proportion.core.database.dao.IngredientDao
import com.ilsecondodasinistra.proportion.core.database.dao.RecipeDao
import com.ilsecondodasinistra.proportion.core.database.dao.ScaleVariantDao
import com.ilsecondodasinistra.proportion.core.database.dao.ShoppingDao
import com.ilsecondodasinistra.proportion.core.database.dao.SyncCacheDao
import com.ilsecondodasinistra.proportion.core.database.dao.TagDao
import com.ilsecondodasinistra.proportion.core.domain.SyncScheduler
import com.ilsecondodasinistra.proportion.core.domain.TimeProvider
import com.ilsecondodasinistra.proportion.core.domain.repository.IngredientRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.PreferencesRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.RecipeRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.ScaleVariantRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.ShoppingRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.SyncRepository
import com.ilsecondodasinistra.proportion.core.domain.repository.TagRepository
import com.ilsecondodasinistra.proportion.core.transfer.TransferRepository
import com.ilsecondodasinistra.proportion.core.domain.scale.BakingAdvisor
import com.ilsecondodasinistra.proportion.core.domain.scale.DefaultRecipeScaler
import com.ilsecondodasinistra.proportion.core.domain.scale.DiscreteAnalyser
import com.ilsecondodasinistra.proportion.core.domain.scale.RecipeScaler
import com.ilsecondodasinistra.proportion.core.domain.unit.DefaultUnitConverter
import com.ilsecondodasinistra.proportion.core.domain.unit.QuantityFormatter
import com.ilsecondodasinistra.proportion.core.domain.unit.UnitConverter
import com.ilsecondodasinistra.proportion.core.domain.unit.UnitNamer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ProPortionDatabase =
        Room.databaseBuilder(context, ProPortionDatabase::class.java, ProPortionDatabase.NAME)
            .addMigrations(Migration1to2(context), Migration2to3(context), Migration3to4(), Migration4to5())
            .addCallback(ProPortionDatabase.seedCallback(context))
            .build()

    @Provides fun recipeDao(db: ProPortionDatabase): RecipeDao = db.recipeDao()
    @Provides fun ingredientDao(db: ProPortionDatabase): IngredientDao = db.ingredientDao()
    @Provides fun tagDao(db: ProPortionDatabase): TagDao = db.tagDao()
    @Provides fun variantDao(db: ProPortionDatabase): ScaleVariantDao = db.scaleVariantDao()
    @Provides fun shoppingDao(db: ProPortionDatabase): ShoppingDao = db.shoppingDao()
    @Provides fun syncCacheDao(db: ProPortionDatabase): SyncCacheDao = db.syncCacheDao()

    @Provides
    @Singleton
    fun json(): Json = Json {
        // A file written by a later version must import, minus the fields this version cannot read.
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun timeProvider(): TimeProvider = TimeProvider { System.currentTimeMillis() }

    @Provides
    @Singleton
    fun unitConverter(): UnitConverter = DefaultUnitConverter()

    @Provides
    @Singleton
    fun quantityFormatter(converter: UnitConverter, namer: UnitNamer): QuantityFormatter =
        QuantityFormatter(converter, namer)

    @Provides
    @Singleton
    fun discreteAnalyser(formatter: QuantityFormatter): DiscreteAnalyser = DiscreteAnalyser(formatter)

    @Provides
    @Singleton
    fun bakingAdvisor(): BakingAdvisor = BakingAdvisor()

    @Provides
    @Singleton
    fun recipeScaler(
        converter: UnitConverter,
        formatter: QuantityFormatter,
        discreteAnalyser: DiscreteAnalyser,
        bakingAdvisor: BakingAdvisor,
    ): RecipeScaler = DefaultRecipeScaler(converter, formatter, discreteAnalyser, bakingAdvisor)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {

    @Binds abstract fun recipeRepository(impl: RecipeRepositoryImpl): RecipeRepository
    @Binds abstract fun ingredientRepository(impl: IngredientRepositoryImpl): IngredientRepository
    @Binds abstract fun tagRepository(impl: TagRepositoryImpl): TagRepository
    @Binds abstract fun scaleVariantRepository(impl: ScaleVariantRepositoryImpl): ScaleVariantRepository
    @Binds abstract fun shoppingRepository(impl: ShoppingRepositoryImpl): ShoppingRepository
    @Binds abstract fun preferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository
    @Binds abstract fun transferRepository(impl: TransferRepositoryImpl): TransferRepository
    @Binds abstract fun syncRepository(impl: SyncRepositoryImpl): SyncRepository
    @Binds abstract fun syncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler
}
