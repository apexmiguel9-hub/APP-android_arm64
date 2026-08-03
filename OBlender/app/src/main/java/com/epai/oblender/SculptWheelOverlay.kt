package com.epai.oblender

import android.content.Context
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Las 42 herramientas oficiales de Blender 3.6 Sculpt Mode, en orden desde Draw. */
val sculptTools = listOf(
    "Draw", "Draw Sharp", "Clay", "Clay Strips", "Clay Tubes",
    "Layer", "Inflate", "Blob", "Crease", "Smooth",
    "Flatten", "Fill", "Scrape", "Pinch", "Grab",
    "Snake Hook", "Thumb", "Pose", "Nudge", "Rotate",
    "Slide Relax", "Boundary", "Cloth", "Simplify", "Mask",
    "Face Set", "Mesh Filter", "Color Filter", "Box Mask", "Lasso Mask",
    "Line Mask", "Poly Mask", "Box Trim", "Lasso Trim", "Slice",
    "Box Hide", "Lasso Hide", "Line Project", "Expand", "Sample Detail",
    "Multires Displacement", "Sculpt Curves"
)

/** idname real de Blender (builtin_brush.<Label> tal cual, p.ej. "builtin_brush.Snake Hook"
 *  con espacio) para el poll de sync y el puente JNI. */
private fun toolId(label: String) = "builtin_brush." + label

/** Teclas del keymap de sculpt (blender_default.py, operador C paint.brush_select que SÍ
 *  cambia el brush real; wm.tool_set_by_id es Python y puede no correr en este build).
 *  Ordinales = OBLButtonID.h. Camino PROBADO en device (lo usaba el arco anterior). */
private data class ToolKey(val key: Int, val shift: Boolean = false)

private val toolKeys = mapOf(
    "Draw" to ToolKey(27),               // X
    "Clay" to ToolKey(29),               // C
    "Smooth" to ToolKey(44, shift = true),   // Shift+S
    "Flatten" to ToolKey(22, shift = true),  // Shift+T
    "Pinch" to ToolKey(73),              // P
    "Grab" to ToolKey(45),               // G
    "Snake Hook" to ToolKey(71),         // K
    "Inflate" to ToolKey(23),            // I
    "Layer" to ToolKey(72),              // L
    "Crease" to ToolKey(29, shift = true),   // Shift+C
    "Mask" to ToolKey(31)                // M
)

private fun emitToolKey(key: ToolKey) {
    if (key.shift) {
        OBLNativeActivity.oblSetValueOnStatic("0")  // Shift down
        OBLNativeActivity.oblSetValueStatic(key.key.toString())
        OBLNativeActivity.oblSetValueOffStatic("0") // Shift up
    } else {
        OBLNativeActivity.oblSetValueStatic(key.key.toString())
    }
}

private const val ARC_STEP = 20f      // grados por tool a lo largo del arco
private const val VISIBLE_DEG = 75f   // tools con |angulo| <= VISIBLE_DEG (7 visibles)
private const val SPHERE_ACTIVE = 46f // radio base de la esfera activa (px)
private const val SPHERE_IDLE = 38f   // radio base del resto (px)

/** Geometría del arco en dp (misma proporción que el arco que funcionaba: parábola
 *  "bridge" anclada al borde inferior). Única fuente de verdad, compartida con
 *  wheelWindowSize(px) para que la ventana del overlay recorte EXACTAMENTE el arco. */
private data class ArcGeometry(val halfW: Float, val arcH: Float, val bandHalf: Float)

private fun arcGeometry(context: Context): ArcGeometry {
    val sw = context.resources.displayMetrics.widthPixels.toFloat()
    val density = context.resources.displayMetrics.density
    val screenWd = sw / density
    return ArcGeometry(
        halfW = screenWd * 0.21f,  // más compacto: no tapa el área de sculpt
        arcH = screenWd * 0.08f,
        bandHalf = max(22f, screenWd * 0.025f)
    )
}

