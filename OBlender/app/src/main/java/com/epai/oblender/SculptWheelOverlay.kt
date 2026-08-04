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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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

/** Tools REALES de sculpt en este fork (blender-clone blender-v3.6-release):
 *  32 brushes (builtin_brush.<Name>, generados del enum Brush.sculpt_tool en
 *  rna_brush.c) + 14 tools non-brush (builtin.<id>, en space_toolsystem_toolbar.py
 *  _defs_sculpt). Verificadas contra el source; la lista anterior de "42 oficiales"
 *  contenía tools que NO existen aquí (Clay Tubes, Box Trim/Slice, Expand, etc.). */
val sculptTools = listOf(
    // ---- Brushes (builtin_brush.<Name>) ----
    "Draw", "Draw Sharp", "Clay", "Clay Strips", "Clay Thumb",
    "Layer", "Inflate", "Blob", "Crease", "Smooth",
    "Flatten", "Fill", "Scrape", "Multi-plane Scrape", "Pinch",
    "Grab", "Elastic Deform", "Snake Hook", "Thumb", "Pose",
    "Nudge", "Rotate", "Slide Relax", "Boundary", "Cloth",
    "Simplify", "Mask", "Draw Face Sets", "Multires Displacement Eraser",
    "Multires Displacement Smear", "Paint", "Smear",
    // ---- Non-brush tools (builtin.<id>) ----
    "Box Mask", "Lasso Mask", "Line Mask", "Box Hide",
    "Box Face Set", "Lasso Face Set", "Box Trim", "Lasso Trim",
    "Line Project", "Mesh Filter", "Cloth Filter", "Color Filter",
    "Edit Face Set", "Mask by Color"
)

/** idname real de Blender. Brushes: "builtin_brush.<Label>" tal cual (display name
 *  del enum, p.ej. "builtin_brush.Snake Hook" con espacio, "builtin_brush.Slide Relax").
 *  Non-brush: idname propio "builtin.<id>". */
private val nonBrushTools = mapOf(
    "Box Mask" to "builtin.box_mask",
    "Lasso Mask" to "builtin.lasso_mask",
    "Line Mask" to "builtin.line_mask",
    "Box Hide" to "builtin.box_hide",
    "Box Face Set" to "builtin.box_face_set",
    "Lasso Face Set" to "builtin.lasso_face_set",
    "Box Trim" to "builtin.box_trim",
    "Lasso Trim" to "builtin.lasso_trim",
    "Line Project" to "builtin.line_project",
    "Mesh Filter" to "builtin.mesh_filter",
    "Cloth Filter" to "builtin.cloth_filter",
    "Color Filter" to "builtin.color_filter",
    "Edit Face Set" to "builtin.face_set_edit",
    "Mask by Color" to "builtin.mask_by_color"
)

private fun toolId(label: String) = nonBrushTools[label] ?: "builtin_brush." + label

/** Icono del tool Clay (clay.xml, vector 1024x1032 ~462KB). Se infla desde res/raw
 *  (no res/drawable: un pathData tan grande se pasa del límite del string-pool de
 *  AAPT2 y falla al compilar) con VectorDrawableCompat.create usando el propio
 *  parser como AttributeSet. Se cachea en un Bitmap de ~128px una sola vez. */
private var clayIconBitmap: android.graphics.Bitmap? = null

private fun loadClayIcon(context: Context): android.graphics.Bitmap? {
    clayIconBitmap?.let { return it }
    try {
        val res = context.resources
        val parser = res.getXml(R.raw.clay)
        val drawable = androidx.core.graphics.drawable.VectorDrawableCompat.create(
            res, parser, parser, null
        )
        if (drawable != null) {
            val w = 128
            val h = (128f * 1032f / 1024f).toInt()
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            clayIconBitmap = bmp
            android.util.Log.d("OBL.WHEEL", "clay icon loaded ${bmp.width}x${bmp.height}")
            return bmp
        }
    } catch (e: Exception) {
        android.util.Log.e("OBL.WHEEL", "loadClayIcon failed", e)
    }
    return null
}

/** Teclas del keymap de sculpt (blender_default.py, operador C paint.brush_select).
 *  Ordinales = OBLButtonID.h. Camino PROBADO en device (lo usaba el arco anterior).
 *  El resto de tools van por JNI (oblSetToolByIdStatic), que ahora funciona para
 *  TODAS porque el drain setea el área VIEW_3D en el contexto (creator.cc
 *  obl_activate_tool_by_id) y así el operador Python wm.tool_set_by_id sí corre. */
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
private const val VISIBLE_DEG = 60f   // tools con |angulo| <= VISIBLE_DEG (7 visibles)
private const val SPHERE_ACTIVE = 30f // radio base de la esfera activa (px)
private const val SPHERE_IDLE = 24f   // radio base del resto (px)

/** Geometría del arco en dp (misma parábola "bridge" del arco original, anclada al
 *  borde inferior). Única fuente de verdad, compartida con wheelWindowSize(px) para
 *  que la ventana del overlay recorte EXACTAMENTE el arco (sin área muerta que tapa
 *  el sculpt). */
private data class ArcGeometry(val halfW: Float, val arcH: Float, val bandHalf: Float)

