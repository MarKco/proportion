package com.ilsecondodasinistra.proportion

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
            ) {
                ProPortionApp(startDestination = if (openedUri != null) SettingsRouteKey else HomeRoute)
            }
        }
    }

    private fun readText(uri: Uri): String? = runCatching {
        contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
    }.getOrNull()
}
