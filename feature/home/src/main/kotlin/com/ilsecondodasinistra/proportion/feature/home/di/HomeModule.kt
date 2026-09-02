package com.ilsecondodasinistra.proportion.feature.home.di

import com.ilsecondodasinistra.proportion.core.domain.dashboard.DashboardSummariser
import com.ilsecondodasinistra.proportion.core.domain.dashboard.RecipePicker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.random.Random

/**
 * `DashboardSummariser` and `RecipePicker` (`:core:domain`) carry no `@Inject constructor`: that
 * module has no reason to know about Hilt. Dagger also ignores a Kotlin default parameter value —
 * it still demands a binding for every constructor argument, `random` included — so `HomeViewModel`
 * needs all three provided here despite its `random: Random = Random.Default` default, which exists
 * only to let tests construct the view model directly with a seeded value.
 */
@Module
@InstallIn(SingletonComponent::class)
object HomeModule {

    @Provides
    @Singleton
    fun dashboardSummariser(): DashboardSummariser = DashboardSummariser()

    @Provides
    @Singleton
    fun recipePicker(): RecipePicker = RecipePicker()

    /** [SuggestionRandom]-qualified: see that annotation for why this cannot be a bare `Random`. */
    @Provides
    @Singleton
    @SuggestionRandom
    fun random(): Random = Random.Default
}
