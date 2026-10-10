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
import com.craftworks.music.data.providers.lyrics.normalizedForComparison
import com.craftworks.music.managers.settings.MediaProviderSettingsManager
import com.craftworks.music.utils.getTimeStamps
import com.craftworks.music.utils.mmssToMilliseconds
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
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
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

        val lookup = Lookup(baseUrl, title, album, durationMs, durationToleranceSeconds, ignoreCachedResponse)

        try {
            lookup.get(artist, title, album)?.let { return@withContext it }
        } catch (e: Exception) {
            // Anything but "no such record" (handled in get) means the server cannot be asked
            // properly right now, and a dozen more requests would fare no better.
            e.printStackTrace()
            return@withContext null
        }

        // LRCLIB's /api/get treats every field it is given as part of the signature, so a record
        // filed under another album name - a compilation, a regional or deluxe edition - is only
        // found when the album is left out.
        if (album != null) {
            for (queryTitle in lookup.queryTitles()) {
                try {
                    lookup.get(artist, queryTitle, null)?.let { return@withContext it }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // The record matching the signature is often unsynced even though another entry holding
        // the timestamps for the same recording exists, so search before settling for the plain
        // lyrics the lookups may have kept.
        lookup.searchTimedLyrics(artistCandidates(metadata, artist))
            ?: lookup.plain
    }

    /**
     * One track's lookup: the requests it makes share the track's details, and the plain lyrics
     * the first record offered are kept here in case nothing timed turns up.
     */
    private inner class Lookup(
        val baseUrl: String,
        val title: String,
        val album: String?,
        val durationMs: Long?,
        val durationToleranceSeconds: Int,
        val ignoreCachedResponse: Boolean
    ) {
        var plain: Lyrics? = null
            private set

        /**
         * The titles to ask for: the track's own, then without its bracketed parts. LRCLIB matches
         * track_name literally, so a title carrying its version suffix ("Total Eclipse of the
         * Heart (New radio edit mix)") finds nothing even though the database holds the recording
         * under the bare name. Whatever comes back is still matched against the full title.
         */
        fun queryTitles(): List<String> {
            val shorn = title
                .replace(bracketedPart, " ")
                .replace(whitespaceRun, " ")
                .trim()
            return if (shorn.isEmpty() || shorn == title) listOf(title) else listOf(title, shorn)
        }

        /**
         * Asks /api/get for the record matching the signature and returns it when it carries
         * usable timestamps, keeping it as [plain] otherwise. A missing record is null, not an
         * error; any other failure is thrown.
         */
        suspend fun get(artist: String?, queryTitle: String, album: String?): Lyrics? {
            val record: LrcLibLyrics = try {
                retryingWhenBusy {
                    client.get(baseUrl) {
                        url {
                            appendPathSegments("api", "get")
                        }

                        parameter("artist_name", artist)
                        parameter("track_name", queryTitle)
                        parameter("album_name", album)
                        // The server compares against the exact length, so the fraction matters
                        // to a tolerance measured in whole seconds. Hundredths are plenty.
                        parameter("duration", durationMs?.let { (it / 10) / 100.0 })

                        header(HttpHeaders.UserAgent, UserAgent)
                        cacheHeader(ignoreCachedResponse)
                    }
                }.body()
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.NotFound) {
                    Log.d("LRCLIB", "api/get \"$queryTitle\" (album ${album ?: "omitted"}): no record")
                    return null
                }
                throw e
            }
            Log.d("LRCLIB", "api/get -> $record")

            // The signature match is the server's, so check it against the playing track before
            // trusting it: a record whose length disagrees is a different edit of the song.
            if (!durationWithinTolerance(durationMs, record.duration, durationToleranceSeconds)) {
                Log.d("LRCLIB", "api/get match (${record.duration}s) is outside ±${durationToleranceSeconds}s of the track")
                return null
            }

            timedLyrics(record)?.let { return it }
            if (plain == null)
                plain = record.copy(syncedLyrics = null, lyricsfile = null).toLyrics()
            return null
        }

        /**
         * Searches for the recording and returns the first result that carries usable timestamps,
         * or null when there is nothing suitable, leaving the caller with whatever it already had.
         *
         * LRCLIB's search treats artist_name as a phrase every result must contain, so a credit
         * written differently from the database's ("Artist feat. Guest", a duo joined with "&",
         * a compilation's album artist) finds nothing at all. Each of [artists] is tried in turn,
         * and finally the title alone - accepting only results credited to one of them, as the
         * name on its own matches every cover and namesake.
         */
        suspend fun searchTimedLyrics(artists: List<String>): Lyrics? {
            val titles = queryTitles()
            val primary = artists.firstOrNull()

            for (artist in artists.ifEmpty { listOf(null) })
                for (queryTitle in titles)
                    searchOnce(queryTitle, artist, rankArtist = artist, requireArtistAmong = null)
                        ?.let { return it }

            if (artists.isEmpty()) return null
            val credited = artists.filterNot { it.isVariousArtists() }
            if (credited.isEmpty()) return null
            for (queryTitle in titles)
                searchOnce(queryTitle, null, rankArtist = primary, requireArtistAmong = credited)
                    ?.let { return it }
            return null
        }

        private suspend fun searchOnce(
            queryTitle: String,
            artist: String?,
            rankArtist: String?,
            requireArtistAmong: List<String>?
        ): Lyrics? = try {
            val results: List<LrcLibLyrics> = retryingWhenBusy {
                client.get(baseUrl) {
                    url {
                        appendPathSegments("api", "search")
                    }

                    parameter("track_name", queryTitle)
                    artist?.let { parameter("artist_name", it) }

                    header(HttpHeaders.UserAgent, UserAgent)
                    cacheHeader(ignoreCachedResponse)
                }
            }.body()

            // The search covers every recording with that name, so the playing track's title and
            // length do the discarding.
            val candidates = results.filter { result ->
                result.trackName.isSameAs(title) &&
                    durationWithinTolerance(durationMs, result.duration, durationToleranceSeconds) &&
                    (requireArtistAmong == null || requireArtistAmong.any { it.isSameAs(result.artistName) })
            }
            Log.d("LRCLIB", "search \"$queryTitle\" by ${artist ?: "anyone"}: ${results.size} results, ${candidates.size} usable")

            candidates
                .sortedWith(
                    compareBy(
                        { !it.artistName.isSameAs(rankArtist) },
                        { it.hasWordSync != true },
                        { durationDistanceMs(durationMs, it.duration) },
                        { !it.albumName.isSameAs(album) }
                    )
                )
                .firstNotNullOfOrNull { timedLyrics(it) }
        } catch (e: Exception) {
            // A failing search must not lose the lyrics another provider may have returned.
            e.printStackTrace()
            null
        }

        /**
         * The record's lyrics if they carry timestamps that fit the track. Some entries are timed
         * to a different master: "We Didn't Start the Fire" sung through to 4:40 on a 4:10 track
         * would drift further out of step with every line, so it is treated as untimed. Only sung
         * lines count - an LRC often ends with a bare timestamp marking where the song stops.
         */
        private fun timedLyrics(record: LrcLibLyrics): Lyrics? {
            val lyrics = record.toLyrics()?.takeIf { it.hasTimings() } ?: return null
            if (durationMs == null) return lyrics
            val lastSungMs = record.lastSungMs(lyrics) ?: return lyrics
            if (lastSungMs > durationMs + MisalignedGraceMs) {
                Log.d("LRCLIB", "record ${record.id} sings until ${lastSungMs}ms on a ${durationMs}ms track - not using its timings")
                return null
            }
            return lyrics
        }
    }

    /**
     * The artist names to search under, most likely first: the track's performer, the album
     * artist, the displayed credit, that credit without its guests, its first name of several,
     * and the sort name. "Various Artists" is only ever searched as the first, never added.
     */
    private fun artistCandidates(metadata: MediaMetadata?, primary: String?): List<String> {
        val display = metadata?.artist?.toString().asQueryValue()
        val withoutGuests = display?.replace(featuringCredit, "")?.trim()
        val firstCredited = withoutGuests?.split(joinedCredit)?.firstOrNull()?.trim()

        val fallbacks = listOf(
            metadata?.extras?.getString("lyricsAlbumArtist") ?: metadata?.albumArtist?.toString(),
            display,
            withoutGuests,
            firstCredited,
            metadata?.extras?.getString("lyricsArtistSort")
        ).filterNot { it.isVariousArtists() }

        return (listOf(primary) + fallbacks)
            .mapNotNull { it.asQueryValue() }
            .distinctBy { it.normalizedForComparison() }
    }
}

