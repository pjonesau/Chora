package com.craftworks.music.ui.screens.tv.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craftworks.music.R
import com.craftworks.music.data.model.LyricSource
import com.craftworks.music.managers.settings.MediaProviderSettingsManager
import com.craftworks.music.ui.elements.dialogs.tv.provider.ModifyLrcLibProviderDialog
import com.craftworks.music.ui.elements.tv.TvLyricsProviderCard
import com.craftworks.music.ui.elements.tv.TvSettingsStepper
import kotlinx.coroutines.launch

@Composable
fun TvS_LyricsScreen() {
    val context = LocalContext.current.applicationContext

    val settingsManager = remember { MediaProviderSettingsManager(context) }
    val coroutineScope = rememberCoroutineScope()

    val lrclibUrl by settingsManager.lrcLibEndpointFlow.collectAsStateWithLifecycle("")
    val lyricProviders by settingsManager.lyricProvidersFlow.collectAsStateWithLifecycle(emptyList())

    var showLrcLibEditDialog by remember { mutableStateOf(false) }

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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
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

            TvSettingsStepper(
                title = stringResource(R.string.settings_lyrics_duration_tolerance),
                value = if (tolerance == 0)
                    stringResource(R.string.settings_lyrics_duration_tolerance_exact)
                else
                    stringResource(R.string.settings_lyrics_duration_tolerance_value, tolerance),
                canDecrease = tolerance > 0,
                canIncrease = tolerance < MediaProviderSettingsManager.MAX_LYRICS_DURATION_TOLERANCE,
                onDecrease = {
                    coroutineScope.launch { settingsManager.setLyricsDurationTolerance(tolerance - 1) }
                },
                onIncrease = {
                    coroutineScope.launch { settingsManager.setLyricsDurationTolerance(tolerance + 1) }
                },
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }

    if (showLrcLibEditDialog)
        ModifyLrcLibProviderDialog(
            initialUrl = lrclibUrl,
            setShowDialog = { showLrcLibEditDialog = it }
        )
}
