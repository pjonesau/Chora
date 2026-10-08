package com.craftworks.music.ui.elements

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.craftworks.music.R
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.repository.GenreRepository

/** Tiles per row and rows in a genre collage. 3 across and 2 down on a 3:2 card gives square tiles. */
private const val CollageColumns = 3
private const val CollageRows = 2

/** The genre's name, with Navidrome's placeholder for untagged tracks shown as something human. */
@Composable
fun genreDisplayName(name: String): String = when (name) {
    MediaModel.Genre.EMPTY_NAME -> stringResource(R.string.genre_unknown)
    MediaModel.Genre.OTHER_NAME -> stringResource(R.string.genre_other)
    else -> name
}

/**
 * The line under a genre's name. The group card counts the genres behind it instead: their own
 * song and album counts overlap, since an album tagged with several genres is counted by each
 * of them, so summing them would overstate what is in the group.
 */
@Composable
fun genreSubtitle(genre: MediaModel.Genre, groupSize: Int?): String =
    if (genre.name == MediaModel.Genre.OTHER_NAME && groupSize != null)
        stringResource(R.string.genre_group_count, groupSize)
    else
        stringResource(
            R.string.genre_song_album_count,
            genre.songCount ?: 0,
            genre.albumCount ?: 0
        )

/**
 * A genre's artwork as a grid of album covers.
 *
 * The card this fills is 3:2, so each cell is (w/3) x (h/2) - exactly square. Album art is
 * square as well, so Crop has nothing to cut off and nothing is stretched.
 *
 * Fewer than six albums repeat until the grid is full, which keeps every card the same shape
 * instead of leaving holes. A genre with no albums at all falls back to a placeholder.
 */
@Composable
fun GenreCollage(
    artwork: List<GenreRepository.GenreArtwork>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (artwork.isEmpty()) {
            Icon(
                painter = painterResource(R.drawable.round_music_note_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.Center)
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                repeat(CollageRows) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        repeat(CollageColumns) { column ->
                            val tile = artwork[(row * CollageColumns + column) % artwork.size]
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(tile.url)
                                    .diskCacheKey(tile.albumId)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GenreCard(
    genre: MediaModel.Genre,
    artwork: List<GenreRepository.GenreArtwork>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onArtworkRequest: (MediaModel.Genre) -> Unit = {},
    groupSize: Int? = null
) {
    // Only composed while the card is on screen, which is what makes the artwork fetch lazy -
    // a genre that never scrolls into view never costs a request.
    LaunchedEffect(genre.name) { onArtworkRequest(genre) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        GenreCollage(
            artwork = artwork,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 2f)
        )

        Text(
            text = genreDisplayName(genre.name),
            style = MaterialTheme.typography.titleSmall,
            // Genre names get long; two lines before it starts truncating.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = genreSubtitle(genre, groupSize),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
