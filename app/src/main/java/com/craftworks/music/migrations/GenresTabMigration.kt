package com.craftworks.music.migrations

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.craftworks.music.R
import com.craftworks.music.data.BottomNavItem
import com.craftworks.music.data.model.Screen
import com.craftworks.music.dataStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * Puts Genres where Songs was in the saved navigation order.
 *
 * Changing the in-code default list only affects a fresh install: an existing install has a
 * stored list containing Songs and no Genres, so the Songs tab would simply disappear and
 * nothing would take its place.
 *
 * The entry is replaced in position, keeping whatever enabled state the user had chosen, so a
 * customised order survives.
 */
class GenresTabMigration : Migration {
    // Frozen rather than referenced from AppearanceSettingsManager: a migration must keep
    // reading the key it was written against even if that manager is refactored later.
    private val bottomNavItemsKey = stringPreferencesKey("bottom_nav_order")

    private val json = Json { ignoreUnknownKeys = true }

    override fun up(context: Context) {
        runBlocking {
            context.dataStore.edit { preferences ->
                val stored = preferences[bottomNavItemsKey] ?: return@edit

                val items = runCatching {
                    json.decodeFromString<List<BottomNavItem>>(stored)
                }.getOrNull() ?: return@edit

                // Already migrated, or the user removed Songs themselves.
                if (items.any { it.screenRoute == Screen.Genres }) return@edit

                val index = items.indexOfFirst { it.screenRoute == Screen.Songs }
                if (index == -1) return@edit

                val migrated = items.toMutableList()
                migrated[index] = BottomNavItem(
                    title = context.getString(R.string.nav_genres),
                    icon = R.drawable.rounded_genre_24,
                    screenRoute = Screen.Genres,
                    enabled = items[index].enabled
                )

                preferences[bottomNavItemsKey] = json.encodeToString(migrated)
            }
        }
    }
}
