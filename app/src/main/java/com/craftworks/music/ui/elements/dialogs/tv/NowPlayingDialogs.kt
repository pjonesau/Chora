package com.craftworks.music.ui.elements.dialogs.tv

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.craftworks.music.R
import kotlinx.coroutines.delay

/**
 * The two places the playing track can lead to, for the go-to button on the player. Back dismisses
 * it, so it needs no third button; an option with nothing behind it (a track with no album) is
 * disabled rather than missing, which the static dialog can afford to do.
 *
 * Focus starts on the leftmost option that can act (the artist, then the album). The go-to button
 * is a plain click, which fires on key-up, so no key is still held when the dialog appears.
 */
@Composable
fun GoToDialog(
    albumAvailable: Boolean,
    artistAvailable: Boolean,
    onDismiss: () -> Unit,
    onGoToAlbum: () -> Unit,
    onGoToArtist: () -> Unit
) {
    val artistFocus = remember { FocusRequester() }
    val albumFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        val initialFocus = when {
            artistAvailable -> artistFocus
            albumAvailable -> albumFocus
            else -> return@LaunchedEffect
        }
        // The dialog's window does not take focus on the first frame, so keep asking for a moment.
        repeat(10) {
            if (initialFocus.requestFocus()) return@LaunchedEffect
            delay(50)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(R.string.now_playing_go_to),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge
            )
        },
        confirmButton = {
            Button(
                onClick = onGoToAlbum,
                enabled = albumAvailable,
                modifier = Modifier.focusRequester(albumFocus)
            ) {
                Text(stringResource(R.string.now_playing_album))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onGoToArtist,
                enabled = artistAvailable,
                modifier = Modifier.focusRequester(artistFocus)
            ) {
                Text(stringResource(R.string.now_playing_artist))
            }
        }
    )
}
