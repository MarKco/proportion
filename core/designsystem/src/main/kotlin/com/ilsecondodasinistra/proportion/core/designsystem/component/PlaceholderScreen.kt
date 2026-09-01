package com.ilsecondodasinistra.proportion.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Temporary body for a destination whose screen has not been built yet. Every real screen replaces
 * it; the test tag is what the navigation test asserts on.
 */
@Composable
fun PlaceholderScreen(title: String, testTag: String) {
    Box(
        modifier = Modifier.fillMaxSize().testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
    }
}
