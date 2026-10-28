package com.craftworks.music.ui.elements.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component1
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component2
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Checkbox
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.ListItemScale
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.craftworks.music.R
import com.craftworks.music.data.model.LyricsProvider
import com.craftworks.music.data.providers.media.MediaProvider
import com.craftworks.music.data.providers.media.local.LocalMediaProvider
import com.craftworks.music.data.providers.media.subsonic.SubsonicMediaProvider
import com.craftworks.music.managers.DataRefreshManager
import com.craftworks.music.managers.MediaProviderManager

@Composable
private fun ProviderItem(
    modifier: Modifier = Modifier,
    icon: Int,
    title: String,
    subtitle: String,
    trailingContent: @Composable () -> Unit = { },
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = { }
) {
    ListItem(
        modifier = modifier,
        selected = enabled,
        scale = ListItemScale.None,
        leadingContent = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(ListItemDefaults.IconSize)
            )
        },
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
            )
        },
        supportingContent = if (subtitle.isNotEmpty()) {
            {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else null,
        trailingContent = {
            Row {
                trailingContent()

                Checkbox(
                    checked = enabled,
                    onCheckedChange = { }
                )
            }
        },
        onClick = onClick,
        onLongClick = onLongClick
    )
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalTvMaterial3Api::class)
@Composable
fun TvProviderCard(provider: MediaProvider) {
    val currentProvider by MediaProviderManager.currentProvider.collectAsStateWithLifecycle()

    val libraries = if (provider == currentProvider) {
        currentProvider?.data?.libraries ?: emptyList()
    } else {
        provider.data?.libraries ?: emptyList()
    }

    val checked by remember { derivedStateOf { provider == currentProvider } }

    val (mainFocus, librariesFocus) = remember { FocusRequester.createRefs() }

    ListItem(
        modifier = Modifier
            .focusProperties {
                down =
                    if (libraries.size > 1 && provider == currentProvider) librariesFocus else FocusRequester.Default
            }
            .focusRequester(mainFocus),
        selected = checked,
        scale = ListItemScale.None,
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.s_m_navidrome),
                contentDescription = null,
                modifier = Modifier.size(ListItemDefaults.IconSize)
            )
        },
        headlineContent = {
            Text(
                text = stringResource(provider.providerName),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
            )
        },
        supportingContent = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = when (provider) {
                        is LocalMediaProvider -> provider.data.libraries.joinToString(", ") { it.first.name }
                        is SubsonicMediaProvider -> provider.providerData.url
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (libraries.size > 1 && provider == currentProvider) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .focusRestorer()
                            .focusGroup()
                            .focusRequester(librariesFocus)
                            .focusProperties {
                                up = mainFocus
                            }
                    ) {
                        libraries.forEach { (library, isSelected) ->
                            FilterChip(
                                onClick = {
                                    MediaProviderManager.setProviderLibraries(
                                        provider.id,
                                        libraries = libraries.map { (currentLibrary, currentEnabled) ->
                                            if (currentLibrary.id == library.id) {
                                                Pair(library, !isSelected)
                                            } else {
                                                Pair(currentLibrary, currentEnabled)
                                            }
                                        }
                                    )
                                },
                                content = {
                                    Text(library.name)
                                },
                                leadingIcon =
                                    if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Filled.Done,
                                                contentDescription = null,
                                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                selected = isSelected,
                            )
                        }
                    }
                }
            }
        },
        trailingContent = {
            Row {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { }
                )
            }
        },
        onClick = {
            MediaProviderManager.setCurrentProvider(provider)
        },
        onLongClick = {
            MediaProviderManager.removeProvider(provider.id)
            DataRefreshManager.notifyDataSourcesChanged()
        }
    )
}

@Composable
fun TvLyricsProviderCard(
    provider: LyricsProvider,
    subtitle: String = "",
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onMoveUp: () -> Unit = { },
    onMoveDown: () -> Unit = { },
    onClick: () -> Unit,
    onLongClick: () -> Unit = { }
) {
    val (mainFocus, reorderFocus) = remember { FocusRequester.createRefs() }
    var mainIsFocused by remember { mutableStateOf(false) }

    ProviderItem(
        modifier = Modifier
            .focusRequester(mainFocus)
            .onFocusChanged { mainIsFocused = it.isFocused }
            // Directional focus search only ever looks at siblings, never at the children of
            // the focused item, so the arrows inside this row cannot be reached by D-pad
            // alone and have to be steered into by hand. The guard keeps this from also
            // hijacking Right while an arrow already holds focus, which is what has to move
            // between the two of them.
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.key == Key.DirectionRight && mainIsFocused
                ) {
                    reorderFocus.requestFocus()
                    true
                } else false
            },
        icon = provider.source.icon,
        title = provider.source.displayName,
        subtitle = subtitle,
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .focusGroup()
                    .focusRestorer()
                    .focusRequester(reorderFocus)
            ) {
                ReorderButton(
                    icon = R.drawable.arrow_upward_24px,
                    description = "Move up",
                    dimmed = isFirst,
                    onClick = onMoveUp,
                    // Left on the first arrow is the way back out of the pair; nothing
                    // outside looks in, for the same reason Right needed steering.
                    modifier = Modifier.onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown &&
                            keyEvent.key == Key.DirectionLeft
                        ) {
                            mainFocus.requestFocus()
                            true
                        } else false
                    }
                )
                ReorderButton(R.drawable.arrow_downward_24px, "Move down", dimmed = isLast, onClick = onMoveDown)
            }
        },
        enabled = provider.enabled,
        onClick = onClick,
        onLongClick = onLongClick
    )
}

/**
 * Dimmed rather than disabled at the ends of the list: a disabled tv-material IconButton cannot
 * take focus, so the row would lose it the moment it arrived at the top.
 */
@Composable
private fun ReorderButton(
    icon: Int,
    description: String,
    dimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = { if (!dimmed) onClick() },
        modifier = modifier.alpha(if (dimmed) 0.4f else 1f)
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(icon),
            contentDescription = description,
            modifier = Modifier.size(ListItemDefaults.IconSize)
        )
    }
}
