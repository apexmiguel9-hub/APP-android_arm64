// DRAFT (LOCAL-ONLY, no commit) — BrushColorPicker HSV circular estilo Blender.
// Algoritmo basado en el picker inmediato de Blender (source/editors/interface
// + source/blenkernel/BKE_color.hh): ring hue = theta/2pi, sat radial, val = barra.
// Reutilizable desde el panel del sculpt wheel. NO integrado todavía a CarouselDock.
// NOTE: sin Android SDK en este entorno → best-effort; buildear en device/RapidStudio
// para validar y ajustar. Se conservan los converters (hsvToRgb/rgbToHsv/hsvToColorInt)
// de SculptWheelOverlay.kt; en el split futuro se unifican en BrushColor.kt.
package com.epai.oblender

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.toDegrees

/** HSV -> RGB (0..1). h en grados 0..360, s/v 0..1. */
internal fun bcpHsvToRgb(h: Float, s: Float, v: Float): Triple<Float, Float, Float> {
    val hh = ((h % 360f) + 360f) % 360f
    val hi = (hh / 60f).toInt() % 6
    val f = hh / 60f - hi
    val p = v * (1f - s)
    val q = v * (1f - f * s)
    val t = v * (1f - (1f - f) * s)
    return when (hi) {
        0 -> Triple(v, t, p)
        1 -> Triple(q, v, p)
        2 -> Triple(p, v, t)
        3 -> Triple(p, q, v)
        4 -> Triple(t, p, v)
        else -> Triple(v, p, q)
    }
}

internal fun bcpColor(h: Float, s: Float, v: Float): Color {
    val (r, g, b) = bcpHsvToRgb(h, s, v)
    return Color(r, g, b)
}

private val HUE_STEPS = (0..360 step 12).map { it.toFloat() }

/**
 * Picker HSV circular estilo Blender.
 * @param hsv FloatArray(3) -> [h° 0..360, s 0..1, v 0..1]
 * @param onHsv callback con el nuevo [h,s,v]
 */
@Composable
fun BrushColorPicker(
    hsv: FloatArray,
    onHsv: (FloatArray) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp
) {
    val hue = hsv.getOrElse(0) { 0f }
    val sat = hsv.getOrElse(1) { 1f }
    val v = hsv.getOrElse(2) { 1f }
    val currentColor = bcpColor(hue, sat, v)
    val density = LocalDensity.current
    val sz = with(density) { Size(size.toPx(), size.toPx()) }

    Box(modifier = modifier.size(size)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        computeHit(pos, sz, hue, sat, v)?.let { onHsv(it) }
                    }
                }
        ) {
            drawWheel(sz, hue, sat, v, currentColor)
        }

        // Borde selector hue (piedra)
        val rOut = sz.minDimension / 2f
        val sx = (cos(hue * PI.toFloat() / 180f) * (rOut - 12f)).toFloat()
        val sy = (sin(hue * PI.toFloat() / 180f) * (rOut - 12f)).toFloat()
        Canvas(
            modifier = Modifier
                .size(14.dp)
                .offset { androidx.compose.ui.unit.IntOffset((sz.width / 2f + sx - 7).roundToInt(), (sz.height / 2f + sy - 7).roundToInt()) }
        ) {
            drawCircle(Color.White, 4f)
            drawCircle(currentColor, 2f, style = Stroke(6f))
        }

        // Barra de brillo (val)
        BrushValueBar(
            color = bcpColor(hue, sat, 1f),
            value = v,
            onValue = { nv -> onHsv(floatArrayOf(hue, sat, nv)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .align(androidx.compose.ui.Alignment.BottomCenter)
        )
    }
}

private fun drawWheel(size: Size, hue: Float, sat: Float, v: Float, currentColor: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val rOut = minOf(size.width, size.height) / 2f
    val rIn = rOut - 30f
    val cxy = Offset(cx, cy)

    // Ring hue (sweep)
    val hueColors = HUE_STEPS.map { Color.hsv(it, 1f, 1f) }
    val hueBrush = Brush.sweepGradient(*hueColors.toTypedArray(), center = cxy)
    val anulus = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(cxy - Offset(rOut, rOut), cxy + Offset(rOut, rOut)))
        addOval(androidx.compose.ui.geometry.Rect(cxy - Offset(rIn, rIn), cxy + Offset(rIn, rIn)))
        fillType = Path.FillType.EvenOdd
    }
    drawPath(anulus, brush = hueBrush)

    // Sat: radial transparent -> hue pleno
    val satBrush = Brush.radialGradient(
        colors = listOf(Color.Transparent, bcpColor(hue, 1f, v)),
        center = cxy,
        radius = rIn
    )
    drawPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(cxy - Offset(rIn, rIn), cxy + Offset(rIn, rIn))) }, brush = satBrush)

    // Selector del color resultante en el centro
    drawCircle(currentColor, 8f, style = Stroke(2f))
}

@Composable
private fun BrushValueBar(color: Color, value: Float, onValue: (Float) -> Unit, modifier: Modifier) {
    val track = Brush.horizontalGradient(*listOf(Color.Black, color).toTypedArray())
    Canvas(modifier = modifier.pointerInput(Unit) {
        detectTapGestures { pos -> onValue((pos.x / size.width).coerceIn(0f, 1f)) }
    }) {
        drawRect(brush = track)
        val sx = value * size.width
        drawLine(Color.White, Offset(sx, 0f), Offset(sx, size.height), 3f)
    }
}

/** Mapea posición touch -> [h,s,v]. null si está fuera del wheel. */
private fun computeHit(pos: Offset, size: Size, hue: Float, sat: Float, v: Float): FloatArray? {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val dx = pos.x - cx
    val dy = pos.y - cy
    val dist = hypot(dx, dy)
    val rOut = minOf(size.width, size.height) / 2f
    val rIn = rOut - 30f
    if (dist > rOut) return null
    return if (dist > rIn) {
        // Ring -> hue
        var a = toDegrees(atan2(dy, dx)).toFloat()
        if (a < 0) a += 360f
        floatArrayOf(a, sat, v)
    } else {
        // Interior -> sat (radial), hue/val intactos
        floatArrayOf(hue, (dist / rIn).coerceIn(0f, 1f), v)
    }
}
