package com.epai.oblender.ui.screens

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.scene.Scene

fun <E : TitledNavKey> NavBackStack<E>.clearWith(navKey: E) {
    val targetClass = navKey::class.java
    if (none { it::class.java == targetClass }) {
        add(navKey)
    }
    removeIf { it::class.java != targetClass }
}

@Composable
fun rememberSwapTween(): FiniteAnimationSpec<Float> {
    return remember { tween(durationMillis = 200) }
}

@Composable
fun <T : Any> rememberTransitionSpec(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform {
    return remember {
        { ContentTransform(fadeIn(), fadeOut()) }
    }
}