/**
 * LRCLIB sheds load with a 503 ("The server is busy, please retry in a moment"), which would
 * otherwise leave the track without lyrics until it is played again - nothing caches a failure.
 * Its busy spells outlast the one second its `Retry-After` asks for, so back off 1, 2 then 4
 * seconds (or longer when the server asks for more, up to 10) before giving up. A skip to the
 * next track cancels the wait along with the rest of the fetch.
 */
private suspend fun retryingWhenBusy(request: suspend () -> HttpResponse): HttpResponse {
    for (backoffMs in BusyRetryBackoffMs) {
        try {
            return request()
        } catch (e: ServerResponseException) {
            if (e.response.status != HttpStatusCode.ServiceUnavailable) throw e
            val askedMs = e.response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()?.times(1000) ?: 0
            val waitMs = maxOf(backoffMs, askedMs.coerceAtMost(MaxBusyWaitMs))
            Log.d("LRCLIB", "server busy - retrying in ${waitMs}ms")
            delay(waitMs)
        }
    }
    return request()
}

private val BusyRetryBackoffMs = listOf(1000L, 2000L, 4000L)
private const val MaxBusyWaitMs = 10_000L

private val bracketedPart = Regex("""\s*[(\[][^()\[\]]*[)\]]""")
private val whitespaceRun = Regex("""\s+""")

