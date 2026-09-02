package com.ilsecondodasinistra.proportion.feature.home.di

import javax.inject.Qualifier

/**
 * Qualifies the [kotlin.random.Random] behind the Home "what shall I cook?" suggestion.
 *
 * `Random` is a generic stdlib type, not an app-specific one like `TimeProvider`: left unqualified
 * at [dagger.hilt.components.SingletonComponent], any other module that also wants to provide a
 * `Random` would collide with this one, and any unrelated `@Inject random: Random` elsewhere in the
 * app would silently receive this dashboard-shuffle instance with no signal that it was meant only
 * for the suggestion card.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SuggestionRandom
