package com.craftworks.music.data.repository

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.craftworks.music.R
import com.craftworks.music.data.model.LibraryType
import com.craftworks.music.data.model.MediaQuery
import com.craftworks.music.data.model.ScrobbleEvent
import com.craftworks.music.data.model.artists
import com.craftworks.music.data.model.getProvider
import com.craftworks.music.data.model.id
import com.craftworks.music.managers.MediaProviderManager
import com.craftworks.music.utils.StringUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How many songs to ask a "similar music" seed for. Servers cap the response by what they
 * actually know, so asking for a full queue is cheap; a niche seed can still come back shorter.
 */
private const val SimilarSongCount = 50

@Singleton
class SongRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun getSongs(query: MediaQuery.SongListQuery): List<MediaItem> = coroutineScope {
        MediaProviderManager.currentProvider.value?.getSongList(query)?.map { it.toMediaItem() } ?: listOf()
    }

    suspend fun getSong(songId: String): MediaItem? = coroutineScope {
        MediaProviderManager.currentProvider.value?.getSongDetail(songId)?.toMediaItem()
    }
    
    suspend fun setSongRating(
        songId: String, rating: Int = 0
    ) {
        MediaProviderManager.currentProvider.value?.setRating(listOf(songId), rating, LibraryType.SONG)
    }

    suspend fun getSimilarSongs(songId: String, count: Int) : List<MediaItem> = coroutineScope {
        MediaProviderManager.currentProvider.value?.getSimilarSongs(songId, count)?.map { it.toMediaItem() } ?: listOf()
    }

    /**
     * A radio queue seeded by [metadata]'s song. A cold or niche track can come back with
     * nothing at all, so the song's first credited artist is used as the seed instead when the
     * song-seeded endpoint is empty. Both empty means the caller has nothing to play.
     */
    suspend fun getSimilarMusic(
        metadata: MediaMetadata,
        count: Int = SimilarSongCount
    ): List<MediaItem> = coroutineScope {
        val provider = MediaProviderManager.currentProvider.value ?: return@coroutineScope listOf()
        val songId = metadata.id ?: return@coroutineScope listOf()

        val similar = provider.getSimilarSongs(songId, count)
        if (similar.isNotEmpty()) return@coroutineScope similar.map { it.toMediaItem() }

        val artistId = metadata.artists?.firstOrNull { it.id.isNotBlank() }?.id
            ?: return@coroutineScope listOf()
        provider.getArtistRadio(artistId, count).map { it.toMediaItem() }
    }

    suspend fun scrobbleSong(songId: String, position: Int, playbackRate: Float, event: ScrobbleEvent?, submission: Boolean) {
        MediaProviderManager.currentProvider.value?.scrobble(
            id=songId,
            position = position,
            playbackRate = playbackRate,
            event = event,
            submission = submission
        )
    }

    fun downloadSong(song: MediaMetadata, template: String, playlistName: String = "{playlist}", playlistIndex: String = "{playlist_index}") {
        val values = mapOf(
            "title" to StringUtils.makeValidFilename(song.title.toString()),
            "album" to StringUtils.makeValidFilename(song.albumTitle.toString()),
            "artist" to StringUtils.makeValidFilename(song.artist.toString()),
            "album_artist" to StringUtils.makeValidFilename(song.albumArtist.toString()),
            "ext" to (song.extras?.getString("format") ?: "mp3"),
            "track" to song.trackNumber.toString(),
            "disc" to song.discNumber.toString(),
            "playlist" to StringUtils.makeValidFilename(playlistName),
            "playlist_index" to playlistIndex
        )
        val fileName = StringUtils.makeValidFilepath(Regex("\\{(\\w+)\\}").replace(template) { match ->
            val key = match.groupValues[1]
            values[key] ?: match.value
        })
        val request = DownloadManager.Request(song.getProvider()?.getStreamUrl(song.id?:"", false)?.toUri())
            .setTitle("${context.getString(R.string.notification_download_name)} ${song.title}")
            .setDescription(context.getString(R.string.notification_download_desc))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, fileName)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
    }
}
