@file:androidx.annotation.OptIn(UnstableApi::class)

package com.craftworks.music.ui.playing.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.craftworks.music.R
import com.craftworks.music.ui.elements.tv.TvHorizontalSongCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** How long the queue waits for the current track's row to appear before giving up on focusing it. */
private const val CurrentTrackFocusTimeoutMilliseconds = 2000L

/**
 * The play queue, full screen: opened from the TV Now Playing controls and from the album
 * details actions. Browse with the D-pad, OK jumps to the focused track and closes, Back closes
 * - the caller puts focus back on the button that opened it.
 *
 * A dialog rather than an overlay, so the D-pad cannot wander into the screen behind it. Unlike
 * the phone's PlayQueueContent there is no drag reordering here: the queue only follows the
 * player.
 */
@Composable
fun TvPlayQueue(
    mediaController: MediaController?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var queue by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }

    DisposableEffect(mediaController) {
        fun syncQueue() {
            queue = mediaController?.let { controller ->
                List(controller.mediaItemCount) { controller.getMediaItemAt(it) }
            }.orEmpty()
            currentIndex = mediaController?.currentMediaItemIndex ?: 0
        }

        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) = syncQueue()

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = mediaController?.currentMediaItemIndex ?: 0
            }
        }

        syncQueue()
        mediaController?.addListener(listener)

        onDispose { mediaController?.removeListener(listener) }
    }

    val listState = rememberLazyListState()
    val titleRequester = remember { FocusRequester() }
    val currentTrackRequester = remember { FocusRequester() }
    var currentTrackPlaced by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val controller = mediaController
        if (controller == null || controller.mediaItemCount == 0) {
            // Nothing to focus, but the D-pad still needs somewhere to land so Back works.
            titleRequester.requestFocus()
            return@LaunchedEffect
        }

        val index = controller.currentMediaItemIndex.coerceIn(0, controller.mediaItemCount - 1)
        listState.scrollToItem(index)
        // A row the list has not composed yet has no focus target, and requesting focus on one
        // would throw, so wait for it to be placed - the same wait TvFocusRestoreState does.
        val placed = withTimeoutOrNull(CurrentTrackFocusTimeoutMilliseconds) {
            snapshotFlow { currentTrackPlaced }.first { it }
        }
        if (placed != null) currentTrackRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 48.dp, vertical = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.now_playing_queue),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .focusRequester(titleRequester)
                    .focusable()
                    .padding(vertical = 8.dp)
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.focusGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(queue) { index, song ->
                    val isCurrent = index == currentIndex
                    TvHorizontalSongCard(
                        song = song,
                        modifier = if (isCurrent) {
                            Modifier
                                .focusRequester(currentTrackRequester)
                                .onPlaced { currentTrackPlaced = true }
                        } else Modifier,
                        isCurrent = isCurrent,
                        onClick = {
                            mediaController?.seekTo(index, 0)
                            onClose()
                        }
                    )
                }
            }
        }
    }
}