private fun arcGeometry(context: Context): ArcGeometry {
    val sw = context.resources.displayMetrics.widthPixels.toFloat()
    val density = context.resources.displayMetrics.density
    val screenWd = sw / density
    return ArcGeometry(
        halfW = screenWd * 0.17f,  // compacto: no invade el área central del sculpt
        arcH = screenWd * 0.055f,
        bandHalf = max(8f, screenWd * 0.02f)
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

    // Mismo filtro que el dibujo: SOLO los tools realmente visibles (centerIdx±3 con
    // |angulo| <= VISIBLE_DEG). Si se considerara el listado completo, un tap cerca de
    // los extremos de la banda podía caer sobre un tool INVISIBLE y "teletransportarse".
    fun visibleIndices(): List<Int> {
        val centerIdx = Math.round(-scrollOffset.value / step).toInt()
            .coerceIn(0, sculptTools.size - 1)
        val lo = (centerIdx - 3).coerceAtLeast(0)
        val hi = (centerIdx + 3).coerceAtMost(sculptTools.size - 1)
        return (lo..hi).filter { abs((it * step) + scrollOffset.value) <= VISIBLE_DEG }
    }

    fun nearestToolIndex(w: Float, h: Float, px: Float, py: Float): Int {
        val cx = w / 2f
        val cy = h
        val halfWW = w / 2f - bandHalf
        var best = -1
        var bestD = 1e9f
        for (i in visibleIndices()) {
            val a = Math.toRadians(((i * step) + scrollOffset.value).toDouble()).toFloat()
            val tx = cx + halfWW * sin(a)
            val ty = cy - arcH * cos(a) * cos(a)
            val d = hypot(px - tx, py - ty)
            if (d < bestD) { bestD = d; best = i }
        }
        // Solo selecciona si el tap cae realmente sobre una esfera.
        return if (bestD <= SPHERE_ACTIVE * 1.8f) best else -1
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

    // Carga (una vez, cacheada) el icono de Clay desde res/raw/clay.xml.
    LaunchedEffect(Unit) { loadClayIcon(context) }

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
                    // No re-centrar durante los 600ms posteriores a una selección del
                    // usuario: el tap ya re-centró al tool elegido y el poll no debe
                    // "teletransportar" el carrusel a otro mientras el keymap procesa.
                    if ((System.currentTimeMillis() - lastUserSelMs) > 600) {
                        recenterTo(idx)
                    }
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
                        if (gestureArmed && dragAccum >= 12f) {
                            // Drag real: snap al tool más cercano al ápice (solo navega).
                            recenterTo(nearestToApexIndex())
                        }
                        // Tap / micro-movimiento: la selección la hace SOLO onTap.
                        // (antes onDragEnd seleccionaba el tool del ápice y pisaba el tool
                        //  que el usuario acababa de tocar -> "se teletransporta")
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
            // Fondo gris + borde dorado (mismo acento ámbar/dorado del arco).
            drawPath(bandPath, Color(0xFF4A4A4A).copy(alpha = 0.90f))
            drawPath(bandPath, Color(0xFFFFC107), style = Stroke(width = 3f))

            // Esferas de herramientas en carousel, con la distribución parabólica.
            // Mismo filtro de visibilidad que el hit-test (visibleIndices).
            val clayBmp = clayIconBitmap?.asImageBitmap()
            val lo = (Math.round(-scrollOffset.value / step).toInt() - 3).coerceAtLeast(0)
            val hi = (Math.round(-scrollOffset.value / step).toInt() + 3).coerceAtMost(sculptTools.size - 1)
            for (i in (lo..hi).filter { abs((it * step) + scrollOffset.value) <= VISIBLE_DEG }) {
                val angleDeg = (i * step) + scrollOffset.value
                val a = Math.toRadians(angleDeg.toDouble()).toFloat()
                val tx = cx + halfWW * sin(a)
                val ty = (cy - arcH * cos(a) * cos(a)).coerceAtMost(cy - 10f)
                val isActive = i == activeSel
                val isHighlight = i == highlightIndex
                val scale = (1.1f - 0.3f * abs(sin(a))).coerceAtLeast(0.8f)
                drawToolSlot(tx, ty, sculptTools[i], scale, isActive, isHighlight, h, clayBmp)
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
    windowH: Float,
    clayBmp: ImageBitmap?
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

    // Icono del tool Clay (clay.xml): dibujado sobre la esfera, recortado al círculo.
    if (label == "Clay" && clayBmp != null) {
        val iconSize = r * 2f * 0.86f
        val clip = Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
        clipPath(clip) {
            drawImage(
                clayBmp,
                dstOffset = IntOffset((cx - iconSize / 2f).toInt(), (cy - iconSize / 2f).toInt()),
                dstSize = IntSize(iconSize.toInt(), iconSize.toInt())
            )
        }
    }

    // Solo el tool ACTIVO lleva nombre, en la franja vacía entre la banda y el borde
    // inferior (no suma altura a la ventana -> el contenedor no tapa el sculpt).
    if (isActive) {
        drawContext.canvas.nativeCanvas.drawText(
            label,
            cx,
            windowH - 8f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(0xFF, 0xC1, 0x07)
                textSize = 14f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
        )
    }
}

/** Tamaño de la ventana del overlay (px): bounding box JUSTO del arco (banda + esferas),
 *  anclada BOTTOM|CENTER_HORIZONTAL. Todo lo demás queda libre para Blender.
 *  OJO unidades: halfW/arcH/bandHalf son dp; SPHERE_* son px. Antes se multiplicaba
 *  (dp+dp+56+SPHERE) por density => box ~525x414px que tapa el sculpt. Ahora:
 *  h = alto del arco (px) + banda (px) + radio esfera (px) + margen. */
fun wheelWindowSize(context: Context): Pair<Int, Int> {
    val g = arcGeometry(context)
    val density = context.resources.displayMetrics.density
    val w = ((g.halfW + g.bandHalf) * 2f + 8f) * density
    val h = (g.arcH + g.bandHalf * 0.5f) * density + SPHERE_ACTIVE * 1.1f + 8f
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
