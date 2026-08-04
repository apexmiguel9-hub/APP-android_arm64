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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

/** Icono del tool Clay (clay.xml, vector 1024x1032 ~462KB) en res/drawable. Se
 *  infla con ResourcesCompat.getDrawable (vector compilado, mismo camino que el
 *  prototipo MainActivity_improved.kt que sí lo muestra) y se cachea en un Bitmap
 *  de ~256px una sola vez. */
private var clayIconBitmap: android.graphics.Bitmap? = null

private fun loadClayIcon(context: Context): android.graphics.Bitmap? {
    clayIconBitmap?.let { return it }
    try {
        val res = context.resources
        val drawable = androidx.core.content.res.ResourcesCompat.getDrawable(res, R.drawable.clay, null)
        if (drawable != null) {
            val w = 256
            val h = (256f * 1032f / 1024f).toInt()
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            // Sanity check: el bitmap debe tener tinta (píxeles no transparentes).
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            var ink = 0
            for (p in px) if (android.graphics.Color.alpha(p) > 0) ink++
            android.util.Log.d("OBL.WHEEL", "clay icon loaded ${bmp.width}x${bmp.height} ink=$ink/${px.size}")
            if (ink == 0) {
                android.util.Log.e("OBL.WHEEL", "loadClayIcon: vector renderizó vacío (sin tinta)")
                return null
            }
            clayIconBitmap = bmp
            return bmp
        }
    } catch (e: Exception) {
        android.util.Log.e("OBL.WHEEL", "loadClayIcon failed: " + e.message, e)
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

// --- Mini menu (long-press en un tool) ---
// Card redondeada arriba de la banda. Filas generadas por panelRows():
// Radius + Strength (siempre) + picker HSV (solo Paint, tiene brush->rgb) +
// params extra por tool (Fase 3, verificados contra las capacidades RNA del
// fork: has_color/has_pinch_factor/has_rake_factor/has_normal_weight/has_height,
// has_plane_offset, has_auto_smooth).
// La ventana crece hacia arriba en panelHeight(rows) + PANEL_GAP px.
private const val PANEL_W = 220f
private const val PANEL_GAP = 10f
private const val PANEL_TITLE_Y = 16f
private const val ROW1_Y = 40f   // centro del track del primer slider (step 32)
private const val ROW_STEP = 32f
private const val TRACK_HALF = 9f
private const val RADIUS_MIN = 2
private const val RADIUS_MAX = 150

/** Único brush de sculpt que usa brush->rgb (color) en este fork:
 *  `rna_BrushCapabilitiesSculpt_has_color_get` = ELEM(tool, SCULPT_TOOL_PAINT).
 *  Smear mezcla colores existentes, Draw Face Sets usa color aleatorio y los
 *  Displacement operan el mesh -> NO tienen color picker. */
private val colorTools = setOf("Paint")

private fun panelHasColor(panelIndex: Int): Boolean =
    panelIndex >= 0 && panelIndex < sculptTools.size && sculptTools[panelIndex] in colorTools

/** Campo del Brush (creator.cc switch g_brush_req_param). Sincronizado con C++:
 *  floats id 1..8, enums id 101..106, toggles/bools id 201..204. */
private const val FIELD_AUTOSMOOTH = 1
private const val FIELD_NORMAL_WEIGHT = 2
private const val FIELD_CREASE_PINCH = 3
private const val FIELD_RAKE = 4
private const val FIELD_HEIGHT = 5
private const val FIELD_TIP_ROUNDNESS = 6
private const val FIELD_ELASTIC_PRESERVE = 7
private const val FIELD_PLANE_OFFSET = 8
private const val FIELD_BLEND = 101
private const val FIELD_ELASTIC_DEFORM_TYPE = 102
private const val FIELD_POSE_DEFORM_TYPE = 103
private const val FIELD_POSE_ORIGIN_TYPE = 104
private const val FIELD_CLOTH_DEFORM_TYPE = 105
private const val FIELD_BOUNDARY_DEFORM_TYPE = 106
private const val FIELD_USE_PERSISTENT = 201
private const val FIELD_USE_PRESSURE_AREA_RADIUS = 202
private const val FIELD_INVERT_TO_SCRAPE_FILL = 203
private const val FIELD_USE_SMOOTH_STROKE = 204

private data class ExtraParam(val field: Int, val name: String, val min: Float, val max: Float)
private data class DropdownSpec(val field: Int, val name: String, val options: List<String>)
private data class ToggleSpec(val field: Int, val name: String)

/** Enum value lists, sincronizados con EnumPropertyItem del fork (rna_brush.c). */
private val blendOptions = listOf(
    "Mix", "Darken", "Mul", "Lighten", "Screen", "Add", "Overlay", "Sub",
    "Hue", "Color", "Luminosity", "Erase Alpha", "Add Alpha"
)
private val elasticDeformOptions = listOf("Grab", "Grab Biscale", "Grab Triscale", "Scale", "Twist")
private val poseDeformOptions = listOf("Rotate/Twist", "Scale/Translate", "Squash & Stretch")
private val poseOriginOptions = listOf("Topology", "Face Sets", "Face Sets FK")
private val clothDeformOptions = listOf("Drag", "Push", "Grab", "Pinch Point", "Pinch Perp", "Inflate", "Snake Hook")
private val boundaryDeformOptions = listOf("Bend", "Expand", "Inflate", "Grab", "Twist", "Smooth")

/** Filas del panel: 1=Radius, 2=Strength, 3..5=H/S/V (solo si hasColor),
 *  100+field = slider extra, 200+field = dropdown, 300+field = toggle.
 *  y en px, step ROW_STEP desde ROW1_Y. */
private data class PanelRowSpec(val id: Int, val field: Int, val y: Float)

private fun panelRows(
    hasColor: Boolean,
    floatExtras: List<ExtraParam>,
    dropdowns: List<DropdownSpec>,
    toggles: List<ToggleSpec>
): List<PanelRowSpec> {
    val rows = mutableListOf<PanelRowSpec>()
    var y = ROW1_Y
    rows += PanelRowSpec(1, 0, y); y += ROW_STEP
    rows += PanelRowSpec(2, 0, y); y += ROW_STEP
    if (hasColor) {
        rows += PanelRowSpec(3, 0, y); y += ROW_STEP
        rows += PanelRowSpec(4, 0, y); y += ROW_STEP
        rows += PanelRowSpec(5, 0, y); y += ROW_STEP
    }
    for (e in floatExtras) {
        rows += PanelRowSpec(100 + e.field, e.field, y); y += ROW_STEP
    }
    for (d in dropdowns) {
        rows += PanelRowSpec(200 + d.field, d.field, y); y += ROW_STEP
    }
    for (t in toggles) {
        rows += PanelRowSpec(300 + t.field, t.field, y); y += ROW_STEP
    }
    return rows
}

private fun panelHeight(rows: List<PanelRowSpec>): Float = 24f + rows.size * ROW_STEP + 8f

/** Params extra flotantes por tool (capabilities reales del fork). */
private val toolFloatExtras: Map<String, List<ExtraParam>> = mapOf(
    "Snake Hook" to listOf(
        ExtraParam(FIELD_RAKE, "Rake", 0f, 1f),
        ExtraParam(FIELD_CREASE_PINCH, "Magnify", 0f, 1f),
        ExtraParam(FIELD_NORMAL_WEIGHT, "Nrm Wgt", 0f, 1f)
    ),
    "Crease" to listOf(
        ExtraParam(FIELD_AUTOSMOOTH, "Smooth", 0f, 1f),
        ExtraParam(FIELD_CREASE_PINCH, "Pinch", 0f, 1f)
    ),
    "Blob" to listOf(
        ExtraParam(FIELD_AUTOSMOOTH, "Smooth", 0f, 1f),
        ExtraParam(FIELD_CREASE_PINCH, "Magnify", 0f, 1f)
    ),
    "Layer" to listOf(
        ExtraParam(FIELD_AUTOSMOOTH, "Smooth", 0f, 1f),
        ExtraParam(FIELD_HEIGHT, "Height", 0f, 1f)
    ),
    "Clay Strips" to listOf(
        ExtraParam(FIELD_AUTOSMOOTH, "Smooth", 0f, 1f),
        ExtraParam(FIELD_PLANE_OFFSET, "Offset", -0.5f, 0.5f),
        ExtraParam(FIELD_TIP_ROUNDNESS, "Round", 0f, 1f)
    ),
    "Clay" to listOf(
        ExtraParam(FIELD_AUTOSMOOTH, "Smooth", 0f, 1f),
        ExtraParam(FIELD_PLANE_OFFSET, "Offset", -0.5f, 0.5f)
    ),
    "Clay Thumb" to listOf(
        ExtraParam(FIELD_AUTOSMOOTH, "Smooth", 0f, 1f),
        ExtraParam(FIELD_PLANE_OFFSET, "Offset", -0.5f, 0.5f)
    ),
    "Fill" to listOf(
        ExtraParam(FIELD_AUTOSMOOTH, "Smooth", 0f, 1f),
        ExtraParam(FIELD_PLANE_OFFSET, "Offset", -0.5f, 0.5f)
    ),
    "Flatten" to listOf(
        ExtraParam(FIELD_AUTOSMOOTH, "Smooth", 0f, 1f),
        ExtraParam(FIELD_PLANE_OFFSET, "Offset", -0.5f, 0.5f)
    ),
    "Scrape" to listOf(
        ExtraParam(FIELD_AUTOSMOOTH, "Smooth", 0f, 1f),
        ExtraParam(FIELD_PLANE_OFFSET, "Offset", -0.5f, 0.5f)
    ),
    "Multi-plane Scrape" to listOf(ExtraParam(FIELD_AUTOSMOOTH, "Smooth", 0f, 1f)),
    "Draw" to listOf(ExtraParam(FIELD_AUTOSMOOTH, "Smooth", 0f, 1f)),
    "Draw Sharp" to listOf(ExtraParam(FIELD_AUTOSMOOTH, "Smooth", 0f, 1f)),
    "Inflate" to listOf(ExtraParam(FIELD_AUTOSMOOTH, "Smooth", 0f, 1f)),
    "Pinch" to listOf(ExtraParam(FIELD_AUTOSMOOTH, "Smooth", 0f, 1f)),
    "Nudge" to listOf(ExtraParam(FIELD_AUTOSMOOTH, "Smooth", 0f, 1f)),
    "Rotate" to listOf(ExtraParam(FIELD_AUTOSMOOTH, "Smooth", 0f, 1f)),
    "Slide Relax" to listOf(ExtraParam(FIELD_AUTOSMOOTH, "Smooth", 0f, 1f)),
    "Boundary" to listOf(ExtraParam(FIELD_AUTOSMOOTH, "Smooth", 0f, 1f)),
    "Cloth" to listOf(ExtraParam(FIELD_AUTOSMOOTH, "Smooth", 0f, 1f)),
    "Grab" to listOf(ExtraParam(FIELD_NORMAL_WEIGHT, "Nrm Wgt", 0f, 1f)),
    "Elastic Deform" to listOf(
        ExtraParam(FIELD_NORMAL_WEIGHT, "Nrm Wgt", 0f, 1f),
        ExtraParam(FIELD_ELASTIC_PRESERVE, "Preserve", 0f, 1f)
    )
)

/** Dropdowns (field id 101..106) por tool. */
private val toolDropdowns: Map<String, List<DropdownSpec>> = mapOf(
    "Boundary" to listOf(DropdownSpec(FIELD_BOUNDARY_DEFORM_TYPE, "Deform", boundaryDeformOptions)),
    "Cloth" to listOf(DropdownSpec(FIELD_CLOTH_DEFORM_TYPE, "Deform", clothDeformOptions)),
    "Elastic Deform" to listOf(DropdownSpec(FIELD_ELASTIC_DEFORM_TYPE, "Type", elasticDeformOptions)),
    "Pose" to listOf(
        DropdownSpec(FIELD_POSE_DEFORM_TYPE, "Deform", poseDeformOptions),
        DropdownSpec(FIELD_POSE_ORIGIN_TYPE, "Origin", poseOriginOptions)
    )
)

/** Toggles/bools (field id 201..204) por tool. */
private val toolToggles: Map<String, List<ToggleSpec>> = mapOf(
    "Boundary" to listOf(ToggleSpec(FIELD_USE_PERSISTENT, "Persist")),
    "Cloth" to listOf(ToggleSpec(FIELD_USE_PERSISTENT, "Persist")),
    "Grab" to listOf(ToggleSpec(FIELD_USE_SMOOTH_STROKE, "Smooth Stroke")),
    "Snake Hook" to listOf(ToggleSpec(FIELD_INVERT_TO_SCRAPE_FILL, "Invert")),
    "Pose" to listOf(ToggleSpec(FIELD_USE_SMOOTH_STROKE, "Smooth Stroke"))
)

/** Devuelve las 3 listas (float/dropdown/toggle) del tool visible en `panelIndex`. */
private fun panelControls(panelIndex: Int): Triple<List<ExtraParam>, List<DropdownSpec>, List<ToggleSpec>> {
    if (panelIndex < 0 || panelIndex >= sculptTools.size) return Triple(emptyList(), emptyList(), emptyList())
    val label = sculptTools[panelIndex]
    return Triple(
        toolFloatExtras[label] ?: emptyList(),
        toolDropdowns[label] ?: emptyList(),
        toolToggles[label] ?: emptyList()
    )


/** HSV -> RGB (0..1). h en grados 0..360, s/v 0..1. */
private fun hsvToRgb(h: Float, s: Float, v: Float): Triple<Float, Float, Float> {
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

/** RGB (0..1) -> HSV. h 0..360, s 0..1, v 0..1. */
private fun rgbToHsv(r: Float, g: Float, b: Float): FloatArray {
    val mx = maxOf(r, g, b)
    val mn = minOf(r, g, b)
    val d = mx - mn
    var h = 0f
    if (d > 0f) {
        h = when (mx) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }
        if (h < 0f) h += 360f
    }
    val s = if (mx > 0f) d / mx else 0f
    return floatArrayOf(h, s, mx)
}

/** Color ARGB (para dibujar/preview) desde HSV. */
private fun hsvToColorInt(h: Float, s: Float, v: Float): Int {
    val (r, g, b) = hsvToRgb(h, s, v)
    return android.graphics.Color.rgb((r * 255f).roundToInt(), (g * 255f).roundToInt(), (b * 255f).roundToInt())
}

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
    var clayIcon by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var collapsed by remember { mutableStateOf(OverlayState.sculptArcCollapsed) }
    // Mini menu (long-press): -1 = cerrado; >=0 = índice del tool con el panel abierto.
    var panelIndex by remember { mutableStateOf(-1) }
    var panelRadius by remember { mutableStateOf(RADIUS_MIN) }
    var panelStrength by remember { mutableStateOf(1f) }
    // Fase 2: estado HSV del color del brush (solo se muestra si panelHasColor).
    var panelHue by remember { mutableStateOf(0f) }
    var panelSat by remember { mutableStateOf(1f) }
    var panelVal by remember { mutableStateOf(0.5f) }
    // Fase 3: valores de los params extra por tool (field -> valor actual).
    val panelExtras = remember { androidx.compose.runtime.mutableStateMapOf<Int, Float>() }

    // Posición del chevron (manija de colapsar/expandir): colapsado -> centro de la
    // ventana chica; expandido -> sobre la curvatura superior del arco.
    fun chevronHandleY(h: Float): Float =
        if (collapsed) h / 2f else max(14f, h - (arcH + bandHalf + 24f))

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

     // Altura extra (px) que debe crecer la ventana del overlay para alojar el panel.
     fun panelHeightPx(): Int {
         if (panelIndex < 0) return 0
         val (f, d, t) = panelControls(panelIndex)
         val rows = panelRows(panelHasColor(panelIndex), f, d, t)
         return (panelHeight(rows) + PANEL_GAP).toInt()
     }

    // Aplica el color HSV actual del panel al brush activo (en vivo, como radius/strength).
    fun pushColor() {
        val (r, g, b) = hsvToRgb(panelHue, panelSat, panelVal)
        OBLNativeActivity.setActiveBrushColorStatic(r, g, b)
    }

    // Aplica un param extra (field id) al valor ya guardado en panelExtras.
    fun pushExtra(field: Int) {
        val v = panelExtras[field] ?: return
        OBLNativeActivity.setActiveBrushExtraStatic(field, v)
    }

    // Valor 0..1 de un slider según posición X (rango del param).
    fun sliderValueToFrac(v: Float, min: Float, max: Float): Float =
        ((v - min) / (max - min)).coerceIn(0f, 1f)

    // Carga (una vez, cacheada) el icono de Clay desde res/drawable/clay.xml.
    // Se infla/rasteriza FUERA del hilo principal (el vector tiene 2251 paths; el
    // costo único no debe jankear la UI). Estado reactivo: al terminar, el Canvas
    // se redibuja con el icono.
    LaunchedEffect(Unit) {
        clayIcon = withContext(Dispatchers.Default) { loadClayIcon(context) }
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
                updateSculptArcWindow(collapsed, inSculpt, panelHeightPx())
            }
            delay(200)
        }
    }

    // Mantiene el estado Compose `collapsed` sincronizado con el overlay global y
    // con el tamaño de la ventana (chica si está colapsada, arco completo si no).
    LaunchedEffect(collapsed) {
        OverlayState.sculptArcCollapsed = collapsed
        updateSculptArcWindow(collapsed, OverlayState.sculptArcActive, panelHeightPx())
    }

    // Mini menu: al abrir el panel (long-press), la ventana crece hacia arriba para
    // alojar la card; se re-leen los valores del brush tras activar el tool (el drain
    // tarda unas frames en aplicarlo). Al cerrar, la ventana vuelve al tamaño normal.
    LaunchedEffect(panelIndex) {
        updateSculptArcWindow(collapsed, OverlayState.sculptArcActive, panelHeightPx())
        if (panelIndex >= 0) {
            delay(300)
            if (panelIndex >= 0) {
                panelRadius = OBLNativeActivity.getActiveBrushRadiusStatic()
                panelStrength = OBLNativeActivity.getActiveBrushStrengthStatic()
                if (panelHasColor(panelIndex)) {
                    val c = OBLNativeActivity.getActiveBrushColorStatic()
                    val hsv = rgbToHsv(c[0], c[1], c[2])
                    panelHue = hsv[0]
                    panelSat = hsv[1]
                    panelVal = hsv[2]
                }
                val (f, d, t) = panelControls(panelIndex)
                for (e in f) {
                    panelExtras[e.field] = OBLNativeActivity.getActiveBrushExtraStatic(e.field)
                }
                for (dd in d) {
                    panelExtras[dd.field] = OBLNativeActivity.getActiveBrushExtraStatic(dd.field)
                }
                for (tg in t) {
                    panelExtras[tg.field] = OBLNativeActivity.getActiveBrushExtraStatic(tg.field)
                }
            }
        }
    }

    // Fuera de Sculpting se reinicia el panel para no abrir un menú stale al volver.
    LaunchedEffect(OverlayState.sculptArcActive) {
        if (!OverlayState.sculptArcActive) panelIndex = -1
    }

    // Fuera de Sculpting no se renderiza nada (la ventana ya está GONE + NOT_TOUCHABLE).
    if (!OverlayState.sculptArcActive) return

    // Raíz SIN fondo sólido. Los detectores solo reaccionan con hit-test de banda/esfera;
    // las zonas vacías de la ventana no consumen ni disparan nada.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Tap + long-press con control de slop: el long-press SOLO dispara si el
                // dedo se queda quieto (sin moverse más allá del touchSlop durante 500ms);
                // si se mueve, el drag del carrusel toma el gesto (nada de confusión).
                detectLongPressStill(
                    onTap = onTap@ { pos ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val cx = w / 2f
                        if (collapsed) {
                            // Colapsado: tocar en cualquier lado expande.
                            OverlayState.sculptArcCollapsed = false
                            collapsed = false
                            return@onTap
                        }
                        // Panel abierto: el tap SOLO ajusta un slider o cierra con la X.
                        // Tap fuera de sliders NO cierra (anti-cierre accidental al
                        // mover la barra con el dedo apenas desviado de la fila).
                        if (panelIndex >= 0) {
                            if (isPanelCloseHit(pos.x, pos.y, cx)) {
                                panelIndex = -1
                                return@onTap
                            }
                            val (f, d, t) = panelControls(panelIndex)
                            val rows = panelRows(panelHasColor(panelIndex), f, d, t)
                            val s = sliderHit(pos.x, pos.y, cx, rows)
                            when (s) {
                                1 -> {
                                    panelRadius =
                                        (sliderFrac(pos.x, cx) * (RADIUS_MAX - RADIUS_MIN) + RADIUS_MIN).roundToInt()
                                    OBLNativeActivity.setActiveBrushRadiusStatic(panelRadius)
                                }
                                2 -> {
                                    panelStrength = sliderFrac(pos.x, cx)
                                    OBLNativeActivity.setActiveBrushStrengthStatic(panelStrength)
                                }
                                3 -> {
                                    panelHue = sliderFrac(pos.x, cx) * 360f
                                    pushColor()
                                }
                                4 -> {
                                    panelSat = sliderFrac(pos.x, cx)
                                    pushColor()
                                }
                                5 -> {
                                    panelVal = sliderFrac(pos.x, cx)
                                    pushColor()
                                }
                                else -> {
                                    if (s in 100..199) {
                                        val e = f.firstOrNull { it.field == s - 100 } ?: return@onTap
                                        val v = sliderFrac(pos.x, cx) * (e.max - e.min) + e.min
                                        panelExtras[e.field] = v
                                        pushExtra(e.field)
                                    } else if (s in 200..299) {
                                        val dd = d.firstOrNull { it.field == s - 200 } ?: return@onTap
                                        val cur = (panelExtras[dd.field] ?: 0f).toInt()
                                        val next = (cur + 1) % dd.options.size
                                        panelExtras[dd.field] = next.toFloat()
                                        OBLNativeActivity.setActiveBrushExtraStatic(dd.field, next.toFloat())
                                    } else if (s in 300..399) {
                                        val tg = t.firstOrNull { it.field == s - 300 } ?: return@onTap
                                        val cur = panelExtras[tg.field] ?: 0f
                                        val next = if (cur >= 0.5f) 0f else 1f
                                        panelExtras[tg.field] = next
                                        OBLNativeActivity.setActiveBrushExtraStatic(tg.field, next)
                                    }
                                }
                            }
                            return@onTap
                        }
                        val chevronY = chevronHandleY(h)
                        if (abs(pos.x - cx) <= 40f && abs(pos.y - chevronY) <= 14f) {
                            // Chevron: colapsa la UI.
                            OverlayState.sculptArcCollapsed = true
                            collapsed = true
                            return@onTap
                        }
                        val sel = nearestToolIndex(w, h, pos.x, pos.y)
                        if (sel >= 0) selectTool(sel)
                    },
                    onLongPress = onLongPress@ { pos ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (collapsed) {
                            OverlayState.sculptArcCollapsed = false
                            collapsed = false
                            return@onLongPress
                        }
                        val idx = nearestToolIndex(w, h, pos.x, pos.y)
                        // Fase 1: mini menu SOLO para brushes (tienen radius + strength).
                        if (idx >= 0 && toolId(sculptTools[idx]).startsWith("builtin_brush.")) {
                            selectTool(idx)
                            panelIndex = idx
                            panelRadius = OBLNativeActivity.getActiveBrushRadiusStatic()
                            panelStrength = OBLNativeActivity.getActiveBrushStrengthStatic()
                            android.util.Log.d("OBL.WHEEL", "long-press -> panel tool=${sculptTools[idx]}")
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                var gestureArmed = false
                var dragAccum = 0f
                var onHandle = false
                var handleDelta = 0f
                var panelDrag = 0 // 1 = slider Radius, 2 = slider Strength
                detectDragGestures(
                    onDragStart = { start ->
                        gestureArmed = false
                        dragAccum = 0f
                        onHandle = false
                        handleDelta = 0f
                        panelDrag = 0
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val cx = w / 2f
                        if (collapsed) {
                            // Colapsado: cualquier drag hacia arriba expande.
                            OverlayState.sculptArcCollapsed = false
                            collapsed = false
                            return@detectDragGestures
                        }
                        // Panel abierto: el drag SOLO ajusta un slider o cierra con la X.
                        // Drag fuera de sliders NO cierra (antes lo cerraba y el drag del
                        // carrusel con el dedo apenas desviado del track cerraba el menú).
                        if (panelIndex >= 0) {
                            if (isPanelCloseHit(start.x, start.y, cx)) {
                                panelIndex = -1
                                return@detectDragGestures
                            }
                            val (f, d, t) = panelControls(panelIndex)
                            val rows = panelRows(panelHasColor(panelIndex), f, d, t)
                            val s = sliderHit(start.x, start.y, cx, rows)
                            if (s == 1 || s == 2 || (s in 3..5) || (s in 100..199)) {
                                panelDrag = s
                            }
                            return@detectDragGestures
                        }
                        val chevronY = chevronHandleY(h)
                        if (abs(start.x - cx) <= 40f && abs(start.y - chevronY) <= 14f) {
                            onHandle = true
                            return@detectDragGestures
                        }
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
                        if (panelDrag != 0) {
                            panelDrag = 0
                            return@detectDragGestures
                        }
                        if (onHandle) {
                            val threshold = 24f
                            if (handleDelta > threshold) {
                                OverlayState.sculptArcCollapsed = true
                                collapsed = true
                            } else if (handleDelta < -threshold) {
                                OverlayState.sculptArcCollapsed = false
                                collapsed = false
                            }
                        } else if (gestureArmed && dragAccum >= 12f) {
                            // Drag real: snap al tool más cercano al ápice (solo navega).
                            recenterTo(nearestToApexIndex())
                        }
                        // Tap / micro-movimiento: la selección la hace SOLO onTap.
                        // (antes onDragEnd seleccionaba el tool del ápice y pisaba el tool
                        //  que el usuario acababa de tocar -> "se teletransporta")
                        gestureArmed = false
                        onHandle = false
                        highlightIndex = -1
                    },
                    onDragCancel = {
                        gestureArmed = false
                        onHandle = false
                        panelDrag = 0
                        highlightIndex = -1
                    },
                    onDrag = { change, dragAmount ->
                        if (panelDrag != 0) {
                            // Aplicación EN VIVO del slider mientras se arrastra.
                            change.consume()
                            val frac = sliderFrac(change.position.x, size.width / 2f)
                            when (panelDrag) {
                                1 -> {
                                    val v = (frac * (RADIUS_MAX - RADIUS_MIN) + RADIUS_MIN).roundToInt()
                                    if (v != panelRadius) {
                                        panelRadius = v
                                        OBLNativeActivity.setActiveBrushRadiusStatic(v)
                                    }
                                }
                                2 -> {
                                    val v = frac
                                    if (abs(v - panelStrength) > 0.005f) {
                                        panelStrength = v
                                        OBLNativeActivity.setActiveBrushStrengthStatic(v)
                                    }
                                }
                                3 -> {
                                    val v = frac * 360f
                                    if (abs(v - panelHue) > 1f) {
                                        panelHue = v
                                        pushColor()
                                    }
                                }
                                4 -> {
                                    val v = frac
                                    if (abs(v - panelSat) > 0.005f) {
                                        panelSat = v
                                        pushColor()
                                    }
                                }
                                5 -> {
                                    val v = frac
                                    if (abs(v - panelVal) > 0.005f) {
                                        panelVal = v
                                        pushColor()
                                    }
                                }
                                    else -> {
                                        if (panelDrag in 100..199) {
                                            val (f, _, _) = panelControls(panelIndex)
                                            val e = f.firstOrNull { it.field == panelDrag - 100 }
                                                ?: return@detectDragGestures
                                            val v = frac * (e.max - e.min) + e.min
                                            if (abs(v - (panelExtras[e.field] ?: v)) > 0.002f * (e.max - e.min)) {
                                                panelExtras[e.field] = v
                                                pushExtra(e.field)
                                            }
                                        }
                                    }
                            }
                            return@detectDragGestures
                        }
                        if (!gestureArmed && !onHandle) return@detectDragGestures
                        change.consume()
                        if (onHandle) {
                            handleDelta += dragAmount.y
                            return@detectDragGestures
                        }
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

            // Colapsado: solo se dibuja el chevron (apuntando hacia arriba = expandir).
            if (collapsed) {
                drawChevron(cx, h / 2f, collapsed = true)
                return@Canvas
            }

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
            drawPath(bandPath, Color(0xFF3A3A3A).copy(alpha = 0.90f))
            drawPath(bandPath, Color(0xFFFFC107), style = Stroke(width = 3f))

            // Esferas de herramientas en carousel, con la distribución parabólica.
            // Mismo filtro de visibilidad que el hit-test (visibleIndices).
            val clayBmp = clayIcon
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

            // Chevron (manija de colapsar) en la curvatura superior del arco.
            // Oculto mientras el mini menu está abierto (la card ocupa esa zona).
            if (panelIndex < 0) {
                drawChevron(cx, chevronHandleY(h.toFloat()), collapsed = false)
            } else {
                // Mini menu: card redondeada arriba del arco (la ventana creció hacia
                // arriba, la card se dibuja en el top del canvas).
                val (pf, pd, pt) = panelControls(panelIndex)
                drawBrushPanel(
                    cx = cx,
                    label = sculptTools[panelIndex],
                    radius = panelRadius,
                    strength = panelStrength,
                    hasColor = panelHasColor(panelIndex),
                    hue = panelHue,
                    sat = panelSat,
                    value = panelVal,
                    floatExtras = pf,
                    dropdowns = pd,
                    toggles = pt,
                    extraValues = panelExtras
                )
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
    clayBmp: android.graphics.Bitmap?
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

    // Icono del tool Clay (clay.xml): SI hay bitmap, el icono ES toda la esfera (no se
    // dibuja la esfera gris atrás -> solo icono + contorno de selección). Si no hay
    // bitmap (todavía cargando), se cae a la esfera arcilla normal.
    val useClayIcon = label == "Clay" && clayBmp != null
    if (useClayIcon) {
        val iconSize = r * 2f * 1.06f
        val native = drawContext.canvas.nativeCanvas
        native.save()
        native.clipPath(
            android.graphics.Path().apply {
                addCircle(cx, cy, r, android.graphics.Path.Direction.CW)
            }
        )
        native.drawBitmap(
            clayBmp,
            android.graphics.Rect(0, 0, clayBmp.width, clayBmp.height),
            android.graphics.RectF(
                cx - iconSize / 2f, cy - iconSize / 2f,
                cx + iconSize / 2f, cy + iconSize / 2f
            ),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        )
        native.restore()
    } else {
        // Esfera arcilla: gradiente radial, luz arriba-izquierda, sombra abajo-derecha.
        val brush = Brush.radialGradient(
            colors = listOf(Color(0xFFE0E0E0), Color(0xFF9E9E9E), Color(0xFF404040)),
            center = Offset(cx - r * 0.35f, cy - r * 0.4f),
            radius = r * 1.25f
        )
        drawCircle(brush, r, Offset(cx, cy))
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

/** Tamaño de la ventana COLAPSADA (px): solo el chevron/manija. */
fun collapsedWheelWindowSize(): Pair<Int, Int> = 112 to 60

/** Hit-test del botón X (cerrar el panel), esquina superior derecha de la card. */
private fun isPanelCloseHit(x: Float, y: Float, cx: Float): Boolean =
    hypot(x - (cx + PANEL_W / 2f - 16f), y - 16f) <= 18f

/** Hit-test de los sliders del panel según sus filas: devuelve el id de la fila
 *  (1=Radius, 2=Strength, 3..5=H/S/V, 100+field=extra) o 0 si no cae en ninguna. */
private fun sliderHit(x: Float, y: Float, cx: Float, rows: List<PanelRowSpec>): Int {
    val left = cx - PANEL_W / 2f + 12f
    val right = cx + PANEL_W / 2f - 12f
    if (x < left || x > right) return 0
    for (r in rows) {
        if (abs(y - r.y) <= TRACK_HALF + 4f) return r.id
    }
    return 0
}

/** Fracción 0..1 del slider según la posición X (para calcular el valor). */
private fun sliderFrac(x: Float, cx: Float): Float {
    val left = cx - PANEL_W / 2f + 12f
    val right = cx + PANEL_W / 2f - 12f
    return ((x - left) / (right - left)).coerceIn(0f, 1f)
}

/** Detector de tap + long-press con control de slop: el long-press SOLO dispara si el
 *  dedo permanece quieto (dentro del touchSlop) durante holdTimeMillis. Si se mueve,
 *  no hace nada (el drag del carrusel toma el gesto). */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectLongPressStill(
    holdTimeMillis: Long = 500L,
    onTap: (Offset) -> Unit,
    onLongPress: (Offset) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val downPos = down.position
        val slop = viewConfiguration.touchSlop
        var gesture = 0 // 1=tap, 2=movió (drag), 3=consumido/cancelado
        val ended = withTimeoutOrNull(holdTimeMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                // Multi-touch (2 dedos: zoom/órbita) cancela el long-press.
                if (event.changes.count { it.pressed } > 1) { gesture = 3; break }
                val primary = event.changes.firstOrNull { it.id == down.id }
                if (primary == null) { gesture = 2; break }
                if (primary.isConsumed) { gesture = 3; break }
                if (!primary.pressed) { gesture = 1; break }
                if ((primary.position - downPos).getDistance() > slop) { gesture = 2; break }
            }
            Unit
        }
        if (ended == null) {
            // Se quedó quieto el tiempo suficiente -> long press. Consume hasta el UP
            // para que el drag del carrusel no dispare después.
            onLongPress(downPos)
            while (true) {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) break
            }
        } else if (gesture == 1) {
            down.consume()
            onTap(downPos)
        }
        // gesture 2/3: el drag (o un consumo ajeno) se llevó el gesto -> no hacer nada.
    }
}

/** Mini menu: card redondeada arriba del arco con los sliders/toggles/dropdowns del brush.
 *  Filas = panelRows(hasColor, floatExtras, dropdowns, toggles): Radius + Strength siempre,
 *  + HSV si hasColor (solo Paint), + params extra por tool (Fase 3). Aplicación en vivo
 *  al mover/tap (el setter JNI solo stash; el drain del render thread aplica al brush activo). */
private fun DrawScope.drawBrushPanel(
    cx: Float,
    label: String,
    radius: Int,
    strength: Float,
    hasColor: Boolean,
    hue: Float,
    sat: Float,
    value: Float,
    floatExtras: List<ExtraParam>,
    dropdowns: List<DropdownSpec>,
    toggles: List<ToggleSpec>,
    extraValues: Map<Int, Float>
) {
    val rows = panelRows(hasColor, floatExtras, dropdowns, toggles)
    val panelH = panelHeight(rows)
    val left = cx - PANEL_W / 2f
    drawRoundRect(
        color = Color(0xE62B2B2B),
        topLeft = Offset(left, 0f),
        size = Size(PANEL_W, panelH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f)
    )
    drawRoundRect(
        color = Color(0xFFCC6F03),
        topLeft = Offset(left, 0f),
        size = Size(PANEL_W, panelH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f),
        style = Stroke(width = 2f)
    )
    // Título (truncado para nombres largos, sin chocar con la X ni el swatch).
    val title = if (label.length > 18) label.take(17) + "…" else label
    drawContext.canvas.nativeCanvas.drawText(
        title,
        cx,
        PANEL_TITLE_Y,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(0xD0, 0xD0, 0xD0)
            textSize = 15f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
    )
    if (hasColor) {
        // Swatch de preview del color actual (arriba-izquierda del título).
        val cur = hsvToColorInt(hue, sat, value)
        drawRoundRect(
            color = Color(cur),
            topLeft = Offset(cx - PANEL_W / 2f + 12f, PANEL_TITLE_Y - 11f),
            size = Size(18f, 18f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
        )
        drawRoundRect(
            color = Color(0xFFCC6F03),
            topLeft = Offset(cx - PANEL_W / 2f + 12f, PANEL_TITLE_Y - 11f),
            size = Size(18f, 18f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
            style = Stroke(width = 1.5f)
        )
    }
    // Botón X (cerrar el panel): esquina superior derecha.
    val xc = cx + PANEL_W / 2f - 16f
    val yc = 16f
    drawCircle(
        color = Color(0xFF3A3A3A),
        radius = 10f,
        center = Offset(xc, yc)
    )
    drawCircle(
        color = Color(0xFFCC6F03),
        radius = 10f,
        center = Offset(xc, yc),
        style = Stroke(width = 1.5f)
    )
    drawContext.canvas.nativeCanvas.drawLine(
        xc - 4.5f, yc - 4.5f, xc + 4.5f, yc + 4.5f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(0xD0, 0xD0, 0xD0)
            strokeWidth = 2f
            strokeCap = Paint.Cap.ROUND
        }
    )
    drawContext.canvas.nativeCanvas.drawLine(
        xc - 4.5f, yc + 4.5f, xc + 4.5f, yc - 4.5f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(0xD0, 0xD0, 0xD0)
            strokeWidth = 2f
            strokeCap = Paint.Cap.ROUND
        }
    )
    val cur = hsvToColorInt(hue, sat, value)
    for (r in rows) {
        when (r.id) {
            1 -> drawSlider(
                cx, r.y, "Radius", radius.toString(),
                (radius - RADIUS_MIN).toFloat() / (RADIUS_MAX - RADIUS_MIN)
            )
            2 -> drawSlider(cx, r.y, "Strength", String.format("%.2f", strength), strength.coerceIn(0f, 1f))
            3 -> drawSlider(
                cx, r.y, "Hue", String.format("%.0f", hue), hue / 360f,
                track = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
                        Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000)
                    )
                )
            )
            4 -> drawSlider(
                cx, r.y, "Sat", String.format("%d%%", (sat * 100f).roundToInt()), sat,
                track = Brush.linearGradient(colors = listOf(Color(0xFFFFFFFF), Color(cur)))
            )
            5 -> drawSlider(
                cx, r.y, "Val", String.format("%d%%", (value * 100f).roundToInt()), value,
                track = Brush.linearGradient(colors = listOf(Color(0xFF000000), Color(cur)))
            )
            else -> {
                if (r.id in 100..199) {
                    val e = floatExtras.firstOrNull { it.field == r.id - 100 } ?: continue
                    val ev = extraValues[e.field] ?: e.min
                    val frac = ((ev - e.min) / (e.max - e.min)).coerceIn(0f, 1f)
                    drawSlider(cx, r.y, e.name, String.format("%.2f", ev), frac)
                } else if (r.id in 200..299) {
                    val dd = dropdowns.firstOrNull { it.field == r.id - 200 } ?: continue
                    drawDropdown(cx, r.y, dd.name, dd.options,
                        (extraValues[dd.field] ?: 0f).toInt())
                } else if (r.id in 300..399) {
                    val tg = toggles.firstOrNull { it.field == r.id - 300 } ?: continue
                    val on = (extraValues[tg.field] ?: 0f) >= 0.5f
                    drawToggle(cx, r.y, tg.name, on)
                }
            }
        }
    }
}

private fun DrawScope.drawSlider(
    cx: Float,
    y: Float,
    name: String,
    valueText: String,
    frac: Float,
    track: Brush? = null
) {
    val left = cx - PANEL_W / 2f + 12f
    val right = cx + PANEL_W / 2f - 12f
    val w = right - left
    drawContext.canvas.nativeCanvas.drawText(
        name,
        left,
        y - 12f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(0xD0, 0xD0, 0xD0)
            textSize = 12f
            textAlign = Paint.Align.LEFT
        }
    )
    drawContext.canvas.nativeCanvas.drawText(
        valueText,
        right,
        y - 12f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(0xFF, 0xC1, 0x07)
            textSize = 12f
            textAlign = Paint.Align.RIGHT
        }
    )
    if (track != null) {
        // Slider de color: track con gradiente completo (sin relleno ámbar).
        drawRoundRect(
            brush = track,
            topLeft = Offset(left, y - TRACK_HALF),
            size = Size(w, TRACK_HALF * 2f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(TRACK_HALF)
        )
    } else {
        drawRoundRect(
            color = Color(0xFF3A3A3A),
            topLeft = Offset(left, y - TRACK_HALF),
            size = Size(w, TRACK_HALF * 2f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(TRACK_HALF)
        )
        drawRoundRect(
            color = Color(0xFFFFC107),
            topLeft = Offset(left, y - TRACK_HALF),
            size = Size(w * frac.coerceIn(0f, 1f), TRACK_HALF * 2f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(TRACK_HALF)
        )
    }
    drawCircle(
        color = Color(0xFFFFF8E1),
        radius = 7f,
        center = Offset(left + w * frac.coerceIn(0f, 1f), y)
    )
}

/** Dropdown (tap para ciclar): label a la izquierda, opción actual centrada en un "chip"
 *  gris/dorado a la derecha. Hit area ancho toda la row (sliderHit lo resuelve por y). */
private fun DrawScope.drawDropdown(
    cx: Float,
    y: Float,
    name: String,
    options: List<String>,
    selected: Int
) {
    val left = cx - PANEL_W / 2f + 12f
    val right = cx + PANEL_W / 2f - 12f
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(0xD0, 0xD0, 0xD0)
        textSize = 12f
        textAlign = Paint.Align.LEFT
    }
    drawContext.canvas.nativeCanvas.drawText(name, left, y - 12f, p)
    val label = options.getOrElse(selected) { "?" }
    // Chip
    val chipW = 72f
    val chipL = right - chipW
    drawRoundRect(
        color = Color(0xFF3A3A3A),
        topLeft = Offset(chipL, y - TRACK_HALF),
        size = Size(chipW, TRACK_HALF * 2f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(TRACK_HALF)
    )
    drawRoundRect(
        color = Color(0xFFFFC107),
        topLeft = Offset(chipL, y - TRACK_HALF),
        size = Size(chipW, TRACK_HALF * 2f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(TRACK_HALF),
        style = Stroke(width = 1.5f)
    )
    drawContext.canvas.nativeCanvas.drawText(
        label,
        right - 6f, y - 12f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(0xFF, 0xC1, 0x07)
            textSize = 12f
            textAlign = Paint.Align.RIGHT
        }
    )
}

/** Toggle (switch): label a la izquierda, mini-switch gris/dorado a la derecha. */
private fun DrawScope.drawToggle(cx: Float, y: Float, name: String, on: Boolean) {
    val left = cx - PANEL_W / 2f + 12f
    drawContext.canvas.nativeCanvas.drawText(
        name, left, y - 12f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(0xD0, 0xD0, 0xD0)
            textSize = 12f
            textAlign = Paint.Align.LEFT
        }
    )
    val right = cx + PANEL_W / 2f - 12f
    val swW = 36f
    val swL = right - swW
    val bg = if (on) 0xFFFFC107 else 0xFF555555
    drawRoundRect(
        color = Color(bg),
        topLeft = Offset(swL, y - TRACK_HALF),
        size = Size(swW, TRACK_HALF * 2f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(TRACK_HALF)
    )
    val knobX = if (on) right - 4f else swL + 4f
    drawCircle(color = Color(0xFFFFF8E1), radius = TRACK_HALF + 1f, center = Offset(knobX, y))
}

/** Chevron amarillo con contorno ámbar, sobre pill oscura. Es la manija que cuelga de
 *  la curvatura superior del arco: colapsado apunta hacia ARRIBA (expandir), expandido
 *  hacia ABAJO (colapsar). */
private fun DrawScope.drawChevron(cx: Float, cy: Float, collapsed: Boolean) {
    val w = 52f
    val h = 26f
    val left = cx - w / 2f
    val top = cy - h / 2f
    drawRoundRect(
        color = Color(0xCC1B1B1B),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f)
    )
    drawRoundRect(
        color = Color(0xFFCC6F03),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f),
        style = Stroke(width = 2f)
    )
    val r = 8f
    val path = Path().apply {
        if (collapsed) {
            moveTo(cx - r, cy + r * 0.5f)
            lineTo(cx, cy - r * 0.5f)
            lineTo(cx + r, cy + r * 0.5f)
        } else {
            moveTo(cx - r, cy - r * 0.5f)
            lineTo(cx, cy + r * 0.5f)
            lineTo(cx + r, cy - r * 0.5f)
        }
        close()
    }
    drawPath(path, Color(0xFFFFC107))
    drawPath(path, Color(0xFFCC6F03), style = Stroke(width = 2f))
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
