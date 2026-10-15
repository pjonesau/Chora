package com.craftworks.music.data.repository

import androidx.media3.common.MediaItem
import com.craftworks.music.data.model.AlbumListSort
import com.craftworks.music.data.model.LibraryType
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.model.MediaQuery
import com.craftworks.music.data.model.SongListSort
import com.craftworks.music.data.model.SortOrder
import com.craftworks.music.managers.MediaProviderManager
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenreRepository @Inject constructor() {

    /** One tile of a genre's collage: the image to load, and the album to key its cache on. */
    data class GenreArtwork(
        val albumId: String,
        val url: String
    )

    suspend fun getGenres(
        query: MediaQuery.GenreListQuery
    ): List<MediaModel.Genre> = coroutineScope {
        MediaProviderManager.currentProvider.value?.getGenreList(query) ?: emptyList()
    }

    suspend fun getGenreAlbums(
        genreName: String,
        startIndex: Int = 0,
        limit: Int? = null
    ): List<MediaItem> = coroutineScope {
        MediaProviderManager.currentProvider.value?.getAlbumList(
            MediaQuery.AlbumListQuery(
                sortBy = AlbumListSort.NAME,
                sortOrder = SortOrder.ASC,
                genreIds = listOf(genreName),
                limit = limit,
                startIndex = startIndex
            )
        )?.map { it.toMediaItem() } ?: emptyList()
    }

    /**
     * Up to [count] covers for the genre's collage, in the same order the details screen shows
     * its albums. Artwork is requested at 300px to match Album.toMediaItem(), so a cover that
     * has already been loaded by an album grid comes from Coil's cache.
     */
    suspend fun getGenreArtwork(
        genreName: String,
        count: Int = 6
    ): List<GenreArtwork> = coroutineScope {
        val provider = MediaProviderManager.currentProvider.value ?: return@coroutineScope emptyList()

        provider.getAlbumList(
            MediaQuery.AlbumListQuery(
                sortBy = AlbumListSort.NAME,
                sortOrder = SortOrder.ASC,
                genreIds = listOf(genreName),
                limit = count,
                startIndex = 0
            )
        ).mapNotNull { album ->
            album.imageId?.let { GenreArtwork(album.id, provider.getImageUrl(it, LibraryType.ALBUM, 300)) }
        }
    }

    /**
     * Every song in a genre, for play all / shuffle all.
     *
     * Paged because a server clamps a page to 500 songs, and terminated on an empty page
     * rather than a short one - a server may cap below the size that was asked for, and a
     * short page would then look like the end of the genre.
     */
    suspend fun getGenreSongs(
        genreName: String,
        pageSize: Int = 500,
        maxPages: Int = 20
    ): List<MediaItem> = coroutineScope {
        val provider = MediaProviderManager.currentProvider.value ?: return@coroutineScope emptyList()

        val songs = mutableListOf<MediaItem>()
        var startIndex = 0
        var pages = 0

        while (pages < maxPages) {
            val page = provider.getSongList(
                MediaQuery.SongListQuery(
                    sortBy = SongListSort.NAME,
                    sortOrder = SortOrder.ASC,
                    genreIds = listOf(genreName),
                    limit = pageSize,
                    startIndex = startIndex
                )
            )
            if (page.isEmpty()) break

            songs += page.map { it.toMediaItem() }
            startIndex += page.size
            pages++
        }

        songs
    }
}
