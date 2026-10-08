package com.craftworks.music.data.providers.lyrics.lrclib

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaMetadata
import com.craftworks.music.data.model.LrcLibLyrics
import com.craftworks.music.data.model.Lyrics
import com.craftworks.music.data.model.SyncType
import com.craftworks.music.data.model.toLyrics
import com.craftworks.music.managers.settings.MediaProviderSettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.FileStorage
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

private const val UserAgent = "Chora - Navidrome Client (https://github.com/CraftWorksMC/Chora)"

/**
 * How far a search result's duration may be from the track's and still be taken as the same
 * recording. Anything further away is a different edit (radio edit, live version), whose
 * timestamps would not line up with the audio.
 */
private const val SearchDurationToleranceSeconds = 5.0

@Singleton
class LrclibDataSource @Inject constructor(
    private val settingsManager: MediaProviderSettingsManager,
    @ApplicationContext context: Context
) {
    @OptIn(InternalAPI::class)
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }

        install(HttpCache) {
            val cacheDir = File(context.cacheDir, "lrclib_http_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            publicStorage(FileStorage(cacheDir))
        }

        install(Logging) {
            level = LogLevel.INFO
            logger = Logger.SIMPLE
        }

        expectSuccess = true
    }

    suspend fun getLyrics(
        metadata: MediaMetadata?,
        ignoreCachedResponse: Boolean = false
    ): Lyrics? = withContext(Dispatchers.IO) {
        val baseUrl = settingsManager.lrcLibEndpointFlow.first()

        val artist = metadata?.extras?.getString("lyricsArtist") ?: metadata?.artist.toString()
        val title = metadata?.title?.toString()
        val album = metadata?.albumTitle?.toString()
        val duration = metadata?.durationMs?.div(1000)?.toInt()

        try {
            val response = client.get(baseUrl) {
                url {
                    appendPathSegments("api", "get")
                }

                parameter("artist_name", artist)
                parameter("track_name", title)
                parameter("album_name", album)
                parameter("duration", duration)

                header(HttpHeaders.UserAgent, UserAgent)

                if (ignoreCachedResponse)
                    header(HttpHeaders.CacheControl, "no-cache")
                else
                    header(HttpHeaders.CacheControl, "max-stale=2592000")
            }

            val mediaDataPlainLyrics: LrcLibLyrics = response.body()
            Log.d("LRCLIB", response.bodyAsText())
            Log.d("LRCLIB", response.headers.entries().joinToString("\n") { "${it.key}: ${it.value}" })

            val lyrics = mediaDataPlainLyrics.toLyrics()
            if (lyrics.hasTimings()) return@withContext lyrics

            // The record matching artist, title, album and duration exactly is often unsynced
            // even though another entry holding the timestamps for the same recording exists
            // (the same album under a different name, or a regional edition). Keep the plain
            // lyrics if the search cannot find a synced one.
            return@withContext searchTimedLyrics(baseUrl, artist, title, album, duration, ignoreCachedResponse)
                ?: lyrics
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) {
                // Nothing matched that signature at all, so the plain result is not available
                // either - the search is the only chance of getting anything here.
                return@withContext searchTimedLyrics(baseUrl, artist, title, album, duration, ignoreCachedResponse)
            }
            e.printStackTrace()
            return@withContext null
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    /**
     * Searches for the recording and returns the first result that carries timestamps, preferring
     * the closest duration and then the same album. Returns null when there is nothing suitable,
     * leaving the caller with whatever it already had.
     */
    private suspend fun searchTimedLyrics(
        baseUrl: String,
        artist: String?,
        title: String?,
        album: String?,
        duration: Int?,
        ignoreCachedResponse: Boolean
    ): Lyrics? = try {
        if (title.isNullOrBlank()) return null

        val results: List<LrcLibLyrics> = client.get(baseUrl) {
            url {
                appendPathSegments("api", "search")
            }

            parameter("track_name", title)
            artist?.takeIf { it.isNotBlank() }?.let { parameter("artist_name", it) }

            header(HttpHeaders.UserAgent, UserAgent)

            if (ignoreCachedResponse)
                header(HttpHeaders.CacheControl, "no-cache")
            else
                header(HttpHeaders.CacheControl, "max-stale=2592000")
        }.body()

        Log.d("LRCLIB", "search returned ${results.size} results for $title")

        results
            .filter { it.titleMatches(title) && it.durationMatches(duration) }
            .sortedWith(compareBy({ it.durationDistance(duration) }, { !it.albumMatches(album) }))
            .mapNotNull { it.toLyrics() }
            .firstOrNull { it.hasTimings() }
    } catch (e: Exception) {
        // A failing search must not lose the lyrics another provider may have returned.
        e.printStackTrace()
        null
    }
}

private fun LrcLibLyrics.durationMatches(duration: Int?): Boolean {
    val wanted = duration ?: return true
    val found = this.duration ?: return true
    return abs(found - wanted) <= SearchDurationToleranceSeconds
}

private fun LrcLibLyrics.durationDistance(duration: Int?): Double {
    val wanted = duration ?: return 0.0
    val found = this.duration ?: return Double.MAX_VALUE
    return abs(found - wanted)
}

/**
 * Compares titles ignoring case and punctuation, so `Next Ex-Girlfriend` matches
 * `Next Ex-girlfriend`. Containment rather than equality, because one side is often the
 * shortened form of the other (`Ohio` against `Ohio (Come Back To Texas)`).
 */
private fun LrcLibLyrics.titleMatches(title: String): Boolean {
    val wanted = title.normalizedForComparison()
    val found = trackName.normalizedForComparison()
    return wanted.isNotEmpty() && found.isNotEmpty() && (found.contains(wanted) || wanted.contains(found))
}

private fun LrcLibLyrics.albumMatches(album: String?): Boolean {
    val wanted = album?.normalizedForComparison() ?: return false
    val found = albumName.normalizedForComparison()
    return wanted.isNotEmpty() && found.isNotEmpty() && (found.contains(wanted) || wanted.contains(found))
}

/** Lower case, with everything that is not a letter or a digit removed. */
private fun String?.normalizedForComparison(): String =
    this?.lowercase()?.replace(Regex("[^\\p{L}\\p{N}]+"), "") ?: ""

/** True when the lyrics carry timestamps the UI can follow, per line or per word. */
private fun Lyrics?.hasTimings(): Boolean =
    this != null && syncType != SyncType.NONE && lines.isNotEmpty()
