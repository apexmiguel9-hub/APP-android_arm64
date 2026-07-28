package com.epai.oblender.setting.unit

fun ClosedFloatingPointRange<Float>.toFloatRange(): ClosedFloatingPointRange<Float> = this

fun IntRange.toFloatRange(): ClosedFloatingPointRange<Float> = start.toFloat()..endInclusive.toFloat()
