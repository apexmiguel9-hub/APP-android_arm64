package com.epai.oblender.ui.components

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize

@Composable
fun BoxWithConstraintsScope.rememberBoxSize(): IntSize {
    val density = LocalDensity.current
    return remember(maxWidth, maxHeight) {
        with(density) { IntSize(width = maxWidth.roundToPx(), height = maxHeight.roundToPx()) }
    }
}
