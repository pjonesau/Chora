package com.craftworks.music.ui.elements.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onPlaced
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** How long [TvFocusRestoreState.restore] waits for the remembered item to show up. */
private const val RestoreItemTimeoutMilliseconds = 500L

/** How long [TvFocusRestoreState.restore] waits for a list that is still loading. */
private const val LoadListTimeoutMilliseconds = 2000L

/** Plain flag so registering a placed item doesn't read snapshot state during layout. */
private class PlacedItem {
    var registered = false
}

/**
 * Remembers which item of a TV list, grid or row had focus, so focus can be put back on it when
 * the screen is shown again - for example after backing out of a details screen or Now Playing.
 *
 * The remembered key is kept with rememberSaveable, which the NavHost holds on to for as long as
 * the screen stays on the back stack, so it survives navigating away and back (the same way a
 * list's scroll position does). The [Modifier.focusRestorer] / FocusRequester.saveFocusedChild()
 * calls this replaces never restored focus when a screen was entered fresh, and never across
 * screens.
 *
 * ```
 * val focusRestore = rememberTvFocusRestoreState()
 *
 * LaunchedEffect(Unit) { focusRestore.restore(focusRequester) }
 *
 * items(albums) { album ->
 *     TvAlbumCard(
 *         album = album,
 *         modifier = focusRestore.focusModifier(album.mediaMetadata.id ?: album.mediaId),
 *         onClick = { ... },
 *     )
 * }
 * ```
 */
@Stable
class TvFocusRestoreState internal constructor(
    private val focusedItemKeyState: MutableState<String?>,
    private val itemFocusRequesters: MutableMap<String, FocusRequester>,
) {
    /**
     * Attach to every item of the list, grid or row: focus on the item is remembered, and
     * [restore] can put focus back on it. [key] must be stable and unique for the item.
     */
    @Composable
    fun focusModifier(key: String): Modifier {
        val focusRequester = remember { FocusRequester() }
        val placedItem = remember { PlacedItem() }
        DisposableEffect(key, focusRequester) {
            onDispose {
                placedItem.registered = false
                if (itemFocusRequesters[key] === focusRequester) itemFocusRequesters.remove(key)
            }
        }
        return Modifier
            .onPlaced {
                // Only items that made it onto the screen can take focus, so register on placement
                // rather than on composition.
                if (!placedItem.registered) {
                    placedItem.registered = true
                    itemFocusRequesters[key] = focusRequester
                }
            }
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) focusedItemKeyState.value = key }
    }

    /**
     * Requester of the remembered item, if it is currently on screen. Use this to redirect focus
     * into a list (see `FocusProperties.onEnter`), so entering it returns to the item that had
     * focus last instead of whatever item the focus search happens to pick.
     */
    fun focusRequesterOfFocusedItem(): FocusRequester? =
        focusedItemKeyState.value?.let { itemFocusRequesters[it] }

    /**
     * Puts focus back on the remembered item, waiting briefly for it to appear. Falls back to
     * [fallback], which should focus the first item of the list, when nothing was remembered or
     * the item is gone.
     */
    suspend fun restore(fallback: FocusRequester) {
        val key = focusedItemKeyState.value
        if (key == null) {
            // Nothing to restore. The list may still be loading when the screen appears, so wait
            // for its first item before falling back to it.
            withTimeoutOrNull(LoadListTimeoutMilliseconds) {
                snapshotFlow { itemFocusRequesters.isNotEmpty() }.first { it }
            }
            fallback.requestFocus()
            return
        }

        val focusRequester = withTimeoutOrNull(RestoreItemTimeoutMilliseconds) {
            snapshotFlow { itemFocusRequesters[key] }.filterNotNull().first()
        }
        // Fall back if the item is gone, or if it refused focus - a TV screen should never be
        // left without any.
        if (focusRequester?.requestFocus() != true) fallback.requestFocus()
    }
}

@Composable
fun rememberTvFocusRestoreState(): TvFocusRestoreState {
    val focusedItemKey = rememberSaveable { mutableStateOf<String?>(null) }
    val itemFocusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    return remember { TvFocusRestoreState(focusedItemKey, itemFocusRequesters) }
}
