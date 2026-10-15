package com.craftworks.music.ui.screens.tv.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.craftworks.music.R
import com.craftworks.music.data.model.LyricSource
import com.craftworks.music.managers.MediaProviderManager
import com.craftworks.music.managers.settings.MediaProviderSettingsManager
import com.craftworks.music.ui.elements.dialogs.tv.provider.*
import com.craftworks.music.ui.elements.tv.TvLyricsProviderCard
import com.craftworks.music.ui.elements.tv.TvProviderCard
import kotlinx.coroutines.launch

@Composable
fun TvS_ProviderScreen() {
    val context = LocalContext.current.applicationContext

    val providers by MediaProviderManager.allProviders.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "Media",
        "Lyrics"
    )

    var showAddProviderDialog by remember { mutableStateOf(false) }
    var showLrcLibEditDialog by remember { mutableStateOf(false) }

    val settingsManager = remember { MediaProviderSettingsManager(context) }
    val coroutineScope = rememberCoroutineScope()

    val lrclibUrl by settingsManager.lrcLibEndpointFlow.collectAsStateWithLifecycle("")
    val lyricProviders by settingsManager.lyricProvidersFlow.collectAsStateWithLifecycle(emptyList())

    /** The same swap the phone's drag reorder performs: the moved source takes the other's place. */
    val moveLyricProvider: (Int, Int) -> Unit = { index, delta ->
        val to = index + delta
        if (to in lyricProviders.indices) {
            coroutineScope.launch {
                settingsManager.setLyricProviders(
                    lyricProviders.toMutableList().apply { add(to, removeAt(index)) }
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        TabRow(
            modifier = Modifier.fillMaxWidth().focusGroup().focusRestorer(),
            selectedTabIndex = selectedTab
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onFocus = { selectedTab = index },
                    onClick = { selectedTab = index },
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }

        when (selectedTab) {
            0 -> LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(providers, key = { it.id }) { provider ->
                    TvProviderCard(provider)
                }

                item {
                    ListItem(
                        selected = false,
                        onClick = {
                            showAddProviderDialog = true
                        },
                        leadingContent = {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                        },
                        headlineContent = {
                            Text(stringResource(R.string.action_add))
                        }
                    )
                }
            }

            1 -> LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(lyricProviders, key = { _, provider -> provider.source }) { index, provider ->
                    TvLyricsProviderCard(
                        provider = provider,
                        subtitle = if (provider.source == LyricSource.LRCLIB) lrclibUrl else "",
                        isFirst = index == 0,
                        isLast = index == lyricProviders.lastIndex,
                        onMoveUp = { moveLyricProvider(index, -1) },
                        onMoveDown = { moveLyricProvider(index, +1) },
                        onClick = {
                            coroutineScope.launch {
                                settingsManager.setLyricProviders(
                                    lyricProviders.map {
                                        if (it.source == provider.source) it.copy(enabled = !it.enabled)
                                        else it
                                    }
                                )
                            }
                        },
                        onLongClick = {
                            if (provider.source == LyricSource.LRCLIB) showLrcLibEditDialog = true
                        }
                    )
                }

                item(key = "lyrics_duration_tolerance") {
                    val tolerance by settingsManager.lyricsDurationToleranceFlow.collectAsStateWithLifecycle(
                        MediaProviderSettingsManager.DEFAULT_LYRICS_DURATION_TOLERANCE
                    )

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_lyrics_duration_tolerance),
                                style = MaterialTheme.typography.titleSmall
                            )

                            Text(
                                text = if (tolerance == 0)
                                    stringResource(R.string.settings_lyrics_duration_tolerance_exact)
                                else
                                    stringResource(R.string.settings_lyrics_duration_tolerance_value, tolerance),
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Slider(
                                value = tolerance.toFloat(),
                                onValueChange = { },
                                valueRange = 0f..MediaProviderSettingsManager.MAX_LYRICS_DURATION_TOLERANCE.toFloat(),
                                steps = MediaProviderSettingsManager.MAX_LYRICS_DURATION_TOLERANCE - 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onKeyEvent { keyEvent ->
                                        // KeyDown only: the remote sends the pair, which would
                                        // otherwise move two steps per press.
                                        if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false

                                        when (keyEvent.key) {
                                            Key.DirectionRight -> {
                                                coroutineScope.launch { settingsManager.setLyricsDurationTolerance(tolerance + 1) }
                                                true
                                            }
                                            Key.DirectionLeft -> {
                                                coroutineScope.launch { settingsManager.setLyricsDurationTolerance(tolerance - 1) }
                                                true
                                            }
                                            else -> false
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }

    if(showAddProviderDialog)
        TvCreateMediaProviderDialog(setShowDialog = { showAddProviderDialog = it })

    if(showLrcLibEditDialog)
        ModifyLrcLibProviderDialog(
            initialUrl = lrclibUrl,
            setShowDialog = { showLrcLibEditDialog = it }
        )
}