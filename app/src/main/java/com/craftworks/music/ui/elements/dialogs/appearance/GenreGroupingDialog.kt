package com.craftworks.music.ui.elements.dialogs.appearance

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.craftworks.music.R
import com.craftworks.music.managers.settings.GenreGrouping
import com.craftworks.music.managers.settings.LocalDataSettingsManager
import com.craftworks.music.ui.elements.bounceClick
import kotlinx.coroutines.launch

@Preview(showBackground = true)
@Composable
fun PreviewGenreGroupingDialog() {
    GenreGroupingDialog(setShowDialog = { })
}

@OptIn(
    ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun GenreGroupingDialog(setShowDialog: (Boolean) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val selectedGrouping by LocalDataSettingsManager(context).genreGrouping.collectAsState(
        GenreGrouping.UNDER_5
    )

    AlertDialog(
        onDismissRequest = { setShowDialog(false) },
        title = { Text(stringResource(R.string.appearance_genre_grouping)) },
        text = {
            Column {
                for (option in GenreGrouping.entries) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = (option == selectedGrouping),
                                onClick = {
                                    coroutineScope.launch {
                                        LocalDataSettingsManager(context).saveGenreGrouping(option)
                                    }
                                    setShowDialog(false)
                                },
                                role = Role.RadioButton
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selectedGrouping,
                            onClick = null,
                            modifier = Modifier.bounceClick()
                        )
                        Text(
                            text = if (option == GenreGrouping.OFF)
                                stringResource(R.string.genre_grouping_off)
                            else
                                stringResource(R.string.genre_grouping_under, option.groupBelow),
                            fontWeight = FontWeight.Normal,
                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = { }
    )
}
