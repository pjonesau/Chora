package com.craftworks.music.managers.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.craftworks.music.data.model.LyricSource
import com.craftworks.music.data.model.LyricsProvider
import com.craftworks.music.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaProviderSettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val LRCLIB_ENDPOINT = stringPreferencesKey("lrclib_endpoint")
        private val LRCLIB_LYRICS = booleanPreferencesKey("lrclib_lyrics_enabled")
        private val NETEASE_LYRICS = booleanPreferencesKey("netease_lyrics_enabled")
        private val LYRIC_PROVIDERS = stringPreferencesKey("lyric_providers")
        private val LYRICS_DURATION_TOLERANCE = intPreferencesKey("lyrics_duration_tolerance_seconds")

        const val DEFAULT_LYRICS_DURATION_TOLERANCE = 5
        const val MAX_LYRICS_DURATION_TOLERANCE = 30

        /**
         * Order is priority: ties between results of the same quality are broken by it. Unison is
         * off by default - its catalogue is small enough that it rarely answers at all.
         */
        val DEFAULT_LYRIC_PROVIDERS = listOf(
            LyricsProvider(LyricSource.MEDIA_PROVIDER, true),
            LyricsProvider(LyricSource.LRCLIB, true),
            LyricsProvider(LyricSource.BINI_LYRICS, false),
            LyricsProvider(LyricSource.UNISON, false),
            LyricsProvider(LyricSource.NETEASE, false),
        )
    }

    val lrcLibEndpointFlow: Flow<String> = context.dataStore.data.map {
        it[LRCLIB_ENDPOINT] ?: "https://lrclib.net"
    }

    suspend fun setLrcLibEndpoint(LrcLibEndpoint: String) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[LRCLIB_ENDPOINT] = LrcLibEndpoint
            }
        }
    }

    val lrcLibLyricsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[LRCLIB_LYRICS] ?: true
    }

    val netEaseLyricsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NETEASE_LYRICS] ?: false
    }

    /** How far a downloaded recording's length may differ from the track's before it is discarded. */
    val lyricsDurationToleranceFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[LYRICS_DURATION_TOLERANCE] ?: DEFAULT_LYRICS_DURATION_TOLERANCE
    }

    suspend fun setLyricsDurationTolerance(seconds: Int) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[LYRICS_DURATION_TOLERANCE] = seconds.coerceIn(0, MAX_LYRICS_DURATION_TOLERANCE)
            }
        }
    }

    val lyricProvidersFlow: Flow<List<LyricsProvider>> = context.dataStore.data.map { preferences ->
        val jsonString = preferences[LYRIC_PROVIDERS] ?: return@map DEFAULT_LYRIC_PROVIDERS

        val stored = try {
            Json.decodeFromString<List<LyricsProvider>>(jsonString)
        } catch (e: Exception) {
            return@map DEFAULT_LYRIC_PROVIDERS
        }

        // A source added after this list was saved is appended with its default, so it can never be
        // invisible to an install that has already stored an order.
        val missing = DEFAULT_LYRIC_PROVIDERS.filter { default -> stored.none { it.source == default.source } }
        stored + missing
    }

    suspend fun setLyricProviders(providers: List<LyricsProvider>) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[LYRIC_PROVIDERS] = Json.encodeToString(providers)
            }
        }
    }
}