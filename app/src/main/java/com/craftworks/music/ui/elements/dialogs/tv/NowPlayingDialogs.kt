package com.craftworks.music.ui.elements.dialogs.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.craftworks.music.R

/**
 * The two places the playing track can lead to, for the go-to button on the player. Back dismisses
 * it, so it needs no third button; an option with nothing behind it (a track with no album) is
 * disabled rather than missing, which the static dialog can afford to do.
 */
@Composable
fun GoToDialog(
    albumAvailable: Boolean,
    artistAvailable: Boolean,
    onDismiss: () -> Unit,
    onGoToAlbum: () -> Unit,
    onGoToArtist: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            // Focusable, but not clickable: a key still held when the dialog appears must not
            // activate whatever holds the initial focus.
            Box(
                modifier = Modifier.focusable()
            ) {
                Text(
                    text = stringResource(R.string.now_playing_go_to),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onGoToAlbum,
                enabled = albumAvailable
            ) {
                Text(stringResource(R.string.now_playing_album))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onGoToArtist,
                enabled = artistAvailable
            ) {
                Text(stringResource(R.string.now_playing_artist))
            }
        }
    )
}
