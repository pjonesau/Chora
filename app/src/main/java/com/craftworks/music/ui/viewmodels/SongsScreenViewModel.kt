package com.craftworks.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.StarRating
import com.craftworks.music.data.model.MediaQuery
import com.craftworks.music.data.model.SongListSort
import com.craftworks.music.data.model.SortOrder
import com.craftworks.music.data.model.id
import com.craftworks.music.data.repository.SongRepository
import com.craftworks.music.managers.DataRefreshManager
import com.craftworks.music.managers.settings.LocalDataSettingsManager
import com.craftworks.music.managers.settings.MiscSettingsManager
import com.craftworks.music.utils.LogFailures
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SongsScreenViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val localDataSettingsManager: LocalDataSettingsManager,
    private val miscSettingsManager: MiscSettingsManager
) : ViewModel() {

    private val _allSongs = MutableStateFlow<List<MediaItem>>(emptyList())
    val allSongs: StateFlow<List<MediaItem>> = _allSongs.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val searchResults: StateFlow<List<MediaItem>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.ASC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _sort = MutableStateFlow(SongListSort.NAME)
    val sort: StateFlow<SongListSort> = _sort.asStateFlow()

    private val _showFavoritesOnly = MutableStateFlow(false)
    val showFavoritesOnly: StateFlow<Boolean> = _showFavoritesOnly.asStateFlow()

    init {
        viewModelScope.launch(LogFailures) {
            combine(
                localDataSettingsManager.sortSong,
                localDataSettingsManager.sortSongOrder,
                localDataSettingsManager.showFavoriteSong
            ) { sort, sortOrder, showFavorites -> Triple(sort, sortOrder, showFavorites) }
                .distinctUntilChanged()
                .collect { (sort, sortOrder, showFavorites) ->
                    _sort.value = sort
                    _sortOrder.value = sortOrder
                    _showFavoritesOnly.value = showFavorites
                    getSongs()
                }
        }

        viewModelScope.launch(LogFailures) {
            DataRefreshManager.dataSourceChangedEvent.collect {
                getSongs()
            }
        }
    }

    private var getSongsJob: Job? = null
    fun getSongs() {
        getSongsJob?.cancel()

        getSongsJob = viewModelScope.launch(LogFailures) {
            try {
                _isLoading.value = true
                _allSongs.value = songRepository.getSongs(
                    MediaQuery.SongListQuery(
                        sortBy = _sort.value,
                        sortOrder = _sortOrder.value,
                        startIndex = 0,
                        favorite = if (_showFavoritesOnly.value) true else null
                    )
                )
            }
            finally {
                _isLoading.value = false
            }
        }
    }

    fun getMoreSongs(size: Int){
        viewModelScope.launch(LogFailures) {
            _isLoading.value = true
            try {
                coroutineScope {
                    val songOffset = _allSongs.value.size
                    _allSongs.value += songRepository.getSongs(
                        MediaQuery.SongListQuery(
                            sortBy = _sort.value,
                            sortOrder = _sortOrder.value,
                            limit = size,
                            startIndex = songOffset,
                            favorite = if (_showFavoritesOnly.value) true else null
                        )
                    )
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun search(query: String){
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch(LogFailures) {
            _isLoading.value = true
            try {
                coroutineScope {
                    _searchResults.value = songRepository.getSongs(
                        MediaQuery.SongListQuery(
                            sortBy = _sort.value,
                            sortOrder = _sortOrder.value,
                            searchTerm = query,
                            startIndex = 0,
                            favorite = if (_showFavoritesOnly.value) true else null
                        )
                    )
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    fun setSorting(newSort: SongListSort) {
        viewModelScope.launch(LogFailures) {
            localDataSettingsManager.saveSortSong(newSort)
        }
    }
    fun setOrder(newSortOrder: SortOrder) {
        viewModelScope.launch(LogFailures) {
            localDataSettingsManager.saveSortSongOrder(newSortOrder)
        }
    }
    fun setShowFavoritesOnly(showFavorites: Boolean) {
        viewModelScope.launch(LogFailures) {
            localDataSettingsManager.saveShowFavoriteSong(showFavorites)
        }
    }
    fun downloadSong(song: MediaItem) {
        viewModelScope.launch(LogFailures) {
            songRepository.downloadSong(song.mediaMetadata, miscSettingsManager.downloadTemplateFlow.first())
        }
    }

    fun setSongRating(
        songId: String,
        rating: Int
    ) {
        val song = _allSongs.value.firstOrNull {
            it.mediaMetadata.id == songId
        } ?: _searchResults.value.firstOrNull {
            it.mediaMetadata.id == songId
        }

        val maxStars = (song?.mediaMetadata?.userRating as? StarRating)?.maxStars ?: 5

        val updatedSong = song?.buildUpon()?.setMediaMetadata(
            song.mediaMetadata.buildUpon()
                .setUserRating(StarRating(maxStars, rating.toFloat()))
                .build()
        )?.build()

        if (updatedSong != null) {
            _allSongs.value = _allSongs.value.map { item ->
                if (item.mediaId == song.mediaId) updatedSong else item
            }
            _searchResults.value = _searchResults.value.map { item ->
                if (item.mediaId == song.mediaId) updatedSong else item
            }
        }

        viewModelScope.launch(LogFailures) {
            songRepository.setSongRating(songId, rating)
        }
    }
}