package com.craftworks.music.ui.elements.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.craftworks.music.R

/**
 * A settings value on TV, stepped with a pair of buttons.
 *
 * These screens used to put a material3 Slider here, which no remote can steer: the slider is
 * focusable, so focus lands on it, but its own arrow-key handling sits after the caller's
 * modifier in its chain and key events reach the last key handler first - so it consumed Left
 * and Right and fed them to its onValueChange, which these screens have to leave empty, there
 * being nothing to drag. Buttons keep the whole interaction visible and reachable instead.
 *
 * The buttons are dimmed rather than disabled at the ends of the range, following the rule the
 * other TV buttons use: a disabled tv-material button cannot take focus.
 */
@Composable
fun TvSettingsStepper(
    title: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )

            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.focusGroup()
            ) {
                StepperButton(R.drawable.rounded_playlist_remove_24, "Decrease", dimmed = !canDecrease, onClick = onDecrease)
                StepperButton(R.drawable.rounded_add_24, "Increase", dimmed = !canIncrease, onClick = onIncrease)
            }
        }
    }
}

@Composable
private fun StepperButton(
    icon: Int,
    description: String,
    dimmed: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = { if (!dimmed) onClick() },
        modifier = Modifier.alpha(if (dimmed) 0.4f else 1f)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            modifier = Modifier.size(ListItemDefaults.IconSize)
        )
    }
}
