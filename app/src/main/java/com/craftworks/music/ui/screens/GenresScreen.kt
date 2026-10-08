package com.craftworks.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.craftworks.music.R
import com.craftworks.music.data.model.GenreListSort
import com.craftworks.music.data.model.Screen
import com.craftworks.music.data.model.SortOrder
import com.craftworks.music.managers.MediaProviderManager
import com.craftworks.music.ui.elements.GenreCard
import com.craftworks.music.ui.elements.RippleEffect
import com.craftworks.music.ui.elements.TopBarWithSearch
import com.craftworks.music.ui.viewmodels.GenresScreenViewModel
import com.craftworks.music.ui.playing.dpToPx

@Composable
fun GenresScreen(
    navHostController: NavHostController,
    viewModel: GenresScreenViewModel = hiltViewModel()
) {
    val allGenresList by viewModel.allGenres.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val artwork by viewModel.genreArtwork.collectAsStateWithLifecycle()

    val currentProvider by MediaProviderManager.currentProvider.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()

    val state = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    val isRefreshing by viewModel.isLoading.collectAsStateWithLifecycle()

    var showRipple by remember { mutableIntStateOf(0) }
    val rippleXOffset = LocalWindowInfo.current.containerSize.width / 2
    val rippleYOffset = dpToPx(12)

    var showSortMenu by remember { mutableStateOf(false) }

    val scrollBehavior = androidx.compose.material3.TopAppBarDefaults.enterAlwaysScrollBehavior()

    val sortTranslationBindings = mapOf(
        GenreListSort.ALBUM_COUNT to R.string.sort_by_album_count,
        GenreListSort.NAME to R.string.sort_by_name,
        GenreListSort.SONG_COUNT to R.string.sort_by_song_count,
    )

    val openGenre: (String) -> Unit = { genreName ->
        navHostController.navigate(Screen.GenreDetails(genreName)) { launchSingleTop = true }
    }

    PullToRefreshBox(
        state = state,
        isRefreshing = isRefreshing,
        onRefresh = {
            viewModel.refresh()
            showRipple++
        }
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopBarWithSearch(
                    headerText = stringResource(R.string.nav_genres),
                    scrollBehavior = scrollBehavior,
                    onSearch = { query -> viewModel.search(query) },
                    searchResults = {
                        GenreGrid(
                            genres = searchResults,
                            artwork = artwork,
                            onGenreSelected = { openGenre(it.name) },
                            onGenreVisible = { viewModel.loadGenreArtwork(it) }
                        )
                    },
                    extraAction = {
                        Row {
                            if (currentProvider?.supportGenreSortOrder ?: false) {
                                Box {
                                    IconButton(onClick = { viewModel.setOrder(sortOrder.invert()) }) {
                                        Icon(
                                            imageVector = ImageVector.vectorResource(
                                                if (sortOrder == SortOrder.ASC) R.drawable.arrow_upward_24px
                                                else R.drawable.arrow_downward_24px
                                            ),
                                            contentDescription = stringResource(R.string.button_toggle_sort_order),
                                        )
                                    }
                                }
                            }
                            if (currentProvider?.supportedGenreSort.orEmpty().size > 1) {
                                Box {
                                    IconButton(onClick = { showSortMenu = true }) {
                                        Icon(
                                            imageVector = ImageVector.vectorResource(R.drawable.rounded_sort_24),
                                            contentDescription = stringResource(R.string.button_sort_by),
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false }
                                    ) {
                                        currentProvider?.supportedGenreSort.orEmpty().map {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        sortTranslationBindings[it]?.let { id -> stringResource(id) }
                                                            ?: it.name
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.setSorting(it)
                                                    showSortMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                GenreGrid(
                    genres = allGenresList,
                    artwork = artwork,
                    onGenreSelected = { openGenre(it.name) },
                    onGenreVisible = { viewModel.loadGenreArtwork(it) }
                )
            }
        }
    }

    RippleEffect(
        center = Offset(rippleXOffset.toFloat(), rippleYOffset.toFloat()),
        color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
        key = showRipple
    )
}

/** The phone grid of genre cards. Cards are 3:2 plus two lines of text, so wider than the art grids. */
@Composable
fun GenreGrid(
    genres: List<com.craftworks.music.data.model.MediaModel.Genre>,
    artwork: Map<String, List<com.craftworks.music.data.repository.GenreRepository.GenreArtwork>>,
    onGenreSelected: (com.craftworks.music.data.model.MediaModel.Genre) -> Unit,
    onGenreVisible: (com.craftworks.music.data.model.MediaModel.Genre) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(12.dp)
    ) {
        items(genres, key = { it.name }) { genre ->
            GenreCard(
                genre = genre,
                artwork = artwork[genre.name].orEmpty(),
                onClick = { onGenreSelected(genre) },
                onArtworkRequest = onGenreVisible
            )
        }
    }
}
