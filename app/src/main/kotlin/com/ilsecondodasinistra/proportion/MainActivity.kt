package com.ilsecondodasinistra.proportion

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilsecondodasinistra.proportion.core.designsystem.theme.ProPortionTheme
import com.ilsecondodasinistra.proportion.core.model.ThemeMode
import com.ilsecondodasinistra.proportion.navigation.ProPortionApp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.viewModels
import com.ilsecondodasinistra.proportion.core.data.PendingImport
import com.ilsecondodasinistra.proportion.feature.home.HomeRoute
import com.ilsecondodasinistra.proportion.feature.settings.SettingsRouteKey
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var pendingImport: PendingImport

    /**
     * On API 33+, `LocaleManager` (called from `AppCompatLocaleController` in `:core:ui`) already
     * updates every process's resources, this activity's included, the moment it is set — this
     * override would just be redundant there. Below 33 there is no such platform hook, so a chosen
     * language would otherwise only take effect on the next cold start; wrapping the base context
     * here with AppCompat's already-persisted choice is what makes [recreate] (called right after
     * the choice changes) show it immediately instead.
     */
    override fun attachBaseContext(newBase: Context) {
        val wrapped = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) newBase else newBase.withAppLocale()
        super.attachBaseContext(wrapped)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Opening a .proportion attachment lands here: hand the text to settings, which previews it.
        val openedUri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data
        val openedFile = openedUri?.let(::readText)
        when {
            openedFile != null -> pendingImport.offer(openedFile)
            openedUri != null -> pendingImport.offerUnreadable()
        }

        setContent {
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            val darkTheme = when (preferences.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            ProPortionTheme(
                darkTheme = darkTheme,
                dynamicColour = preferences.useDynamicColour,
                appTheme = preferences.appTheme,
            ) {
                ProPortionApp(startDestination = if (openedUri != null) SettingsRouteKey else HomeRoute)
            }
        }
    }

    private fun readText(uri: Uri): String? = runCatching {
        contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
    }.getOrNull()
}

/** Below API 33 only: wraps the context in AppCompat's already-persisted app language, if any. */
private fun Context.withAppLocale(): Context {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) return this
    val platformLocales = locales.unwrap() as LocaleList
    val configuration = Configuration(resources.configuration).apply { setLocales(platformLocales) }
    return createConfigurationContext(configuration)
}
