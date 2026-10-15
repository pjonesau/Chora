package com.craftworks.music.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

/**
 * Draws the content [amount] wider on each side than the space it was given, and shifts it left,
 * so a lazy row inside a padded grid item can scroll under that padding instead of clipping to
 * it. The original width is still reported upward, so the parent's layout is unchanged. The
 * content must have a bounded max width, since it is measured at the parent's width plus the
 * bleed.
 */
fun Modifier.bleedHorizontal(amount: Dp) = layout { measurable, constraints ->
    val extraPx = (amount * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.maxWidth + extraPx,
            maxWidth = constraints.maxWidth + extraPx
        )
    )
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(-amount.roundToPx(), 0)
    }
}

fun Modifier.fadingEdge(brush: Brush) = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(brush = brush, blendMode = BlendMode.DstIn)
    }