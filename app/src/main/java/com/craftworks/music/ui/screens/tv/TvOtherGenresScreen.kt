package com.craftworks.music.ui.screens.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.craftworks.music.R
import com.craftworks.music.data.model.Screen
import com.craftworks.music.ui.elements.tv.TvGenreCard
import com.craftworks.music.ui.elements.tv.rememberTvFocusRestoreState
import com.craftworks.music.ui.viewmodels.GenresScreenViewModel

/**
 * The genres the "Other" card stands for, as their own grid - the same cards as the genre tab,
 * so one of them can be opened from here as usual. Back returns to the genre tab.
 */
@Composable
fun TvOtherGenresScreen(
    navHostController: NavHostController,
    viewModel: GenresScreenViewModel = hiltViewModel()
) {
    val otherGenres by viewModel.otherGenres.collectAsStateWithLifecycle()
    val artwork by viewModel.genreArtwork.collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }
    val focusRestore = rememberTvFocusRestoreState()

    LaunchedEffect(Unit) {
        focusRestore.restore(focusRequester)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier
            .fillMaxSize()
            .focusGroup()
            .focusRequester(focusRequester),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(span = { GridItemSpan(4) }) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.genre_other),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = stringResource(R.string.genre_group_count, otherGenres.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(otherGenres, key = { it.name }) { genre ->
            TvGenreCard(
                genre = genre,
                artwork = artwork[genre.name].orEmpty(),
                modifier = focusRestore.focusModifier(genre.name),
                onArtworkRequest = { viewModel.loadGenreArtwork(it) },
                onClick = {
                    navHostController.navigate(Screen.GenreDetails(genre.name)) {
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
