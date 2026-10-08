package com.craftworks.music.ui.screens.tv.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.craftworks.music.R
import com.craftworks.music.managers.MediaProviderManager
import com.craftworks.music.ui.elements.dialogs.tv.provider.TvCreateMediaProviderDialog
import com.craftworks.music.ui.elements.tv.TvProviderCard

@Composable
fun TvS_ProviderScreen() {
    val providers by MediaProviderManager.allProviders.collectAsStateWithLifecycle()

    var showAddProviderDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
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

    if (showAddProviderDialog)
        TvCreateMediaProviderDialog(setShowDialog = { showAddProviderDialog = it })
}
