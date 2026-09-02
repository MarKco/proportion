package com.ilsecondodasinistra.proportion.core.ui

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.ilsecondodasinistra.proportion.core.domain.LocaleController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Backs the app's own language with two mechanisms, because `AppCompatDelegate.setApplicationLocales`
 * alone does not take effect in the *running* process here: applying it live relies on hooks tied to
 * `AppCompatActivity`, and this app's Compose-only theme deliberately isn't one (making it one just
 * for this would need a `Theme.AppCompat` parent for no other benefit — confirmed by a real crash
 * during development: "You need to use a Theme.AppCompat theme (or descendant) with this activity").
 *
 * - On API 33+, [android.app.LocaleManager] is called directly — the same mechanism the device's own
 *   Settings > Apps > <App> > Language screen uses, proven on-device to update the running app
 *   immediately after the activity recreates.
 * - [AppCompatDelegate] is called too, on every API level, purely so the choice is persisted (via its
 *   own `AppLocalesMetadataHolderService` storage) and reapplied automatically the next time the
 *   process cold-starts. Below API 33 that is the only thing this class can offer: the language
 *   changes from the next app open, not immediately, since there is no platform API to call instead.
 */
class AppCompatLocaleController @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocaleController {

    override fun currentTag(): String? =
        AppCompatDelegate.getApplicationLocales().takeIf { !it.isEmpty }?.toLanguageTags()

    override fun setTag(tag: String?) {
        AppCompatDelegate.setApplicationLocales(
            if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LocaleControllerModule {

    /** The domain asks for the app's language through [LocaleController]; only this layer knows AppCompat. */
    @Binds
    abstract fun localeController(impl: AppCompatLocaleController): LocaleController
}
