package com.craftworks.music.data.providers.lyrics.lrclib

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaMetadata
import com.craftworks.music.data.model.LrcLibLyrics
import com.craftworks.music.data.model.Lyrics
import com.craftworks.music.data.model.SyncType
import com.craftworks.music.data.model.toLyrics
import com.craftworks.music.data.providers.lyrics.asKnownDurationMs
import com.craftworks.music.data.providers.lyrics.asQueryValue
import com.craftworks.music.data.providers.lyrics.durationDistanceMs
import com.craftworks.music.data.providers.lyrics.durationWithinTolerance
import com.craftworks.music.data.providers.lyrics.isSameAs
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
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
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

private const val UserAgent = "Chora - Navidrome Client (https://github.com/CraftWorksMC/Chora)"

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
        ignoreCachedResponse: Boolean = false,
        durationToleranceSeconds: Int
    ): Lyrics? = withContext(Dispatchers.IO) {
        val baseUrl = settingsManager.lrcLibEndpointFlow.first()

        val artist = (metadata?.extras?.getString("lyricsArtist") ?: metadata?.artist?.toString()).asQueryValue()
        val title = metadata?.title?.toString().asQueryValue()
        val album = metadata?.albumTitle?.toString().asQueryValue()
        val durationMs = metadata?.durationMs.asKnownDurationMs()

        // Both endpoints need a track name, and there is no identifier to look a nameless track up by.
        if (title == null) return@withContext null

        try {
            val response = client.get(baseUrl) {
                url {
                    appendPathSegments("api", "get")
                }

                parameter("artist_name", artist)
                parameter("track_name", title)
                parameter("album_name", album)
                parameter("duration", durationMs?.let { (it / 1000).toInt() })

                header(HttpHeaders.UserAgent, UserAgent)
                cacheHeader(ignoreCachedResponse)
            }

            val record: LrcLibLyrics = response.body()
            Log.d("LRCLIB", "api/get -> $record")

            // The signature match is the server's, so check it against the playing track before
            // trusting it: a record whose length disagrees is a different edit of the song.
            val withinTolerance = durationWithinTolerance(durationMs, record.duration, durationToleranceSeconds)
            if (!withinTolerance)
                Log.d("LRCLIB", "api/get match (${record.duration}s) is outside ±${durationToleranceSeconds}s of the track - searching instead")

            val lyrics = if (withinTolerance) record.toLyrics() else null
            if (lyrics?.hasTimings() == true) return@withContext lyrics

            // The record matching artist, title, album and duration exactly is often unsynced
            // even though another entry holding the timestamps for the same recording exists
            // (the same album under a different name, or a regional edition). Keep the plain
            // lyrics if the search cannot find a synced one.
            return@withContext searchTimedLyrics(baseUrl, artist, title, album, durationMs, durationToleranceSeconds, ignoreCachedResponse)
                ?: lyrics
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) {
                // Nothing matched that signature at all, so the plain result is not available
                // either - the search is the only chance of getting anything here.
                return@withContext searchTimedLyrics(baseUrl, artist, title, album, durationMs, durationToleranceSeconds, ignoreCachedResponse)
            }
            e.printStackTrace()
            return@withContext null
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    /**
     * Searches for the recording and returns the first result that carries timestamps, discarding
     * anything whose length is outside the tolerance and preferring the matching artist, then word
     * synchronisation, then the closest duration. Returns null when there is nothing suitable,
     * leaving the caller with whatever it already had.
     */
    private suspend fun searchTimedLyrics(
        baseUrl: String,
        artist: String?,
        title: String,
        album: String?,
        durationMs: Long?,
        durationToleranceSeconds: Int,
        ignoreCachedResponse: Boolean
    ): Lyrics? {
        searchTimedLyricsOnce(baseUrl, artist, title, title, album, durationMs, durationToleranceSeconds, ignoreCachedResponse)
            ?.let { return it }

        // LRCLIB matches track_name literally, so a title carrying its version suffix
        // ("Total Eclipse of the Heart (New radio edit mix)") finds nothing even though the
        // database holds the recording under the bare name. Ask again without the bracketed
        // parts; what comes back is still matched against the name we were given, so the
        // suffix costs nothing but the extra request.
        val shorn = title
            .replace(bracketedPart, " ")
            .replace(whitespaceRun, " ")
            .trim()
        if (shorn.isEmpty() || shorn == title) return null

        Log.d("LRCLIB", "search for \"$title\" found nothing; looking again for \"$shorn\"")
        return searchTimedLyricsOnce(baseUrl, artist, shorn, title, album, durationMs, durationToleranceSeconds, ignoreCachedResponse)
    }

    private suspend fun searchTimedLyricsOnce(
        baseUrl: String,
        artist: String?,
        queryTitle: String,
        matchTitle: String,
        album: String?,
        durationMs: Long?,
        durationToleranceSeconds: Int,
        ignoreCachedResponse: Boolean
    ): Lyrics? = try {
        val results: List<LrcLibLyrics> = client.get(baseUrl) {
            url {
                appendPathSegments("api", "search")
            }

            parameter("track_name", queryTitle)
            artist?.let { parameter("artist_name", it) }

            header(HttpHeaders.UserAgent, UserAgent)
            cacheHeader(ignoreCachedResponse)
        }.body()

        // The search covers every recording with that name, so the playing track's length does the
        // discarding. Artist is a ranking input rather than a filter: a hard filter would drop
        // compilations and Various Artists entries that are the right recording all the same.
        val withinTolerance = results.filter {
            it.trackName.isSameAs(matchTitle) && durationWithinTolerance(durationMs, it.duration, durationToleranceSeconds)
        }
        Log.d("LRCLIB", "search \"$queryTitle\": ${results.size} results, ${withinTolerance.size} within ±${durationToleranceSeconds}s")

        withinTolerance
            .sortedWith(
                compareBy(
                    { !it.artistName.isSameAs(artist) },
                    { it.hasWordSync != true },
                    { durationDistanceMs(durationMs, it.duration) },
                    { !it.albumName.isSameAs(album) }
                )
            )
            .mapNotNull { it.toLyrics() }
            .firstOrNull { it.hasTimings() }
    } catch (e: Exception) {
        // A failing search must not lose the lyrics another provider may have returned.
        e.printStackTrace()
        null
    }
}

private val bracketedPart = Regex("""\s*[(\[][^()\[\]]*[)\]]""")
private val whitespaceRun = Regex("""\s+""")

private fun HttpRequestBuilder.cacheHeader(ignoreCachedResponse: Boolean) {
    if (ignoreCachedResponse)
        header(HttpHeaders.CacheControl, "no-cache")
    else
        header(HttpHeaders.CacheControl, "max-stale=2592000")
}

/** True when the lyrics carry timestamps the UI can follow, per line or per word. */
private fun Lyrics?.hasTimings(): Boolean =
    this != null && syncType != SyncType.NONE && lines.isNotEmpty()
