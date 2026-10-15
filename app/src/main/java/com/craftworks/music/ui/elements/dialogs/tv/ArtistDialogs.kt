package com.craftworks.music.ui.elements.dialogs.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How far a single D-pad press scrolls the biography. */
private val BiographyScrollStep = 96.dp

/**
 * The full artist biography, which the details screen only shows a few lines of. It arrives as
 * HTML from the server's metadata agent, so it is rendered as markup rather than shown as tags.
 *
 * The body takes focus itself and turns D-pad up and down into scrolling. There are no focusable
 * items in the text for the focus search to move between, so without this the biography would
 * stay stuck at the top. Back closes the dialog.
 */
@Composable
fun ArtistBiographyDialog(
    artistName: String,
    biography: String,
    setShowDialog: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = { setShowDialog(false) },
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = artistName,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            val scrollState = rememberScrollState()
            val focusRequester = remember { FocusRequester() }
            val coroutineScope = rememberCoroutineScope()
            val scrollStep = with(LocalDensity.current) { BiographyScrollStep.toPx() }

            val biographyText = remember(biography) {
                runCatching { AnnotatedString.fromHtml(biography) }
                    .getOrElse { AnnotatedString(biography) }
            }

            LaunchedEffect(Unit) {
                // The dialog's window does not take focus on the first frame, so keep asking for
                // a moment - without focus on the body, the D-pad keys below never arrive.
                repeat(10) {
                    if (focusRequester.requestFocus()) return@LaunchedEffect
                    delay(50)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false

                        when (event.key) {
                            Key.DirectionUp -> {
                                coroutineScope.launch { scrollState.scrollBy(-scrollStep) }
                                true
                            }
                            Key.DirectionDown -> {
                                coroutineScope.launch { scrollState.scrollBy(scrollStep) }
                                true
                            }
                            else -> false
                        }
                    }
                    .verticalScroll(scrollState)
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = biographyText,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = { }
    )
}