/** "Artist feat. Guest", "Artist with Guest": everything from the guest credit on. */
private val featuringCredit = Regex("""\s+(feat\.?|ft\.?|featuring|with)\s.*$""", RegexOption.IGNORE_CASE)

/** The separators of a joint credit: "A, B", "A & B", "A and B", "A x B", "A vs. B", "A / B", "A; B". */
private val joinedCredit = Regex("""\s*(?:,|&|\band\b|\bx\b|\bvs\.?|/|;)\s*""", RegexOption.IGNORE_CASE)

/**
 * How far past the end of the track a sung line may be timed before the record is taken to belong
 * to a different master. Rounding and a fade the file trims account for a second or so.
 */
private const val MisalignedGraceMs = 2_000L

private fun String?.isVariousArtists(): Boolean =
    normalizedForComparison().let { it == "variousartists" || it == "va" }

/**
 * When the last line with words is sung, or null when the record has none. Taken from the record's
 * LRC where it has no lyrics file, because [toLyrics] keeps only the first timestamp of a line
 * that repeats (a chorus) and the last sung moment is often one of the later ones.
 */
private fun LrcLibLyrics.lastSungMs(lyrics: Lyrics): Int? {
    val fromLrc = syncedLyrics
        ?.takeIf { lyricsfile.isNullOrBlank() || lyricsfile == "null" }
        ?.lines()
        ?.filter { it.substringAfterLast("]").isNotBlank() }
        ?.flatMap { getTimeStamps(it) }
        ?.mapNotNull { mmssToMilliseconds(it) }
        ?.maxOrNull()
    return fromLrc ?: lyrics.lines
        .filter { line -> line.lines.any { it.text.isNotBlank() || !it.words.isNullOrEmpty() } }
        .maxOfOrNull { it.startMs }
}

private fun HttpRequestBuilder.cacheHeader(ignoreCachedResponse: Boolean) {
    if (ignoreCachedResponse)
        header(HttpHeaders.CacheControl, "no-cache")
    else
        header(HttpHeaders.CacheControl, "max-stale=2592000")
}

/** True when the lyrics carry timestamps the UI can follow, per line or per word. */
private fun Lyrics?.hasTimings(): Boolean =
    this != null && syncType != SyncType.NONE && lines.isNotEmpty()