/**
 * Carrusel de Sculpt = arco/parábola inferior (anillo cortado en arco), compacto y
 * pegado al borde inferior-centro de la pantalla.
 *
 * - POSICIÓN/ESCALA: la ventana (WindowManager) se redimensiona a wheelWindowSize() =
 *   SOLO la franja del arco (~66% ancho x ~11% alto, BOTTOM|CENTER_HORIZONTAL). El arco
 *   se dibuja con `ty = cy - arcH*cos^2(a)`, `tx = cx + halfW*sin(a)` (misma parábola
 *   del arco original), con el ápice a poca altura sobre el borde inferior.
 * - TOUCH PASSTHROUGH: como una ventana de overlay no puede ser transparente al toque
 *   por píxel hacia la ventana de Blender, la clave es que la VENTANA sea diminuta
 *   (solo el arco) con FLAG_NOT_TOUCH_MODAL: todo toque FUERA de esa franja llega
 *   directo al GLSurfaceView. Dentro de la franja, el hit-test es preciso: el tap solo
 *   selecciona si cae sobre una esfera (nearestToolIndex con umbral) y el drag solo se
 *   arma si empieza sobre la banda (gestureArmed). El Box raíz NO consume nada por sí
 *   mismo; las zonas vacías no disparan ninguna acción.
 * - VISIBILIDAD: solo en modo SCULPT + workspace "Sculpting" (poll); oculto =
 *   GONE + NOT_TOUCHABLE.
 * - ACCIONES: tap/select emite la TECLA del keymap (paint.brush_select, C -> cambia el brush
 *   real). Fallback JNI (oblSetToolByIdStatic) solo para tools sin tecla en el keymap.
 */
