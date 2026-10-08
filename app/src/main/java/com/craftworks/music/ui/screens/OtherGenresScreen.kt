package com.craftworks.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.craftworks.music.R
import com.craftworks.music.data.model.Screen
import com.craftworks.music.ui.viewmodels.GenresScreenViewModel

/**
 * The genres the "Other" card stands for: the same grid as the genre tab, without a card of
 * their own until now. Picking one opens it as usual.
 */
@Composable
fun OtherGenresScreen(
    navHostController: NavHostController,
    viewModel: GenresScreenViewModel = hiltViewModel()
) {
    val otherGenres by viewModel.otherGenres.collectAsStateWithLifecycle()
    val artwork by viewModel.genreArtwork.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding(),
                    bottom = 6.dp
                ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = { navHostController.popBackStack() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.action_close)
                    )
                }
            }

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

        GenreGrid(
            genres = otherGenres,
            artwork = artwork,
            onGenreSelected = { genre ->
                navHostController.navigate(Screen.GenreDetails(genre.name)) {
                    launchSingleTop = true
                }
            },
            onGenreVisible = { viewModel.loadGenreArtwork(it) }
        )
    }
}
