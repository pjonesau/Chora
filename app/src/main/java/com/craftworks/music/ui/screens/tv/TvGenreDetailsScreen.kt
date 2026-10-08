package com.craftworks.music.ui.screens.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import androidx.navigation.NavHostController
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.craftworks.music.R
import com.craftworks.music.data.model.Screen
import com.craftworks.music.data.model.id
import com.craftworks.music.player.SongHelper
import com.craftworks.music.ui.elements.genreDisplayName
import com.craftworks.music.ui.elements.tv.TvAlbumCard
import com.craftworks.music.ui.elements.tv.rememberTvFocusRestoreState
import com.craftworks.music.ui.viewmodels.GenresScreenViewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private const val LoadMoreThreshold = 15
private const val AlbumPageSize = 50

@Composable
fun TvGenreDetailsScreen(
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
    val focusRestore = rememberTvFocusRestoreState()
    val headerRequester = remember { FocusRequester() }

    var isStartingPlayback by remember { mutableStateOf(false) }

    LaunchedEffect(genreName) { viewModel.loadGenreDetails(genreName) }

    LaunchedEffect(Unit) {
        focusRestore.restore(headerRequester)
    }

    LaunchedEffect(albums.size) {
        if (albums.size < AlbumPageSize || albums.size % AlbumPageSize != 0) return@LaunchedEffect

        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?: return@snapshotFlow false
            val total = gridState.layoutInfo.totalItemsCount
            (total - lastVisible) <= LoadMoreThreshold
        }
            .filter { it }
            .collect { viewModel.getMoreGenreAlbums(AlbumPageSize) }
    }

    // Both buttons hand over to Now Playing once the songs are queued, as the TV album and
    // playlist screens do. The action reports whether anything actually started, so a genre
    // whose songs do not come back (an empty music folder, say) leaves you on this screen
    // instead of on one with nothing playing.
    val startPlayback: (suspend () -> Boolean) -> Unit = { action ->
        coroutineScope.launch {
            isStartingPlayback = true
            try {
                if (action()) {
                    navHostController.navigate(Screen.NowPlayingLandscape) {
                        launchSingleTop = true
                    }
                }
            } finally {
                isStartingPlayback = false
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(span = { GridItemSpan(5) }) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = genre?.let { genreDisplayName(it.name) } ?: genreName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
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
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        enabled = !isStartingPlayback,
                        modifier = Modifier.focusRequester(headerRequester),
                        onClick = {
                            startPlayback {
                                val songs = viewModel.getGenreSongs()
                                SongHelper.play(songs, 0, mediaController)
                                songs.isNotEmpty()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.action_play_all))
                    }
                    OutlinedButton(
                        enabled = !isStartingPlayback,
                        onClick = {
                            startPlayback {
                                val songs = viewModel.getGenreSongs()
                                SongHelper.shuffle(songs, mediaController)
                                songs.isNotEmpty()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.action_shuffle_all))
                    }
                }
            }
        }

        items(albums, key = { it.mediaId }) { album ->
            TvAlbumCard(
                album = album,
                modifier = focusRestore.focusModifier("album|${album.mediaMetadata.id ?: album.mediaId}"),
                onClick = {
                    navHostController.navigate(
                        Screen.AlbumDetails(
                            albumId = album.mediaMetadata.id ?: "",
                            imageUri = album.mediaMetadata.artworkUri.toString()
                        )
                    ) { launchSingleTop = true }
                }
            )
        }

        if (isLoadingAlbums && albums.isEmpty()) {
            item(span = { GridItemSpan(5) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 6.dp
                    )
                }
            }
        } else if (!isLoadingAlbums && albums.isEmpty()) {
            item(span = { GridItemSpan(5) }) {
                Text(
                    text = stringResource(R.string.genre_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        }

        item(span = { GridItemSpan(5) }) { Spacer(Modifier.height(12.dp)) }
    }
}
