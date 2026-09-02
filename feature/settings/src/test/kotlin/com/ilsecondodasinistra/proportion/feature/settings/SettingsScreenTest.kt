package com.ilsecondodasinistra.proportion.feature.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.designsystem.theme.ProPortionTheme
import com.ilsecondodasinistra.proportion.core.model.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * On-device TalkBack testing found the theme radio rows and the dynamic-colour switch announced
 * with no label: the RadioButton/Switch were separate, unlabelled semantics nodes from their
 * adjacent text. These tests guard the fix (the row itself carries the selectable/toggleable
 * semantics, merging the visible label into what gets spoken) without depending on TalkBack.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var themeChanged: ThemeMode? = null
    private var dynamicColourChanged: Boolean? = null
    private var languageChanged: AppLanguage? = null

    private fun render(state: SettingsUiState) {
        composeTestRule.setContent {
            ProPortionTheme(dynamicColour = false) {
                SettingsScreen(
                    state = state,
                    snackbarHostState = remember { SnackbarHostState() },
                    onThemeChange = { themeChanged = it },
                    onDynamicColourChange = { dynamicColourChanged = it },
                    onLanguageChange = { languageChanged = it },
                    onBackupClick = {},
                    onRestoreClick = {},
                    onMerge = {},
                    onReplaceRequested = {},
                    onReplaceConfirmed = {},
                    onDismissRestore = {},
                )
            }
        }
    }

    @Test
    fun `the selected theme row reports itself as selected`() {
        render(SettingsUiState(themeMode = ThemeMode.DARK))

        composeTestRule.onNodeWithTag("theme_DARK").assertIsSelected()
        composeTestRule.onNodeWithTag("theme_LIGHT").assertIsNotSelected()
    }

    @Test
    fun `tapping a theme row reports the tapped theme`() {
        render(SettingsUiState(themeMode = ThemeMode.SYSTEM))

        composeTestRule.onNodeWithTag("theme_LIGHT").performClick()

        assertThat(themeChanged).isEqualTo(ThemeMode.LIGHT)
    }

    @Test
    fun `the dynamic colour row reports its checked state and can be toggled`() {
        render(SettingsUiState(useDynamicColour = true))

        composeTestRule.onNodeWithTag("dynamic_colour_switch").assertIsOn()

        composeTestRule.onNodeWithTag("dynamic_colour_switch").performClick()

        assertThat(dynamicColourChanged).isFalse()
    }

    @Test
    fun `the selected language row reports itself as selected`() {
        render(SettingsUiState(language = AppLanguage.ITALIAN))

        composeTestRule.onNodeWithTag("language_ITALIAN").assertIsSelected()
        composeTestRule.onNodeWithTag("language_ENGLISH").assertIsNotSelected()
    }

    @Test
    fun `tapping a language row reports the tapped language`() {
        render(SettingsUiState(language = AppLanguage.SYSTEM))

        composeTestRule.onNodeWithTag("language_ENGLISH").performScrollTo().performClick()

        assertThat(languageChanged).isEqualTo(AppLanguage.ENGLISH)
    }
}
