@file:OptIn(UnstableApi::class) package com.craftworks.music.player

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SongHelper {
    companion object{
        suspend fun play(
            mediaItems: List<MediaItem>,
            index: Int,
            mediaController: MediaController?,
            shuffle: Boolean = false,
        ) {
            if (mediaItems.isEmpty())
                return

            withContext(Dispatchers.Main) {
                // Always set shuffle mode explicitly, so a previous Shuffle doesn't leak into a normal Play.
                mediaController?.shuffleModeEnabled = shuffle
                mediaController?.setMediaItems(mediaItems, index, 0)
                mediaController?.prepare()
                mediaController?.play()
            }
        }
        suspend fun shuffle(mediaItems: List<MediaItem>, mediaController: MediaController?) {
            if (mediaItems.isEmpty())
                return

            // Shuffle the list itself and play it in order, rather than turning on the player's
            // shuffle mode. ExoPlayer's shuffle order is a random permutation that ignores the
            // start index, so the first track could sit anywhere in it - Next went dim partway
            // through, while the queue (shown in list order) still had songs after the current one.
            // A pre-shuffled list keeps the queue, Next/Previous and Play Next in agreement.
            play(mediaItems.shuffled(), 0, mediaController, shuffle = false)
        }
        fun enqueue(mediaItems: List<MediaItem>, mediaController: MediaController?) {
            mediaController?.addMediaItems(mediaItems)
        }
        fun playNext(mediaItems: List<MediaItem>, mediaController: MediaController?) {
            mediaController?.addMediaItems(
                mediaController.currentMediaItemIndex + 1,
                mediaItems
            )
        }
    }
}