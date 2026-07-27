package com.epai.oblender.ui.base

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import com.epai.oblender.ui.screens.TitledNavKey

@Composable
fun BaseScreen(
    screenKey: TitledNavKey,
    currentKey: TitledNavKey?,
    useClassEquality: Boolean = false,
    content: @Composable (isVisible: Boolean) -> Unit,
) {
    val targetVisible = remember(currentKey, screenKey, useClassEquality) {
        isTagVisible(screenKey, currentKey, useClassEquality)
    }
    val visibleState = remember { mutableStateOf(false) }
    LaunchedEffect(targetVisible) {
        visibleState.value = targetVisible
    }
    BaseScreenContent(content = content, visible = visibleState.value)
}

@Composable
private fun BaseScreenContent(
    content: @Composable (isVisible: Boolean) -> Unit,
    visible: Boolean
) {
    Box(
        modifier = Modifier.fillMaxSize().clipToBounds()
    ) {
        content(visible)
        if (!visible) {
            Box(
                modifier = Modifier.fillMaxSize().alpha(0f)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = {})
            )
        }
    }
}

private fun isTagVisible(key: TitledNavKey, current: TitledNavKey?, useClassEquality: Boolean): Boolean {
    return when {
        current == null -> false
        useClassEquality -> key::class == current::class
        else -> key == current
    }
}
