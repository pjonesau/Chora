package com.craftworks.music.player

import android.os.SystemClock
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player

/**
 * What Now Playing shows for the current item: [base] unchanged for anything but a radio station.
 *
 * A station's own metadata has no title - only its name, as station and artist - which the screens
 * printed as "null". The stream may name the song on air (ICY `StreamTitle`, "Artist - Title"),
 * which ExoPlayer merges into the player's metadata as its title, so a station shows that song with
 * its own name beneath, or its name alone with the stream's genre (if any) beneath. Titles that are
 * blank, repeat the station's name or carry a timestamp (Triple M's ad-break markers, "Asset Stop
 * 00:00 2026-10-09T11:00:08.711Z") are not songs and are ignored.
 *
 * A title also expires [SongTitleLifetimeMs] after it first appeared. Most stations send one only
 * when the song changes, so the last song's name stays up through the ads and talk after it, and
 * some streams never change it at all (The Breeze sent "Icehouse - Great Southern Land" for over
 * an hour). Callers re-ask after [radioTitleExpiresInMs] so the screen notices the expiry.
 */
fun Player.withRadioTitle(base: MediaMetadata?): MediaMetadata? {
    val station = currentMediaItem?.mediaMetadata ?: return base
    if (station.mediaType != MediaMetadata.MEDIA_TYPE_RADIO_STATION) return base

    val name = station.station?.toString().orEmpty()
    val live = mediaMetadata
    val song = currentSongTitle(station, live)?.takeIf { it.ageMs() < SongTitleLifetimeMs }?.title

    return station.buildUpon()
        .setTitle(song ?: name)
        .setArtist(if (song != null) name else live.genre?.toString().orEmpty())
        .build()
}

/** How long until the song title on show expires, or null when no title is on show. */
fun Player.radioTitleExpiresInMs(): Long? {
    val station = currentMediaItem?.mediaMetadata ?: return null
    if (station.mediaType != MediaMetadata.MEDIA_TYPE_RADIO_STATION) return null
    val remaining = SongTitleLifetimeMs - (currentSongTitle(station, mediaMetadata)?.ageMs() ?: return null)
    return remaining.takeIf { it > 0 }
}

private const val SongTitleLifetimeMs = 8 * 60 * 1000L

private class SongTitle(val key: String, val title: String, val since: Long) {
    fun ageMs() = SystemClock.elapsedRealtime() - since
}

/** The title on show, keyed by station so that tuning to another station starts its clock afresh. */
private var lastSongTitle: SongTitle? = null

private fun currentSongTitle(station: MediaMetadata, live: MediaMetadata): SongTitle? {
    val name = station.station?.toString().orEmpty()
    val title = live.title?.toString()?.trim()?.takeIf {
        it.isNotEmpty() && !it.equals(name, ignoreCase = true) && !timestamp.containsMatchIn(it)
    } ?: return null

    val key = "${station.extras?.getString("id")}|$title"
    lastSongTitle?.takeIf { it.key == key }?.let { return it }
    return SongTitle(key, title, SystemClock.elapsedRealtime()).also { lastSongTitle = it }
}

private val timestamp = Regex("""\d{4}-\d\d-\d\dT\d\d:\d\d""")
