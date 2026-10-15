package com.craftworks.music.managers

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.craftworks.music.R
import com.craftworks.music.data.model.LibraryType
import com.craftworks.music.data.model.ProviderFeature
import com.craftworks.music.data.model.favorite
import com.craftworks.music.data.model.id
import com.craftworks.music.data.model.providerId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Favourites for songs, albums and artists, shared by every screen that shows a heart.
 *
 * What the server last said about an item rides along on the item itself (`userFavorite` in a
 * song's or album's metadata, `Artist.userFavorite`), and screens keep their lists for a long
 * time, so that value goes stale the moment the user toggles something. A toggle is therefore
 * remembered here, in memory, and wins over the value an item carries: the same song then shows
 * the same heart in the queue, on Now Playing and in whatever list is behind them, without any
 * of them reloading. Kept for the life of the process - a change made from another device shows
 * up after a restart.
 *
 * The change is shown at once and sent in the background; if the server refuses it the heart
 * goes back and a toast says so.
 */
object FavoriteManager {
    // Outlives any screen, so a toggle that is on its way is not cancelled by leaving the screen.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val overrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    private fun key(type: LibraryType, providerId: String?, id: String?) =
        "${type.name}:${providerId.orEmpty()}:${id.orEmpty()}"

    private fun providerFor(providerId: String?) =
        providerId?.let { MediaProviderManager.getProvider(it) }
            ?: MediaProviderManager.currentProvider.value

    /** Whether the item's provider has favourites at all (local files do not). */
    fun isAvailable(providerId: String?): Boolean =
        providerFor(providerId)?.featureFlags?.contains(ProviderFeature.FAVORITES) == true

    fun toggle(
        context: Context,
        type: LibraryType,
        providerId: String?,
        id: String,
        currentlyFavorite: Boolean
    ) {
        val itemKey = key(type, providerId, id)
        val target = !currentlyFavorite
        overrides.update { it + (itemKey to target) }

        val appContext = context.applicationContext
        scope.launch {
            val provider = providerFor(providerId)
            val saved = runCatching {
                if (target) provider?.createFavorite(listOf(id), type)
                else provider?.deleteFavorite(listOf(id), type)
            }.onFailure { Log.e("Favorites", "Could not change favorite $itemKey", it) }
                .getOrNull() == true

            if (!saved) {
                overrides.update { it + (itemKey to currentlyFavorite) }
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, R.string.favorite_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * The heart state of one item, kept up to date. [serverValue] is what the item itself says,
     * used until the user has toggled it here.
     */
    @Composable
    fun rememberFavorite(
        type: LibraryType,
        providerId: String?,
        id: String?,
        serverValue: Boolean?
    ): Favorite {
        val context = LocalContext.current
        val itemKey = key(type, providerId, id)
        val override by remember(itemKey) {
            overrides.map { it[itemKey] }.distinctUntilChanged()
        }.collectAsState(initial = overrides.value[itemKey])

        val isFavorite = override ?: (serverValue == true)
        return Favorite(
            available = id != null && isAvailable(providerId),
            isFavorite = isFavorite,
            toggle = { if (id != null) toggle(context, type, providerId, id, isFavorite) }
        )
    }

    /** The heart of a song as it sits in a queue, list or the player. */
    @Composable
    fun rememberFavorite(metadata: MediaMetadata?): Favorite {
        val isSong = metadata?.mediaType != MediaMetadata.MEDIA_TYPE_RADIO_STATION
        val favorite = rememberFavorite(
            LibraryType.SONG, metadata?.providerId, metadata?.id, metadata?.favorite
        )
        return if (isSong) favorite else favorite.copy(available = false)
    }

    @Composable
    fun rememberFavorite(song: MediaItem): Favorite = rememberFavorite(song.mediaMetadata)
}

@Stable
data class Favorite(
    /** False for providers without favourites and for items that cannot be one (radio stations). */
    val available: Boolean,
    val isFavorite: Boolean,
    val toggle: () -> Unit,
)
