package com.craftworks.music.data.providers.lyrics.binimum

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaMetadata
import com.craftworks.music.data.model.BiniLyricsResponse
import com.craftworks.music.data.model.BiniLyricsResult
import com.craftworks.music.data.model.LyricSource
import com.craftworks.music.data.model.Lyrics
import com.craftworks.music.data.providers.lyrics.asKnownDurationMs
import com.craftworks.music.data.providers.lyrics.asQueryValue
import com.craftworks.music.data.providers.lyrics.durationDistanceMs
import com.craftworks.music.data.providers.lyrics.durationWithinTolerance
import com.craftworks.music.utils.parseTtml
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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val UserAgent = "Chora - Navidrome Client (https://github.com/CraftWorksMC/Chora)"

@Singleton
class BiniLyricsDataSource @Inject constructor(
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
            val cacheDir = File(context.cacheDir, "binimum_http_cache")
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
        val isrc = metadata?.extras?.getString("isrc")
            ?.split(",")
            ?.firstNotNullOfOrNull { it.asQueryValue() }
        val artist = (metadata?.extras?.getString("lyricsArtist") ?: metadata?.artist?.toString()).asQueryValue()
        val title = metadata?.title?.toString().asQueryValue()
        val album = metadata?.albumTitle?.toString().asQueryValue()
        val durationMs = metadata?.durationMs.asKnownDurationMs()

        try {
            // The ISRC names the recording itself, so it is tried before any name search. The name
            // search runs only when there is no ISRC, or when the ISRC - which can cover several
            // pressings - turned up nothing whose length matches the playing track.
            val match =
                isrc?.let { search(ignoreCachedResponse, mapOf("isrc" to it), durationMs, durationToleranceSeconds) }
                    ?: title?.let {
                        search(
                            ignoreCachedResponse,
                            mapOf(
                                "track" to it,
                                "artist" to artist,
                                "album" to album,
                                "duration" to durationMs?.let { ms -> (ms / 1000).toInt() }
                            ),
                            durationMs,
                            durationToleranceSeconds
                        )
                    }
                    ?: return@withContext null

            val ttml = parseTtml(client.get(match.lyricsUrl).bodyAsText(), LyricSource.BINI_LYRICS)
            return@withContext ttml
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) {
                return@withContext null
            }
            e.printStackTrace()
            return@withContext null
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    /**
     * Runs one query and picks the result whose length agrees with the playing track, preferring a
     * word-timed file and then the closest duration. Null when the query failed or nothing matched.
     */
    private suspend fun search(
        ignoreCachedResponse: Boolean,
        parameters: Map<String, Any?>,
        durationMs: Long?,
        durationToleranceSeconds: Int
    ): BiniLyricsResult? = try {
        val results = client.get("https://lyrics-api.binimum.org/") {
            parameters.forEach { (name, value) -> value?.let { parameter(name, it) } }

            header(HttpHeaders.UserAgent, UserAgent)
            cacheHeader(ignoreCachedResponse)
        }.body<BiniLyricsResponse>().results

        val withinTolerance = results.filter {
            durationWithinTolerance(durationMs, it.duration?.toDouble(), durationToleranceSeconds)
        }
        Log.d("BINILYRICS", "${parameters.keys} query: ${results.size} results, ${withinTolerance.size} within ±${durationToleranceSeconds}s")

        withinTolerance
            .sortedWith(
                compareBy(
                    { it.timing_type != "word" },
                    { durationDistanceMs(durationMs, it.duration?.toDouble()) }
                )
            )
            .firstOrNull()
    } catch (e: Exception) {
        // A failed query - the ISRC one included - must leave the other query a chance to answer.
        e.printStackTrace()
        null
    }
}

private fun HttpRequestBuilder.cacheHeader(ignoreCachedResponse: Boolean) {
    if (ignoreCachedResponse)
        header(HttpHeaders.CacheControl, "no-cache")
    else
        header(HttpHeaders.CacheControl, "max-stale=2592000")
}
