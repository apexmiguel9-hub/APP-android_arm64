package com.epai.oblender.ui.control.joystick

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.movtery.layer_controller.observable.ObservableJoystickStyle

@Composable
fun StyleableJoystick(
    style: ObservableJoystickStyle,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 100.dp,
    onDirectionChanged: ((direction: String) -> Unit)? = null,
    deadZoneRatio: Float = 0.1f,
    lockThreshold: Float = 0.8f,
    canLock: Boolean = false,
    onCanLock: ((Boolean) -> Unit)? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape
            )
            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Joystick",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
