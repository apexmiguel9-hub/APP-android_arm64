package com.epai.oblender.utils.animation

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

fun getAnimateTween(): FiniteAnimationSpec<Float> = tween(durationMillis = 200)
