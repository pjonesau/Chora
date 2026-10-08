package com.craftworks.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import androidx.navigation.NavHostController
import com.craftworks.music.R
import com.craftworks.music.data.model.Screen
import com.craftworks.music.data.model.id
import com.craftworks.music.player.SongHelper
import com.craftworks.music.ui.elements.AlbumCard
import com.craftworks.music.ui.elements.genreDisplayName
import com.craftworks.music.ui.viewmodels.GenresScreenViewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** How close to the end of the grid triggers the next page. */
private const val LoadMoreThreshold = 10
private const val AlbumPageSize = 50

@Composable
fun GenreDetailsScreen(
    genreName: String,
    navHostController: NavHostController,
    mediaController: MediaController?,
    viewModel: GenresScreenViewModel = hiltViewModel()
) {
    val genre by viewModel.selectedGenre.collectAsStateWithLifecycle()
    val albums by viewModel.genreAlbums.collectAsStateWithLifecycle()
    val isLoadingAlbums by viewModel.isLoadingAlbums.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    // Collecting the genre's songs can take several requests on a large genre, so the buttons
    // show progress rather than appearing to do nothing.
    var isStartingPlayback by remember { mutableStateOf(false) }

    LaunchedEffect(genreName) { viewModel.loadGenreDetails(genreName) }

    LaunchedEffect(albums.size) {
        // A short page means the genre is exhausted.
        if (albums.size < AlbumPageSize || albums.size % AlbumPageSize != 0) return@LaunchedEffect

        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = gridState.layoutInfo.totalItemsCount
            total > 0 && (total - lastVisible) <= LoadMoreThreshold
        }
            .filter { it }
            .collect { viewModel.getMoreGenreAlbums(AlbumPageSize) }
    }

    val startPlayback: (suspend () -> Unit) -> Unit = { action ->
        coroutineScope.launch {
            isStartingPlayback = true
            try {
                action()
            } finally {
                isStartingPlayback = false
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(96.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding(),
                        bottom = 12.dp
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
                    text = genre?.let { genreDisplayName(it.name) } ?: genreName,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = stringResource(
                        R.string.genre_song_album_count,
                        genre?.songCount ?: 0,
                        genre?.albumCount ?: 0
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        enabled = !isStartingPlayback,
                        onClick = {
                            startPlayback {
                                SongHelper.play(viewModel.getGenreSongs(), 0, mediaController)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.action_play_all))
                    }
                    OutlinedButton(
                        enabled = !isStartingPlayback,
                        onClick = {
                            startPlayback {
                                SongHelper.shuffle(viewModel.getGenreSongs(), mediaController)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.action_shuffle_all))
                    }
                }
            }
        }

        items(albums, key = { it.mediaId }) { album ->
            AlbumCard(
                album = album,
                onClick = {
                    navHostController.navigate(
                        Screen.AlbumDetails(
                            albumId = album.mediaMetadata.id ?: "",
                            imageUri = album.mediaMetadata.artworkUri.toString()
                        )
                    ) { launchSingleTop = true }
                },
                onPlay = {
                    coroutineScope.launch {
                        val mediaItems = viewModel.getAlbum(album.mediaMetadata.id ?: "")
                        if (mediaItems.isNotEmpty())
                            SongHelper.play(
                                mediaItems = mediaItems.subList(1, mediaItems.size),
                                index = 0,
                                mediaController = mediaController
                            )
                    }
                }
            )
        }

        if (isLoadingAlbums && albums.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (!isLoadingAlbums && albums.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.genre_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(12.dp)) }
    }
}
