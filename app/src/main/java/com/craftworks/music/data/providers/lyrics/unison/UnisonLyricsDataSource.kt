package com.craftworks.music.data.providers.lyrics.unison

import android.content.Context
import androidx.media3.common.MediaMetadata
import com.craftworks.music.data.model.Lyric
import com.craftworks.music.data.model.LyricSource
import com.craftworks.music.data.model.Lyrics
import com.craftworks.music.data.model.LyricsLine
import com.craftworks.music.data.model.SyncType
import com.craftworks.music.data.model.UnisonLyricsData
import com.craftworks.music.data.model.UnisonLyricsResponse
import com.craftworks.music.data.model.UnisonSearchResponse
import com.craftworks.music.data.providers.lyrics.asKnownDurationMs
import com.craftworks.music.data.providers.lyrics.asQueryValue
import com.craftworks.music.data.providers.lyrics.durationWithinTolerance
import com.craftworks.music.utils.getTimeStamps
import com.craftworks.music.utils.mmssToMilliseconds
import com.craftworks.music.utils.parseTtml
import com.craftworks.music.utils.separateBackgroundLyrics
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val UserAgent = "Chora - Navidrome Client (https://github.com/CraftWorksMC/Chora)"

@Singleton
class UnisonLyricsDataSource @Inject constructor(
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
            val cacheDir = File(context.cacheDir, "unison_http_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            publicStorage(FileStorage(cacheDir))
        }

        install(Logging) {
            level = LogLevel.INFO
            logger = Logger.SIMPLE
        }

        expectSuccess = false
    }

    suspend fun getLyrics(
        metadata: MediaMetadata?,
        ignoreCachedResponse: Boolean = false,
        durationToleranceSeconds: Int
    ): Lyrics? = withContext(Dispatchers.IO) {
        val artist = (metadata?.extras?.getString("lyricsArtist") ?: metadata?.artist?.toString()).asQueryValue()
        val title = metadata?.title?.toString().asQueryValue()
        val isrc = metadata?.extras?.getString("isrc")
            ?.split(",")
            ?.firstNotNullOfOrNull { it.asQueryValue() }
        val durationMs = metadata?.durationMs.asKnownDurationMs()

        try {
            // The ISRC names the recording, so it is tried first; the name search below runs only
            // when there is no ISRC, or when the identifier's entry turned out to be a different
            // edit of the song.
            val byIsrc = isrc?.let { findByIsrc(it, durationMs, durationToleranceSeconds, ignoreCachedResponse) }
            if (byIsrc != null) return@withContext byIsrc

            if (title == null) return@withContext null

            val response = client.get("https://unison.boidu.dev/lyrics") {
                parameter("song", title)
                parameter("artist", artist)
                parameter("duration", durationMs?.let { (it / 1000).toInt() })

                header(HttpHeaders.UserAgent, UserAgent)
                cacheHeader(ignoreCachedResponse)
            }.body<UnisonLyricsResponse>()

            return@withContext response.data?.toLyrics()
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
     * Looks the recording up by its ISRC and fetches the entry's lyrics, but only when the search
     * returns that same recording at a length the playing track agrees with.
     */
    private suspend fun findByIsrc(
        isrc: String,
        durationMs: Long?,
        durationToleranceSeconds: Int,
        ignoreCachedResponse: Boolean
    ): Lyrics? = try {
        val hits = client.get("https://unison.boidu.dev/lyrics/search") {
            parameter("q", isrc)

            header(HttpHeaders.UserAgent, UserAgent)
            cacheHeader(ignoreCachedResponse)
        }.body<UnisonSearchResponse>().data.orEmpty()

        val hit = hits.firstOrNull {
            it.isrc.equals(isrc, ignoreCase = true) &&
                    durationWithinTolerance(durationMs, it.duration?.toDouble(), durationToleranceSeconds)
        }

        if (hit == null) null
        else client.get("https://unison.boidu.dev/") {
            url { appendPathSegments("lyrics", hit.id.toString()) }

            header(HttpHeaders.UserAgent, UserAgent)
            cacheHeader(ignoreCachedResponse)
        }.body<UnisonLyricsResponse>().data?.toLyrics()
    } catch (e: Exception) {
        // A failed identifier lookup must leave the name search a chance to answer.
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

private fun UnisonLyricsData.toLyrics(): Lyrics? {
    val text = lyrics?.takeIf { it.isNotBlank() } ?: return null

    return when (format) {
        "ttml" -> parseTtml(text, LyricSource.UNISON)
        "lrc" -> {
            val lines = mutableListOf<LyricsLine>()

            text.lines().forEach { lyric ->
                if (lyric.isBlank()) return@forEach
                val timeStampRaw = getTimeStamps(lyric).firstOrNull() ?: return@forEach
                val time = mmssToMilliseconds(timeStampRaw) ?: 0
                val lineText = lyric.substringAfter("]").trim()

                lines.add(separateBackgroundLyrics(lineText, time))
            }

            // A file without a single timestamp is no better than no lyrics at all.
            if (lines.isEmpty()) null
            else Lyrics(
                syncType = SyncType.LINE,
                source = LyricSource.UNISON,
                lines = lines
            )
        }
        "plain" -> Lyrics(
            syncType = SyncType.NONE,
            source = LyricSource.UNISON,
            lines = listOf(
                LyricsLine(
                    startMs = -1,
                    lines = listOf(Lyric(text))
                )
            )
        )
        else -> null
    }
}
