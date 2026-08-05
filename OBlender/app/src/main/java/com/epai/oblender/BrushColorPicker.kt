// Picker HSV circular estilo Blender, dibujado con android.graphics nativo para
// encajar en el mini menu del sculpt wheel (todo el panel usa nativeCanvas +
// hit-test manual). El ring es hue, el disco interior es un control 2D sat(x)+val(y),
// y el slider "Val" del panel queda como control fino adicional.
package com.epai.oblender

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
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

/** Dibuja el color wheel (ring hue + disco sat/val 2D + marcadores) centrado en
 *  (cx, cy) con radio exterior [radius]. El disco mapea sat en X y val en Y
 *  (estilo picker circular de Blender); el slider "Val" del panel queda como
 *  control fino adicional. */
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

    // Disco interior: control 2D sat(x) + val(y) (estilo picker circular de Blender).
    // Horizontal: blanco (sat 0, izquierda) -> hue pleno (sat 1, derecha).
    // Vertical: color pleno arriba (val 1) -> negro abajo (val 0). Así, ajustar la
    // intensidad/tonalidad ("rojo fuerte / rojo claro") se hace DENTRO del disco sin
    // tocar el hue; el anillo exterior queda exclusivamente para el color.
    val hueFull = bcpArgb(hue, 1f, 1f)
    val discClip = Path().apply { addCircle(cx, cy, rIn, Path.Direction.CW) }
    canvas.save()
    canvas.clipPath(discClip)
    val satGrad = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(cx - rIn, cy, cx + rIn, cy,
            intArrayOf(Color.WHITE, hueFull), null, Shader.TileMode.CLAMP)
    }
    canvas.drawRect(cx - rIn, cy - rIn, cx + rIn, cy + rIn, satGrad)
    val valGrad = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(cx, cy - rIn, cx, cy + rIn,
            intArrayOf(Color.argb(0, 0, 0, 0), Color.BLACK), null, Shader.TileMode.CLAMP)
    }
    canvas.drawRect(cx - rIn, cy - rIn, cx + rIn, cy + rIn, valGrad)
    canvas.restore()

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

    // Marcador de la posición actual (sat, val) dentro del disco.
    val sx = cx + (sat - 0.5f) * 2f * rIn
    val sy = cy + (0.5f - v) * 2f * rIn
    canvas.drawCircle(sx, sy, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    canvas.drawCircle(sx, sy, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cur })
}

/** Mapea una posición touch -> [hue, sat, val] (-1 en los canales que no cambia).
 *  null si está fuera del wheel. Ring = hue; disco = sat(x) + val(y). */
internal fun hitWheel(x: Float, y: Float, cx: Float, cy: Float, radius: Float): FloatArray? {
    val dx = x - cx
    val dy = y - cy
    val dist = hypot(dx, dy)
    val rIn = radius - bandOf(radius)
    if (dist > radius) return null
    return if (dist > rIn) {
        var a = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        if (a < 0) a += 360f
        floatArrayOf(a, -1f, -1f)
    } else {
        val s = (0.5f + 0.5f * dx / rIn).coerceIn(0f, 1f)
        val vv = (0.5f - 0.5f * dy / rIn).coerceIn(0f, 1f)
        floatArrayOf(-1f, s, vv)
    }
}