@Composable
fun CarouselDock() {
    val density = LocalDensity.current
    val context = LocalContext.current
    val g = arcGeometry(context)
    val halfW = with(density) { g.halfW.dp.toPx() }
    val arcH = with(density) { g.arcH.dp.toPx() }
    val bandHalf = with(density) { g.bandHalf.dp.toPx() }

    val step = ARC_STEP
    val maxOffset = -((sculptTools.size - 1) * step).toFloat()

    val scrollOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    var activeSel by remember { mutableStateOf(-1) }
    var lastUserSelMs by remember { mutableStateOf(0L) }
    var highlightIndex by remember { mutableStateOf(-1) }

    fun nearestToolIndex(w: Float, h: Float, px: Float, py: Float): Int {
        val cx = w / 2f
        val cy = h
        val halfWW = w / 2f - bandHalf
        var best = -1
        var bestD = 1e9f
        for (i in sculptTools.indices) {
            val a = Math.toRadians(((i * step) + scrollOffset.value).toDouble()).toFloat()
            val tx = cx + halfWW * sin(a)
            val ty = cy - arcH * cos(a) * cos(a)
            val d = hypot(px - tx, py - ty)
            if (d < bestD) { bestD = d; best = i }
        }
        // Solo selecciona si el tap cae realmente sobre una esfera.
        return if (bestD <= SPHERE_ACTIVE * 1.6f) best else -1
    }

    fun nearestToApexIndex(): Int {
        // Tool cuyo ángulo (i*step + offset) queda en el ápice (a=0).
        return Math.round(-scrollOffset.value / step).toInt()
            .coerceIn(0, sculptTools.size - 1)
    }

    fun recenterTo(sel: Int) {
        scope.launch {
            scrollOffset.animateTo(
                targetValue = -(sel * step).toFloat(),
                animationSpec = spring(stiffness = 300f, dampingRatio = 0.8f)
            )
        }
    }

    fun selectTool(sel: Int) {
        if (sel < 0 || sel >= sculptTools.size) return
        val label = sculptTools[sel]
        android.util.Log.d("OBL.WHEEL", "select label=$label")
        val key = toolKeys[label]
        if (key != null) {
            emitToolKey(key)   // paint.brush_select (C): cambia el brush real, camino probado
        } else {
            val id = toolId(label)
            android.util.Log.d("OBL.WHEEL", "no key -> JNI fallback id=$id")
            OBLNativeActivity.oblSetToolByIdStatic(id)
        }
        activeSel = sel
        lastUserSelMs = System.currentTimeMillis()
        recenterTo(sel)
    }

    // Poll: solo muestro el arco en Sculpting (modo + workspace); sincroniza el
    // highlight y re-centra el tool activo reportado por Blender.
    LaunchedEffect(Unit) {
        var lastIdx = -2
        var lastActive = OverlayState.sculptArcActive
        while (true) {
            val activeToolId = OBLNativeActivity.getActiveToolIdStatic()
            val mode = OBLNativeActivity.getActiveModeStatic()
            val workspace = OBLNativeActivity.getActiveWorkspaceStatic()
            val inSculpt = mode == CTX_MODE_SCULPT && workspace.startsWith("Sculpting")
            OverlayState.sculptArcActive = inSculpt
            if (inSculpt) {
                val idx = sculptTools.indexOfFirst { toolId(it) == activeToolId }
                if (idx != activeSel && (System.currentTimeMillis() - lastUserSelMs) > 600) {
                    activeSel = idx
                }
                if (idx >= 0 && idx != lastIdx) {
                    lastIdx = idx
                    recenterTo(idx)
                }
            } else {
                lastIdx = -2
                activeSel = -1
            }
            if (inSculpt != lastActive) {
                lastActive = inSculpt
                updateSculptArcWindow(false, inSculpt)
            }
            delay(200)
        }
    }

    // Fuera de Sculpting no se renderiza nada (la ventana ya está GONE + NOT_TOUCHABLE).
    if (!OverlayState.sculptArcActive) return

    // Raíz SIN fondo sólido. Los detectores solo reaccionan con hit-test de banda/esfera;
    // las zonas vacías de la ventana no consumen ni disparan nada.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { pos ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val sel = nearestToolIndex(w, h, pos.x, pos.y)
                        if (sel >= 0) selectTool(sel)
                    }
                )
            }
            .pointerInput(Unit) {
                var gestureArmed = false
                var dragAccum = 0f
                detectDragGestures(
                    onDragStart = { start ->
                        gestureArmed = false
                        dragAccum = 0f
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val cx = w / 2f
                        val cy = h
                        val halfWW = w / 2f - bandHalf
                        val sx = (start.x - cx) / halfWW
                        // El drag solo se arma si empieza SOBRE la banda del arco.
                        if (start.x >= 0 && start.y >= 0 && sx >= -1f && sx <= 1f) {
                            val theta = asin(sx.coerceIn(-1f, 1f))
                            val curveY = cy - arcH * cos(theta) * cos(theta)
                            if (abs(start.y - curveY) <= bandHalf * 2f) {
                                gestureArmed = true
                                highlightIndex = nearestToApexIndex()
                            }
                        }
                    },
                    onDragEnd = {
                        if (gestureArmed) {
                            if (dragAccum < 12f) {
                                if (highlightIndex >= 0) selectTool(highlightIndex)
                                else recenterTo(nearestToApexIndex())
                            } else {
                                recenterTo(nearestToApexIndex())
                            }
                        }
                        gestureArmed = false
                        highlightIndex = -1
                    },
                    onDragCancel = {
                        gestureArmed = false
                        highlightIndex = -1
                    },
                    onDrag = { change, dragAmount ->
                        if (!gestureArmed) return@detectDragGestures
                        change.consume()
                        val halfWW = size.width / 2f - bandHalf
                        val degPerPx = 180f / halfWW
                        val delta = dragAmount.x * degPerPx
                        if (abs(delta) < 120f) {
                            val target = (scrollOffset.value + delta).coerceIn(maxOffset, 0f)
                            scope.launch { scrollOffset.snapTo(target) }
                            dragAccum += abs(delta)
                            highlightIndex = nearestToApexIndex()
                        }
                    }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h
            val halfWW = w / 2f - bandHalf

            // Banda "bridge": anillo cortado en arco inferior (parábola cos^2),
            // mismo trazado del arco original.
            val bandPath = Path()
            val n = 120
            for (i in 0..n) {
                val theta = -90f + 180f * i / n
                val a = Math.toRadians(theta.toDouble()).toFloat()
                val px = cx + (halfWW + bandHalf) * sin(a)
                val py = cy - (arcH + bandHalf) * cos(a) * cos(a)
                if (i == 0) bandPath.moveTo(px, py) else bandPath.lineTo(px, py)
            }
            for (i in n downTo 0) {
                val theta = -90f + 180f * i / n
                val a = Math.toRadians(theta.toDouble()).toFloat()
                val px = cx + (halfWW - bandHalf) * sin(a)
                val py = cy - (arcH - bandHalf) * cos(a) * cos(a)
                bandPath.lineTo(px, py)
            }
            bandPath.close()
            drawPath(bandPath, Color(0xFF1B1B1B).copy(alpha = 0.60f))

            // Esferas de herramientas en carousel, con la distribución parabólica.
            val centerIdx = Math.round(-scrollOffset.value / step).toInt()
                .coerceIn(0, sculptTools.size - 1)
            val lo = (centerIdx - 3).coerceAtLeast(0)
            val hi = (centerIdx + 3).coerceAtMost(sculptTools.size - 1)
            for (i in lo..hi) {
                val angleDeg = (i * step) + scrollOffset.value
                if (abs(angleDeg) > VISIBLE_DEG) continue
                val a = Math.toRadians(angleDeg.toDouble()).toFloat()
                val tx = cx + halfWW * sin(a)
                val ty = (cy - arcH * cos(a) * cos(a)).coerceAtMost(cy - 10f)
                val isActive = i == activeSel
                val isHighlight = i == highlightIndex
                val scale = (1.1f - 0.3f * abs(sin(a))).coerceAtLeast(0.8f)
                drawToolSlot(tx, ty, sculptTools[i], scale, isActive, isHighlight, h)
            }
        }
    }
}

