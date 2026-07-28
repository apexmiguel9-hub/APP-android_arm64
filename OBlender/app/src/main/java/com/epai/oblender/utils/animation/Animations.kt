package com.epai.oblender.utils.animation

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

fun <T> getAnimateTween(): FiniteAnimationSpec<T> = tween(durationMillis = 200)
