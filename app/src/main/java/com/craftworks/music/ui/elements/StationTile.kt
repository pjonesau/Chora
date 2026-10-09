package com.craftworks.music.ui.elements

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import coil.compose.SubcomposeAsyncImage
import com.craftworks.music.R

/**
 * Stand-in artwork for a radio station with no logo on the server: a tile coloured from the
 * station's name, so a station keeps its colour from one visit to the next, and labelled with its
 * frequency when the name carries one ("102.9 Hot Tomato") or with the name itself otherwise.
 *
 * Sizes are designed for the TV's 128dp card and scale with the tile, and the text ignores the
 * system font scale - it is artwork, and has to fit the square whatever the setting.
 */
@Composable
fun StationTile(name: String, modifier: Modifier = Modifier) {
    val frequency = remember(name) { frequencyPattern.find(name)?.value }
    val subtitle = remember(name) { frequency?.let { name.replace(it, "").trim().ifEmpty { null } } }
    val hue = remember(name) { Math.floorMod(name.hashCode(), 360).toFloat() }

    BoxWithConstraints(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(Color.hsv(hue, 0.5f, 0.62f), Color.hsv((hue + 30f) % 360f, 0.6f, 0.36f))
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        val scale = maxWidth / 128.dp
        val density = LocalDensity.current
        fun size(dp: Float) = with(density) { (dp * scale).dp.toSp() }

        Image(
            painter = painterResource(R.drawable.rounded_radio),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding((8 * scale).dp)
                .size((16 * scale).dp)
                .alpha(0.6f)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = (10 * scale).dp, vertical = (20 * scale).dp)
        ) {
            BasicText(
                text = frequency ?: name,
                style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                maxLines = if (frequency != null) 1 else 3,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = size(11f),
                    maxFontSize = size(if (frequency != null) 34f else 22f)
                )
            )
            subtitle?.let {
                BasicText(
                    text = it,
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = size(12f),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * The station's logo from the server when it has one, the generated [StationTile] otherwise. Only a
 * network address counts as a logo: stations without one carry the bundled placeholder as their
 * artwork, for Now Playing and Android Auto, and the tile is the better picture in a grid.
 */
@Composable
fun StationArtwork(radio: MediaItem, modifier: Modifier = Modifier) {
    val logo = radio.mediaMetadata.artworkUri?.takeIf { it.scheme == "http" || it.scheme == "https" }
    val name = radio.mediaMetadata.station?.toString().orEmpty()

    if (logo == null) {
        StationTile(name, modifier)
        return
    }

    Box(modifier) {
        SubcomposeAsyncImage(
            model = logo,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            // A logo that fails to load falls back to the tile rather than an empty square.
            error = { StationTile(name) },
            loading = { StationTile(name) },
            modifier = Modifier
                .matchParentSize()
                .background(Color.White)
        )
    }
}

/** An FM frequency in a station's name: "97.3", "102.9", "River 94.9". */
private val frequencyPattern = Regex("""\b\d{2,3}\.\d\b""")
