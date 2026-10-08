package com.craftworks.music.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.craftworks.music.data.model.GenreListSort
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.model.MediaQuery
import com.craftworks.music.data.model.SortOrder
import com.craftworks.music.data.repository.AlbumRepository
import com.craftworks.music.data.repository.GenreRepository
import com.craftworks.music.managers.DataRefreshManager
import com.craftworks.music.managers.MediaProviderManager
import com.craftworks.music.managers.settings.LocalDataSettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

@HiltViewModel
class GenresScreenViewModel @Inject constructor(
    private val genreRepository: GenreRepository,
    private val albumRepository: AlbumRepository,
    private val localDataSettingsManager: LocalDataSettingsManager
) : ViewModel() {
    private val _allGenres = MutableStateFlow<List<MediaModel.Genre>>(emptyList())
    val allGenres: StateFlow<List<MediaModel.Genre>> = _allGenres.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MediaModel.Genre>>(emptyList())
    val searchResults: StateFlow<List<MediaModel.Genre>> = _searchResults.asStateFlow()

    private val _genreArtwork =
        MutableStateFlow<Map<String, List<GenreRepository.GenreArtwork>>>(emptyMap())
    val genreArtwork: StateFlow<Map<String, List<GenreRepository.GenreArtwork>>> =
        _genreArtwork.asStateFlow()

    private val _selectedGenre = MutableStateFlow<MediaModel.Genre?>(null)
    val selectedGenre: StateFlow<MediaModel.Genre?> = _selectedGenre.asStateFlow()

    private val _genreAlbums = MutableStateFlow<List<MediaItem>>(emptyList())
    val genreAlbums: StateFlow<List<MediaItem>> = _genreAlbums.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingAlbums = MutableStateFlow(false)
    val isLoadingAlbums: StateFlow<Boolean> = _isLoadingAlbums.asStateFlow()

    private val _sort = MutableStateFlow(GenreListSort.SONG_COUNT)
    val sort: StateFlow<GenreListSort> = _sort.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DESC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    // The map above is the collage cache; this tracks fetches that have not landed yet so a
    // card scrolling in and out of the grid cannot queue the same request twice.
    private val artworkInFlight = mutableSetOf<String>()

    // A screenful of cards composes at once; without a limit they would all fire together.
    private val artworkSemaphore = Semaphore(4)

    init {
        viewModelScope.launch {
            combine(
                localDataSettingsManager.sortGenre,
                localDataSettingsManager.sortGenreOrder
            ) { sort, sortOrder -> sort to sortOrder }
                .distinctUntilChanged()
                .collect { (sort, sortOrder) ->
                    // A sort saved against another provider may not exist on this one.
                    val supported = MediaProviderManager.currentProvider.value?.supportedGenreSort
                    _sort.value = if (supported.isNullOrEmpty() || sort in supported) sort else supported.first()
                    _sortOrder.value = sortOrder
                    getGenres()
                }
        }

        viewModelScope.launch {
            DataRefreshManager.dataSourceChangedEvent.collect {
                // Collages and the open genre belong to the provider that is being replaced.
                _genreArtwork.value = emptyMap()
                artworkInFlight.clear()
                _selectedGenre.value = null
                _genreAlbums.value = emptyList()
                getGenres()
            }
        }
    }

    private var getGenresJob: Job? = null
    fun getGenres() {
        getGenresJob?.cancel()

        getGenresJob = viewModelScope.launch {
            try {
                _isLoading.value = true
                _allGenres.value = genreRepository.getGenres(
                    MediaQuery.GenreListQuery(
                        sortBy = _sort.value,
                        sortOrder = _sortOrder.value,
                        startIndex = 0
                    )
                )
            }
            finally {
                _isLoading.value = false
            }
        }
    }

    /** Pull to refresh: the genre list may be the same, but the collages may not. */
    fun refresh() {
        _genreArtwork.value = emptyMap()
        artworkInFlight.clear()
        getGenres()
    }

    private var searchJob: Job? = null
    fun search(query: String) {
        if (query.isBlank())
            return

        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            try {
                _isLoading.value = true
                _searchResults.value = genreRepository.getGenres(
                    MediaQuery.GenreListQuery(
                        sortBy = _sort.value,
                        sortOrder = _sortOrder.value,
                        startIndex = 0,
                        searchTerm = query
                    )
                )
            }
            finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Fetches a genre's collage covers. Called as each card is composed, so only the genres
     * actually on screen cost a request. Deliberately does not touch [_isLoading], which
     * drives the refresh spinner - scrolling would otherwise flash it.
     */
    fun loadGenreArtwork(genre: MediaModel.Genre) {
        val key = genre.name
        if (_genreArtwork.value.containsKey(key)) return
        synchronized(artworkInFlight) { if (!artworkInFlight.add(key)) return }

        viewModelScope.launch {
            try {
                val artwork = artworkSemaphore.withPermit {
                    genreRepository.getGenreArtwork(key)
                }
                if (artwork.isNotEmpty())
                    _genreArtwork.update { it + (key to artwork) }
            } catch (e: Exception) {
                // A card whose art cannot be loaded keeps its placeholder.
                Log.d("GENRES", "Could not load artwork for $key: ${e.message}")
            } finally {
                synchronized(artworkInFlight) { artworkInFlight.remove(key) }
            }
        }
    }

    private var getAlbumsJob: Job? = null
    fun loadGenreDetails(genreName: String) {
        getAlbumsJob?.cancel()

        _selectedGenre.value = _allGenres.value.firstOrNull { it.name == genreName }
            ?: _searchResults.value.firstOrNull { it.name == genreName }
            ?: MediaModel.Genre(name = genreName)

        getAlbumsJob = viewModelScope.launch {
            try {
                _isLoadingAlbums.value = true
                _genreAlbums.value = genreRepository.getGenreAlbums(genreName, startIndex = 0, limit = 50)
            }
            finally {
                _isLoadingAlbums.value = false
            }
        }
    }

    fun getMoreGenreAlbums(size: Int = 50) {
        val genreName = _selectedGenre.value?.name ?: return
        if (_isLoadingAlbums.value || getAlbumsJob?.isActive == true) return

        getAlbumsJob = viewModelScope.launch {
            try {
                _isLoadingAlbums.value = true
                _genreAlbums.value += genreRepository.getGenreAlbums(
                    genreName,
                    startIndex = _genreAlbums.value.size,
                    limit = size
                )
            }
            finally {
                _isLoadingAlbums.value = false
            }
        }
    }

    /** Every song in the selected genre, for play all and shuffle all. */
    suspend fun getGenreSongs(): List<MediaItem> {
        val genreName = _selectedGenre.value?.name ?: return emptyList()
        return genreRepository.getGenreSongs(genreName)
    }

    suspend fun getAlbum(id: String): List<MediaItem> {
        return albumRepository.getAlbum(id) ?: emptyList()
    }

    fun setSorting(sort: GenreListSort) {
        viewModelScope.launch {
            localDataSettingsManager.saveSortGenre(sort)
        }
    }

    fun setOrder(sortOrder: SortOrder) {
        viewModelScope.launch {
            localDataSettingsManager.saveSortGenreOrder(sortOrder)
        }
    }
}
