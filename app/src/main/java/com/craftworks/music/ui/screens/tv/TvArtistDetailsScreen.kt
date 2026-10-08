package com.craftworks.music.ui.screens.tv

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.craftworks.music.R
import com.craftworks.music.data.model.ProviderFeature
import com.craftworks.music.data.model.Screen
import com.craftworks.music.data.model.displayYear
import com.craftworks.music.data.model.getProvider
import com.craftworks.music.data.model.id
import com.craftworks.music.data.model.newestFirst
import com.craftworks.music.player.SongHelper
import com.craftworks.music.ui.elements.dialogs.tv.ArtistBiographyDialog
import com.craftworks.music.ui.elements.tv.TvAlbumCard
import com.craftworks.music.ui.elements.tv.TvArtistCard
import com.craftworks.music.ui.elements.tv.rememberTvFocusRestoreState
import com.craftworks.music.ui.viewmodels.ArtistsScreenViewModel
import com.craftworks.music.utils.bleedHorizontal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** How much of the biography shows on this screen before the More button takes over. */
private const val BiographyPreviewMaxLines = 4

/** Key the album cards are tracked under by the focus restore state. */
private fun albumFocusKey(album: MediaItem) = "album|" + (album.mediaMetadata.id ?: album.mediaId)

@Composable
@Preview
fun TvArtistDetailsScreen(
    selectedArtistId: String? = null,
    selectedArtistImage: String? = null,
    navHostController: NavHostController = rememberNavController(),
    mediaController: MediaController? = null,
    viewModel: ArtistsScreenViewModel = hiltViewModel()
) {
    LaunchedEffect(selectedArtistId) {
        if (selectedArtistId != null) viewModel.loadArtistDetails(selectedArtistId)
    }

    val showLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val artist = viewModel.selectedArtist.collectAsStateWithLifecycle().value
    val artistAlbums = viewModel.artistAlbums.collectAsStateWithLifecycle().value
    val artistAppearanceAlbums =
        viewModel.artistAppearanceAlbums.collectAsStateWithLifecycle().value

    // Newest first, since the provider's own order is not guaranteed. Albums without a year
    // sort last rather than under a "null" heading, which is what the old year grouping did.
    val albumsByYearDesc = remember(artistAlbums) { artistAlbums.newestFirst() }
    val appearancesByYearDesc = remember(artistAppearanceAlbums) {
        artistAppearanceAlbums.newestFirst()
    }
    val similarArtists = artist?.similarArtists.orEmpty()
    val showSimilarArtists = similarArtists.isNotEmpty() &&
            artist?.getProvider()?.featureFlags?.contains(ProviderFeature.SIMILAR_SONGS) == true

    val focusRestore = rememberTvFocusRestoreState()
    var showBiographyDialog by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = showLoading || artist == null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                strokeWidth = 6.dp
            )
        }
    }

    AnimatedVisibility(
        visible = !showLoading,
        enter = fadeIn()
    ) {
        val coroutineScope = rememberCoroutineScope()
        val playRequester = remember { FocusRequester() }
        val context = LocalContext.current
        var radioLoading by remember { mutableStateOf(false) }

        // The card D-pad Down from the action buttons belongs on: the first card of the first
        // non-empty section below the header. Down restores a remembered album first, so this is
        // only where a fresh visit lands.
        val firstCardKey = when {
            albumsByYearDesc.isNotEmpty() -> albumFocusKey(albumsByYearDesc.first())
            appearancesByYearDesc.isNotEmpty() -> albumFocusKey(appearancesByYearDesc.first())
            showSimilarArtists -> "similar|" + similarArtists.first().id
            else -> null
        }
        val firstCardRequester = remember { FocusRequester() }

        /* The requester Down lands on, alongside the card's focus restore modifier. */
        fun Modifier.focusFirstCardIf(key: String): Modifier =
            if (key == firstCardKey) this.focusRequester(firstCardRequester) else this

        LaunchedEffect(Unit) {
            focusRestore.restore(playRequester)
        }

        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
            columns = GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(5) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusGroup()
                        .onKeyEvent { keyEvent ->
                            // Down belongs on the first album, or the album that had focus last,
                            // rather than on whichever card the focus search finds under the
                            // button being left behind.
                            if (keyEvent.type == KeyEventType.KeyDown &&
                                keyEvent.key == Key.DirectionDown &&
                                firstCardKey != null
                            ) {
                                coroutineScope.launch {
                                    focusRestore.restore(firstCardRequester, keyPrefix = "album|")
                                }
                                true
                            } else false
                        },
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(selectedArtistImage ?: "android.resource://com.craftworks.music/${R.drawable.placeholder}")
                            .diskCacheKey(selectedArtistId)
                            .crossfade(true)
                            .build(),
                        fallback = painterResource(R.drawable.rounded_artist_24),
                        contentScale = ContentScale.Crop,
                        contentDescription = null,
                        modifier = Modifier
                            .size(240.dp)
                            .clip(CircleShape)
                    )

                    // Artist Name
                    Column(
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = artist?.name ?: "",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        // Biography, capped so a long one cannot push the albums off the screen.
                        // The rest of it is behind the More button, which only appears when
                        // there is something left to read.
                        val biographyPreview = artist?.biography?.split("<a target")?.first().orEmpty()
                        var biographyOverflows by remember(biographyPreview) {
                            mutableStateOf(false)
                        }

                        Text(
                            text = biographyPreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = BiographyPreviewMaxLines,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { biographyOverflows = it.hasVisualOverflow },
                        )

                        if (biographyOverflows) {
                            OutlinedButton(
                                onClick = { showBiographyDialog = true },
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                            ) {
                                Text(stringResource(R.string.action_more))
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val allArtistSongsList = artistAlbums.map {
                                            it.mediaMetadata.id.let { id ->
                                                val album = viewModel.getAlbum(id ?: "")
                                                if (album.isNotEmpty())
                                                    album.subList(1, album.size)
                                                else
                                                    emptyList()
                                            }
                                        }

                                        SongHelper.play(
                                            allArtistSongsList.flatten(),
                                            0,
                                            mediaController
                                        )
                                        navHostController.navigate(Screen.NowPlayingLandscape) {
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(playRequester),
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(stringResource(R.string.action_play))
                            }

                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val allArtistSongsList = artistAlbums.flatMap {
                                            it.mediaMetadata.id.let { id ->
                                                val album = viewModel.getAlbum(id ?: "")
                                                if (album.isNotEmpty())
                                                    album.subList(1, album.size)
                                                else
                                                    emptyList()
                                            }
                                        }

                                        SongHelper.shuffle(allArtistSongsList, mediaController)
                                        navHostController.navigate(Screen.NowPlayingLandscape) {
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f),
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                            ) {
                                Icon(
                                    ImageVector.vectorResource(R.drawable.round_shuffle_28),
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(stringResource(R.string.action_shuffle))
                            }

                            val radioArtist = artist
                            if (radioArtist != null &&
                                radioArtist.getProvider()?.featureFlags?.contains(ProviderFeature.SIMILAR_SONGS) == true
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (!radioLoading) {
                                            coroutineScope.launch {
                                                radioLoading = true
                                                try {
                                                    val songs = viewModel.getArtistRadioSongs(radioArtist.id)
                                                    if (songs.isEmpty()) {
                                                        Toast.makeText(context, R.string.radio_empty, Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        SongHelper.play(songs, 0, mediaController)
                                                        navHostController.navigate(Screen.NowPlayingLandscape) {
                                                            launchSingleTop = true
                                                        }
                                                    }
                                                } catch (e: CancellationException) {
                                                    throw e
                                                } catch (e: Exception) {
                                                    // A failed fetch must not end the browse session.
                                                    Toast.makeText(context, R.string.radio_empty, Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    radioLoading = false
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                                ) {
                                    Icon(
                                        ImageVector.vectorResource(R.drawable.rounded_radio),
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize),
                                    )
                                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                    Text(stringResource(R.string.action_radio))
                                }
                            }
                        }
                    }
                }
            }

            /* Discography. The year sits on each card where the artist name would be, so the
               albums run together in one grid instead of a row per year. */
            if (albumsByYearDesc.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.artist_details_discography),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                    )
                }
                items(albumsByYearDesc) { album ->
                    val key = albumFocusKey(album)
                    TvAlbumCard(
                        album = album,
                        modifier = focusRestore.focusModifier(key).focusFirstCardIf(key),
                        onClick = {
                            navHostController.navigate(Screen.AlbumDetails(album.mediaMetadata.id?:"", album.mediaMetadata.artworkUri.toString())) {
                                launchSingleTop = true
                            }
                        },
                        subtitle = album.mediaMetadata.displayYear
                    )
                }
            }

            /* Albums the artist only guests on. These keep the album artist name under the
               title, since that is the part worth knowing here. */
            if (appearancesByYearDesc.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.artist_details_appears_on),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                    )
                }
                items(appearancesByYearDesc) { album ->
                    val key = albumFocusKey(album)
                    TvAlbumCard(
                        album = album,
                        modifier = focusRestore.focusModifier(key).focusFirstCardIf(key),
                        onClick = {
                            navHostController.navigate(Screen.AlbumDetails(album.mediaMetadata.id?:"", album.mediaMetadata.artworkUri.toString())) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }

            /* Artists the provider considers similar. Last, so the discography keeps the top of
               the page, and a row rather than grid items so a long list stays out of the way.
               Fetched with the biography. */
            if (showSimilarArtists) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.focusGroup()) {
                        Text(
                            text = stringResource(R.string.artist_details_similar_artists),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        LazyRow(
                            // A lazy row clips on its own axis, and the grid item is only as wide
                            // as the padded content area, so without the bleed the focused first
                            // and last cards lose an edge to that clip. The bleed widens the row
                            // by the screen gutter each side; the content padding keeps the cards
                            // aligned with the album columns.
                            modifier = Modifier.focusGroup().bleedHorizontal(48.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp)
                        ) {
                            items(similarArtists, key = { it.id }) { similar ->
                                val key = "similar|" + similar.id
                                TvArtistCard(
                                    artist = similar,
                                    modifier = focusRestore.focusModifier(key).focusFirstCardIf(key),
                                    onClick = {
                                        navHostController.navigate(
                                            Screen.ArtistDetails(
                                                similar.id,
                                                similar.imageUrl ?: similar.imageId?.let { imageId ->
                                                    similar.getProvider()?.getImageUrl(imageId)
                                                } ?: ""
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBiographyDialog) {
        ArtistBiographyDialog(
            artistName = artist?.name.orEmpty(),
            biography = artist?.biography.orEmpty(),
            setShowDialog = { showBiographyDialog = it }
        )
    }
}