private fun DrawScope.drawToolSlot(
    cx: Float,
    cy: Float,
    label: String,
    scale: Float,
    isActive: Boolean,
    isHighlight: Boolean,
    windowH: Float
) {
    val r = (if (isActive) SPHERE_ACTIVE else SPHERE_IDLE) * scale
    val highlighted = isActive || isHighlight

    // Glow ámbar detrás del activo / en selección.
    if (highlighted) {
        drawCircle(color = Color(0x33FFC107), radius = r + 14f, center = Offset(cx, cy))
        drawCircle(
            color = Color(0xFFFFC107),
            radius = r + 6f,
            center = Offset(cx, cy),
            style = Stroke(width = if (isActive) 3f else 2f)
        )
        if (isActive) {
            drawCircle(
                color = Color(0xFFCC6F03),
                radius = r + 1f,
                center = Offset(cx, cy),
                style = Stroke(width = 3f)
            )
        }
    }

    // Esfera arcilla: gradiente radial, luz arriba-izquierda, sombra abajo-derecha.
    val brush = Brush.radialGradient(
        colors = listOf(Color(0xFFE0E0E0), Color(0xFF9E9E9E), Color(0xFF404040)),
        center = Offset(cx - r * 0.35f, cy - r * 0.4f),
        radius = r * 1.25f
    )
    drawCircle(brush, r, Offset(cx, cy))

    // Label bajo la esfera, solo si cabe dentro de la ventana.
    if (cy + r + 18f <= windowH - 4f) {
        drawContext.canvas.nativeCanvas.drawText(
            label,
            cx,
            cy + r + 18f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (highlighted) {
                    android.graphics.Color.rgb(0xFF, 0xC1, 0x07)
                } else {
                    android.graphics.Color.rgb(0xD0, 0xD0, 0xD0)
                }
                textSize = if (isActive) 15f else 13f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = isActive || isHighlight
            }
        )
    }
}

/** Tamaño de la ventana del overlay (px): SOLO la franja del arco + margen mínimo,
 *  anclada BOTTOM|CENTER_HORIZONTAL. Todo lo demás queda libre para Blender. */
fun wheelWindowSize(context: Context): Pair<Int, Int> {
    val g = arcGeometry(context)
    val density = context.resources.displayMetrics.density
    val w = ((g.halfW + g.bandHalf) * 2f + 8f) * density
    val h = (g.arcH + g.bandHalf + 56f + SPHERE_ACTIVE) * density
    return w.toInt() to h.toInt()
}

fun createSculptWheelOverlay(context: Context, lifecycleOwner: LifecycleOwner): ComposeView {
    android.util.Log.d("OBL", "createSculptWheelOverlay: start")
    val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent { CarouselDock() }
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        // Oculta + no táctil por defecto; el poll la revela solo en Sculpting.
        visibility = android.view.View.GONE
    }
    composeView.setViewTreeLifecycleOwner(lifecycleOwner)
    composeView.setViewTreeSavedStateRegistryOwner(SimpleSavedStateRegistryOwner())

    val (w, h) = wheelWindowSize(context)
    val lp = WindowManager.LayoutParams(
        w, h,
        LayoutParams.TYPE_APPLICATION_PANEL,
        LayoutParams.FLAG_NOT_FOCUSABLE or
                LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                LayoutParams.FLAG_NOT_TOUCHABLE,
        android.graphics.PixelFormat.TRANSPARENT
    )
    lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
    OverlayState.sculptArcLp = lp

    Handler(Looper.getMainLooper()).post {
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.addView(composeView, lp)
            android.util.Log.d("OBL", "createSculptWheelOverlay: added ${w}x${h}")
        } catch (e: Exception) {
            android.util.Log.e("OBL", "createSculptWheelOverlay: ADD FAILED", e)
        }
    }
    return composeView
}
