package com.ilsecondodasinistra.proportion.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilsecondodasinistra.proportion.core.designsystem.R as DesignSystemR
import com.ilsecondodasinistra.proportion.core.ui.component.EmptyState
import com.ilsecondodasinistra.proportion.core.ui.component.LoadingState

@Composable
fun HomeRoute(
    onRecipeClick: (String) -> Unit,
    onCook: (String) -> Unit,
    onAddRecipe: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreen(
        state = state,
        onRecipeClick = onRecipeClick,
        onCook = onCook,
        onAddRecipe = onAddRecipe,
        onReshuffle = viewModel::onReshuffle,
        onSuggestionTagChange = viewModel::onSuggestionTagChange,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRecipeClick: (String) -> Unit = {},
    onCook: (String) -> Unit = {},
    onAddRecipe: () -> Unit = {},
    onReshuffle: () -> Unit = {},
    onSuggestionTagChange: (String?) -> Unit = {},
) {
    Scaffold(
        modifier = Modifier.testTag("home_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(DesignSystemR.drawable.ic_app_logo),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.home_title))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(modifier = Modifier.padding(padding))

            state.isEmpty -> HomeEmptyState(
                onAddRecipe = onAddRecipe,
                modifier = Modifier.padding(padding),
            )

            else -> HomeContent(
                state = state,
                onRecipeClick = onRecipeClick,
                onCook = onCook,
                onReshuffle = onReshuffle,
                onSuggestionTagChange = onSuggestionTagChange,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onRecipeClick: (String) -> Unit,
    onCook: (String) -> Unit,
    onReshuffle: () -> Unit,
    onSuggestionTagChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        NumbersCard(
            recipeCount = state.recipeCount,
            totalCooks = state.totalCooks,
            favouriteCount = state.favouriteCount,
            donutSlices = state.donutSlices,
            uncategorisedCount = state.uncategorisedCount,
        )

        state.continueCooking?.let { continueCooking ->
            ContinueCookingCard(continueCooking = continueCooking, onCook = onCook)
        }

        if (state.mostCooked.isNotEmpty() || state.favourites.isNotEmpty()) {
            MostCookedCard(
                mostCooked = state.mostCooked,
                favourites = state.favourites,
                onRecipeClick = onRecipeClick,
            )
        }

        SuggestionCard(
            suggestion = state.suggestion,
            suggestionUnavailable = state.suggestionUnavailable,
            tags = state.suggestionTags,
            selectedTagId = state.suggestionTagId,
            onTagChange = onSuggestionTagChange,
            onReshuffle = onReshuffle,
            onRecipeClick = onRecipeClick,
        )
    }
}

/**
 * [EmptyState]'s own action button carries no test tag of its own, so the call to action is
 * rendered separately here with the exact tag the screen test expects, rather than through
 * [EmptyState]'s `actionLabel`/`onAction` pair.
 */
@Composable
private fun HomeEmptyState(onAddRecipe: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        EmptyState(
            title = stringResource(R.string.home_empty_title),
            message = stringResource(R.string.home_empty_body),
            modifier = Modifier.weight(1f),
            testTag = "home_empty_state",
        )
        Button(
            onClick = onAddRecipe,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp)
                .testTag("home_empty_action"),
        ) {
            Text(stringResource(R.string.home_empty_action))
        }
    }
}
