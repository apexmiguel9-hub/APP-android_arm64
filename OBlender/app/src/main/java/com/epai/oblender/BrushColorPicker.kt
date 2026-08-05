// Picker HSV circular estilo Blender, dibujado con android.graphics nativo para
// encajar en el mini menu del sculpt wheel (todo el panel usa nativeCanvas +
// hit-test manual). El ring es hue (theta/2pi), el disco interior sat (radial),
// y el valor se ajusta con el slider "Val" (fila 7) del panel.
package com.epai.oblender

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

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

/** HSV -> ARGB (0xAARRGGBB) para android.graphics. */
internal fun bcpArgb(h: Float, s: Float, v: Float): Int {
    val (r, g, b) = bcpHsvToRgb(h, s, v)
    return Color.rgb((r * 255f).toInt(), (g * 255f).toInt(), (b * 255f).toInt())
}

/** Ancho del anillo de hue en px (depende del radio exterior). */
private fun bandOf(radius: Float): Float = (radius * 0.34f).coerceIn(18f, 34f)

/** Dibuja el color wheel (ring hue + disco sat + muestra central + marcador hue)
 *  centrado en (cx, cy) con radio exterior [radius]. El valor [v] se usa solo
 *  para previsualizar el disco; el ajuste de val es el slider aparte del panel. */
internal fun drawWheelNative(
    canvas: android.graphics.Canvas,
    cx: Float,
    cy: Float,
    radius: Float,
    hue: Float,
    sat: Float,
    v: Float
) {
    val band = bandOf(radius)
    val rIn = radius - band
    val rMid = (radius + rIn) / 2f

    // Ring hue: sweep gradient (rojo -> ... -> rojo) alrededor del disco.
    val sweep = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = band
        shader = SweepGradient(cx, cy, intArrayOf(
            Color.rgb(0xFF, 0x00, 0x00), Color.rgb(0xFF, 0xFF, 0x00),
            Color.rgb(0x00, 0xFF, 0x00), Color.rgb(0x00, 0xFF, 0xFF),
            Color.rgb(0x00, 0x00, 0xFF), Color.rgb(0xFF, 0x00, 0xFF),
            Color.rgb(0xFF, 0x00, 0x00)
        ), null)
    }
    canvas.drawCircle(cx, cy, rMid, sweep)

    // Disco sat: centro blanco (sat 0) -> borde hue pleno a valor actual (sat 1).
    val hueFull = bcpArgb(hue, 1f, v)
    val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(cx, cy, rIn, intArrayOf(Color.WHITE, hueFull), null,
            Shader.TileMode.CLAMP)
    }
    canvas.drawCircle(cx, cy, rIn, disc)

    // Contorno del anillo interior (separa ring y disco).
    canvas.drawCircle(cx, cy, rIn, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.argb(80, 0, 0, 0)
    })

    // Marcador del hue actual sobre el ring.
    val a = Math.toRadians(hue.toDouble()).toFloat()
    val mx = cx + cos(a) * rMid
    val my = cy + sin(a) * rMid
    val cur = bcpArgb(hue, sat, v)
    canvas.drawCircle(mx, my, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    })
    canvas.drawCircle(mx, my, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cur })

    // Muestra del color resultante en el centro del disco.
    canvas.drawCircle(cx, cy, 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cur
        style = Paint.Style.FILL
    })
    canvas.drawCircle(cx, cy, 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    })
}

/** Mapea una posición touch -> [hue, sat]. null si está fuera del wheel.
 *  Solo toca hue/sat; el valor lo controla el slider "Val" del panel. */
internal fun hitWheel(x: Float, y: Float, cx: Float, cy: Float, radius: Float): FloatArray? {
    val dx = x - cx
    val dy = y - cy
    val dist = hypot(dx, dy)
    val rIn = radius - bandOf(radius)
    if (dist > radius) return null
    return if (dist > rIn) {
        var a = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        if (a < 0) a += 360f
        floatArrayOf(a, -1f)
    } else {
        floatArrayOf(-1f, (dist / rIn).coerceIn(0f, 1f))
    }
}
