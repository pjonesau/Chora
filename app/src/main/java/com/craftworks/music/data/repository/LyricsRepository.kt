package com.craftworks.music.data.repository

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.MediaMetadata
import com.craftworks.music.data.model.LyricSource
import com.craftworks.music.data.model.Lyrics
import com.craftworks.music.data.model.LyricsProvider
import com.craftworks.music.data.model.SyncType
import com.craftworks.music.data.model.getProvider
import com.craftworks.music.data.model.id
import com.craftworks.music.data.providers.lyrics.binimum.BiniLyricsDataSource
import com.craftworks.music.data.providers.lyrics.lrclib.LrclibDataSource
import com.craftworks.music.data.providers.lyrics.netease.NeteaseDataSource
import com.craftworks.music.data.providers.lyrics.unison.UnisonLyricsDataSource
import com.craftworks.music.managers.settings.MediaProviderSettingsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

object LyricsState {
    val lyrics = MutableStateFlow<Lyrics?>(null)
    val loading = MutableStateFlow(false)
    var open = mutableStateOf(false)
}

@Singleton
class LyricsRepository @Inject constructor(
    val mediaProviderSettingsManager: MediaProviderSettingsManager,
    val lrclibDataSource: LrclibDataSource,
    val biniLyricsDataSource: BiniLyricsDataSource,
    val unisonLyricsDataSource: UnisonLyricsDataSource,
    val neteaseDataSource: NeteaseDataSource
) {
    /** The fetch outlives its caller: the service prefetches while the UI may ask again. */
    private val fetchScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lyricsFetchJob: Job? = null

    suspend fun getLyrics(metadata: MediaMetadata?, ignoreCachedResponse: Boolean = false) {
        if (metadata?.mediaType == MediaMetadata.MEDIA_TYPE_RADIO_STATION) {
            lyricsFetchJob?.cancel()
            LyricsState.loading.value = false
            LyricsState.lyrics.value = null
            return
        }

        lyricsFetchJob?.cancel()

        val providers = mediaProviderSettingsManager.lyricProvidersFlow.first().filter { it.enabled }
        val durationToleranceSeconds = mediaProviderSettingsManager.lyricsDurationToleranceFlow.first()

        LyricsState.loading.value = true
        // LAZY so the job cannot run before `lyricsFetchJob` points at it: the identity check in
        // the finally block is what stops a cancelled, older fetch clearing `loading`.
        val job = fetchScope.launch(start = CoroutineStart.LAZY) {
            try {
                LyricsState.lyrics.value = fetchLyrics(metadata, ignoreCachedResponse, providers, durationToleranceSeconds)
            } finally {
                if (lyricsFetchJob === coroutineContext[Job]) LyricsState.loading.value = false
            }
        }
        lyricsFetchJob = job
        job.start()
    }

    /**
     * The media provider is asked first and alone: it matches by song id, so it is the only source
     * that can know it has the right recording, and while it has lyrics for the track the external
     * sources are not consulted at all. The rest are then ranked by quality and, between equals, by
     * the order they are listed in the settings.
     */
    private suspend fun fetchLyrics(
        metadata: MediaMetadata?,
        ignoreCachedResponse: Boolean,
        providers: List<LyricsProvider>,
        durationToleranceSeconds: Int
    ): Lyrics? {
        if (providers.any { it.source == LyricSource.MEDIA_PROVIDER }) {
            val mediaLyrics = try {
                metadata?.id?.let { metadata.getProvider()?.getLyrics(it) }
                    ?.firstOrNull { it.lines.isNotEmpty() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A server that cannot answer for this song must not take the other sources down.
                e.printStackTrace()
                null
            }

            if (mediaLyrics != null) {
                Log.d(TAG, "served by the media provider (${mediaLyrics.syncType})")
                return mediaLyrics
            }
        }

        // awaitAll() preserves the order of its input, which is the settings order and the tie-break
        // between results of the same quality - do not replace it with a set, or the order is lost.
        val candidates = coroutineScope {
            providers
                .filter { it.source != LyricSource.MEDIA_PROVIDER }
                .map { provider ->
                    async {
                        val result = try {
                            when (provider.source) {
                                LyricSource.LRCLIB -> lrclibDataSource.getLyrics(metadata, ignoreCachedResponse, durationToleranceSeconds)
                                LyricSource.BINI_LYRICS -> biniLyricsDataSource.getLyrics(metadata, ignoreCachedResponse, durationToleranceSeconds)
                                LyricSource.UNISON -> unisonLyricsDataSource.getLyrics(metadata, ignoreCachedResponse, durationToleranceSeconds)
                                LyricSource.NETEASE -> neteaseDataSource.getLyrics(metadata, durationToleranceSeconds)
                                LyricSource.MEDIA_PROVIDER -> null
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }

                        Log.d(TAG, "${provider.source} -> ${result?.let { "${it.syncType}, ${it.lines.size} lines" } ?: "no lyrics"}")
                        result
                    }
                }
                .awaitAll()
        }.filterNotNull().filter { it.lines.isNotEmpty() }

        val winner = candidates.firstOrNull { it.syncType == SyncType.WORD }
            ?: candidates.firstOrNull { it.syncType == SyncType.LINE }
            ?: candidates.firstOrNull()

        Log.d(TAG, winner?.let { "served by ${it.source} (${it.syncType})" } ?: "no lyrics from any source")
        return winner
    }

    private companion object {
        const val TAG = "Lyrics"
    }
}
