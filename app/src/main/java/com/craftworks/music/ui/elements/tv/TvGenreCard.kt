package com.craftworks.music.ui.elements.tv

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.StandardCardContainer
import androidx.tv.material3.Text
import com.craftworks.music.R
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.repository.GenreRepository
import com.craftworks.music.ui.elements.GenreCollage
import com.craftworks.music.ui.elements.genreDisplayName

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvGenreCard(
    genre: MediaModel.Genre,
    artwork: List<GenreRepository.GenreArtwork>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onArtworkRequest: (MediaModel.Genre) -> Unit = {}
) {
    // Composed only while the card is on screen, so off-screen genres cost no request.
    LaunchedEffect(genre.name) { onArtworkRequest(genre) }

    StandardCardContainer(
        // Wider than the 128dp art cards: the collage is 3:2 and the name needs the room.
        modifier = modifier.width(200.dp),
        imageCard = {
            Card(
                onClick = onClick,
                interactionSource = it,
                content = {
                    GenreCollage(
                        artwork = artwork,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 2f)
                    )
                }
            )
        },
        title = {
            Text(
                text = genreDisplayName(genre.name),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
        },
        subtitle = {
            Text(
                text = stringResource(
                    R.string.genre_song_album_count,
                    genre.songCount ?: 0,
                    genre.albumCount ?: 0
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
    )
}
