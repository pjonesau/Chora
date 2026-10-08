package com.craftworks.music.ui.screens.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.Text
import com.craftworks.music.R
import com.craftworks.music.data.model.GenreListSort
import com.craftworks.music.data.model.Screen
import com.craftworks.music.data.model.SortOrder
import com.craftworks.music.managers.MediaProviderManager
import com.craftworks.music.ui.elements.dialogs.tv.GenericListDialog
import com.craftworks.music.ui.elements.tv.TvGenreCard
import com.craftworks.music.ui.elements.tv.rememberTvFocusRestoreState
import com.craftworks.music.ui.viewmodels.GenresScreenViewModel

@Composable
fun TvGenreScreen(
    navHostController: NavHostController,
    viewModel: GenresScreenViewModel = hiltViewModel()
) {
    val allGenresList by viewModel.allGenres.collectAsStateWithLifecycle()
    val artwork by viewModel.genreArtwork.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()

    val currentProvider by MediaProviderManager.currentProvider.collectAsStateWithLifecycle()

    var showSortDialog by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val focusRestore = rememberTvFocusRestoreState()

    LaunchedEffect(Unit) {
        focusRestore.restore(focusRequester)
    }

    val sortTranslationBindings = mapOf(
        GenreListSort.ALBUM_COUNT to R.string.sort_by_album_count,
        GenreListSort.NAME to R.string.sort_by_name,
        GenreListSort.SONG_COUNT to R.string.sort_by_song_count,
    )

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
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentProvider?.supportedGenreSort.orEmpty().size > 1) {
                    Button(onClick = { showSortDialog = true }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.rounded_sort_24),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )

                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))

                        Text(
                            text = sortTranslationBindings[sort]?.let { id -> stringResource(id) } ?: ""
                        )
                    }
                }

                if (currentProvider?.supportGenreSortOrder ?: false) {
                    IconButton(onClick = { viewModel.setOrder(sortOrder.invert()) }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(
                                if (sortOrder == SortOrder.ASC) R.drawable.arrow_upward_24px
                                else R.drawable.arrow_downward_24px
                            ),
                            contentDescription = stringResource(R.string.button_toggle_sort_order),
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                    }
                }
            }
        }

        items(allGenresList, key = { it.name }) { genre ->
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

    if (showSortDialog && currentProvider != null)
        GenericListDialog(
            titleRes = R.string.button_sort_by,
            label = { sortTranslationBindings[it]?.let { id -> stringResource(id) } ?: "" },
            setShowDialog = { showSortDialog = it },
            options = currentProvider!!.supportedGenreSort,
            selectedOption = sort,
            onOptionSelected = { viewModel.setSorting(it) },
            leftAligned = true,
            modifier = Modifier.padding(20.dp).width(280.dp).fillMaxHeight()
        )
}
