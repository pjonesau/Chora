package com.craftworks.music.migrations

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.craftworks.music.data.model.LyricSource
import com.craftworks.music.data.model.LyricsProvider
import com.craftworks.music.dataStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * Switches Unison off for installs that have it on.
 *
 * Changing the in-code default only affects a fresh install: an existing one has a stored
 * provider list, written while Unison was on by default, and it would keep querying a catalogue
 * too small to answer for most tracks.
 *
 * Only Unison is touched, and only when it is on, so a customised order and every other source's
 * state survive; the toggle in the lyrics settings switches it back on.
 */
class UnisonLyricsOffMigration : Migration {
    // Frozen rather than referenced from MediaProviderSettingsManager: a migration must keep
    // reading the key it was written against even if that manager is refactored later.
    private val lyricProvidersKey = stringPreferencesKey("lyric_providers")

    private val json = Json { ignoreUnknownKeys = true }

    override fun up(context: Context) {
        runBlocking {
            context.dataStore.edit { preferences ->
                val stored = preferences[lyricProvidersKey] ?: return@edit

                val providers = runCatching {
                    json.decodeFromString<List<LyricsProvider>>(stored)
                }.getOrNull() ?: return@edit

                val unison = providers.firstOrNull { it.source == LyricSource.UNISON }
                if (unison == null || !unison.enabled) return@edit

                preferences[lyricProvidersKey] = json.encodeToString(
                    providers.map {
                        if (it.source == LyricSource.UNISON) it.copy(enabled = false) else it
                    }
                )
            }
        }
    }
}
