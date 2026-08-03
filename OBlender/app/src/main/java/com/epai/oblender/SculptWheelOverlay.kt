package com.epai.oblender

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
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

/** idname real de Blender (builtin_brush.<Label_con_underscores>) para el puente JNI
 *  WM_toolsystem_ref_set_by_id (drain en creator.cc / mainBlenderLoop). */
private fun toolId(label: String) = "builtin_brush." + label.replace(" ", "_")

private const val ANGLE_SPACING = 18f   // grados por tool en el carrusel
private const val VISIBLE_SPAN = 4.5f   // tools visibles a cada lado del centro
private const val DOCK_H_DP = 44f       // altura del dock "bridge"
private const val LABEL_H_DP = 28f      // label del tool activo + margen

/** Geometría del dock (dp). Única fuente de verdad: coincide con wheelWindowSize(px)
 *  para que la ventana del overlay no recorte el carrusel ni deje área muerta. */
private fun wheelMetrics(context: Context): WheelGeometry {
    val sw = context.resources.displayMetrics.widthPixels.toFloat()
    val density = context.resources.displayMetrics.density
    val screenWd = sw / density
    val radius = screenWd * 0.36f
    val sphere = screenWd * 0.10f
    return WheelGeometry(
        radius = radius,
        sphere = sphere,
        boxW = 2 * radius + sphere,
        boxH = radius + sphere + DOCK_H_DP
    )
}

private data class WheelGeometry(val radius: Float, val sphere: Float, val boxW: Float, val boxH: Float)

/**
 * Carrusel de herramientas de Sculpt ("dock" flotante bottom-center).
 *
 * 1) TRANSPARENCIA: el Box raíz NO tiene .background() opaco; solo se pinta el dock
 *    "bridge" (Path) y las esferas. Fuera de ellos, la ventana es 100% transparente.
 * 2) TOUCH PASSTHROUGH: la ventana (WindowManager) se redimensiona a wheelWindowSize()
 *    = solo el área del dock, anclada BOTTOM|CENTER_HORIZONTAL con FLAG_NOT_TOUCH_MODAL.
 *    Todo toque FUERA de esa ventana cae directamente en el GLSurfaceView de Blender
 *    (esculpir/rotar/dibujar sin interferencias). Cuando el dock está oculto la ventana
 *    pasa a GONE + FLAG_NOT_TOUCHABLE.
 * 3) VISIBILIDAD: solo se muestra si Blender está en modo SCULPT dentro del workspace
 *    "Sculpting" (poll de modo/workspace, igual que el arco anterior).
 * 4) ACCIONES: el tap en una esfera anima el centrado Y llama oblSetToolByIdStatic(id)
 *    -> JNI oblSetToolById -> oblSetSculptToolRequest -> WM_toolsystem_ref_set_by_id
 *    en el hilo de render (activación real del tool de sculpt).
 */
@Composable
fun CarouselDock() {
    val density = LocalDensity.current
    val context = LocalContext.current
    val m = wheelMetrics(context)
    val radius = m.radius.dp
    val sphere = m.sphere.dp
    val boxW = m.boxW.dp
    val boxH = m.boxH.dp

    val radiusPx = with(density) { radius.toPx() }
    val spherePx = with(density) { sphere.toPx() }
    val boxWpx = with(density) { boxW.toPx() }
    val halfSphere = spherePx / 2f
    val cx = boxWpx / 2f
    val cy0 = spherePx + radiusPx   // centro del arco; ápice activo en y = spherePx

    val totalTools = sculptTools.size.toFloat()

    val dragAngle = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    val selectedIndex = ((dragAngle.value / ANGLE_SPACING).roundToInt() %
            sculptTools.size + sculptTools.size) % sculptTools.size

    var activeSel by remember { mutableStateOf(-1) }
    var lastUserSelMs by remember { mutableStateOf(0L) }

    // Poll: solo muestro el dock en Sculpting (modo + workspace); sincroniza el
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
                    coroutineScope.launch {
                        dragAngle.animateTo(
                            targetValue = idx * ANGLE_SPACING,
                            animationSpec = spring(stiffness = 300f, dampingRatio = 0.8f)
                        )
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

    // Fuera de Sculpting: no se renderiza nada (la ventana ya está GONE + NOT_TOUCHABLE).
    if (!OverlayState.sculptArcActive) return

    // Raíz SIN fondo sólido (requisito 1): la ventana está recortada al dock
    // (wheelWindowSize), así los toques fuera del carrusel pasan a Blender (req. 2).
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = sculptTools[selectedIndex],
                color = if (activeSel >= 0) Color(0xFFFFAB40) else Color(0xFFA0A0A0),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .size(boxW, boxH)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val target = (dragAngle.value / ANGLE_SPACING).roundToInt()
                                coroutineScope.launch {
                                    dragAngle.animateTo(
                                        targetValue = target * ANGLE_SPACING,
                                        animationSpec = spring(stiffness = 300f, dampingRatio = 0.8f)
                                    )
                                }
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                dragAngle.snapTo(dragAngle.value - dragAmount * 0.20f)
                            }
                        }
                    }
            ) {
                val goldOrangeGradient = Brush.linearGradient(
                    colors = listOf(Color(0xFFE5C158), Color(0xFFFF6F00))
                )
                val interactionSource = remember { MutableInteractionSource() }
                val centerPos = dragAngle.value / ANGLE_SPACING

                sculptTools.forEachIndexed { index, label ->
                    val diffUnwrapped = index - centerPos
                    var diff = diffUnwrapped % totalTools
                    if (diff > totalTools / 2f) diff -= totalTools
                    if (diff < -totalTools / 2f) diff += totalTools
                    if (abs(diff) <= VISIBLE_SPAN) {
                        val ang = Math.toRadians((diff * ANGLE_SPACING - 90f).toDouble())
                        val cosA = cos(ang).toFloat()
                        val sinA = sin(ang).toFloat()
                        val dist = abs(diff * ANGLE_SPACING)
                        val itemAlpha = (1f - (dist / 75f)).coerceIn(0f, 1f)
                        val itemScale = (1f - (dist / 130f)).coerceIn(0.5f, 1f)
                        val isSelected = abs(diff) < 0.5f
                        val sx = (cx + cosA * radiusPx - halfSphere).roundToInt()
                        val sy = (cy0 + sinA * radiusPx - halfSphere).roundToInt()
                        val id = toolId(label)
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(sx, sy) }
                                .size(sphere)
                                .clickable(interactionSource = interactionSource, indication = null) {
                                    android.util.Log.d("OBL.WHEEL", "select label=$label id=$id")
                                    // Req. 4: activación real del tool en Blender (JNI).
                                    OBLNativeActivity.oblSetToolByIdStatic(id)
                                    lastUserSelMs = System.currentTimeMillis()
                                    val cur = dragAngle.value / ANGLE_SPACING
                                    var delta = index - cur
                                    delta = ((delta + totalTools / 2f) % totalTools) - totalTools / 2f
                                    coroutineScope.launch {
                                        dragAngle.animateTo(
                                            dragAngle.value + delta * ANGLE_SPACING,
                                            spring(stiffness = 300f)
                                        )
                                    }
                                }
                                .graphicsLayer {
                                    alpha = itemAlpha
                                    scaleX = itemScale
                                    scaleY = itemScale
                                }
                                .background(
                                    brush = if (isSelected) goldOrangeGradient else Brush.linearGradient(listOf(Color(0xFF3B3B3B), Color(0xFF3B3B3B))),
                                    shape = CircleShape
                                )
                                .padding(if (isSelected) 3.dp else 2.dp)
                                .background(Color(0xFF2C2C2C), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            ToolIcon(label)
                        }
                    }
                }

                // Único elemento opaco del overlay: el dock "bridge" anclado al borde
                // inferior de la ventana (fuera de él todo es transparente).
                Canvas(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(DOCK_H_DP.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val dock = Path().apply {
                        moveTo(0f, h)
                        cubicTo(w * 0.20f, h * 0.35f, w * 0.80f, h * 0.35f, w, h)
                        lineTo(w, h)
                        close()
                    }
                    drawPath(dock, Color(0xFF2C2C2C))
                    drawPath(dock, Color(0xFFE5C158), style = Stroke(2.5f))
                }
            }
        }
    }
}

@Composable
private fun ToolIcon(label: String) {
    // Esfera "arcilla" 3D para toda tool (sombra + gradiente radial).
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val center = Offset(w * 0.5f, h * 0.5f)
        drawCircle(color = Color(0xFF181818), radius = w * 0.41f, center = Offset(center.x, center.y + w * 0.03f))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFCECECE), Color(0xFF808080), Color(0xFF424242)),
                center = Offset(center.x - w * 0.12f, center.y - h * 0.12f),
                radius = w * 0.5f
            ),
            radius = w * 0.39f,
            center = center
        )
    }
    // Icono 3D esculpido / iniciales, centrado sobre la esfera.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (label) {
            "Draw" -> Canvas(modifier = Modifier.size(24.dp)) {
                val w = size.width; val h = size.height
                val domeGrad = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFB0B0B0), Color(0xFF424242)),
                    center = Offset(w * 0.38f, h * 0.35f),
                    radius = w * 0.5f
                )
                drawOval(Color(0xFF111111), topLeft = Offset(w * 0.15f, h * 0.65f), size = Size(w * 0.7f, h * 0.15f))
                val path = Path().apply {
                    moveTo(w * 0.20f, h * 0.70f)
                    cubicTo(w * 0.20f, h * 0.30f, w * 0.80f, h * 0.30f, w * 0.80f, h * 0.70f)
                    close()
                }
                drawPath(path, domeGrad)
            }
            "Draw Sharp" -> Canvas(modifier = Modifier.size(24.dp)) {
                val w = size.width; val h = size.height
                val bladeGrad = Brush.linearGradient(
                    colors = listOf(Color(0xFFF0F0F0), Color(0xFF808080), Color(0xFF2A2A2A)),
                    start = Offset(w * 0.2f, h * 0.8f),
                    end = Offset(w * 0.8f, h * 0.2f)
                )
                val path = Path().apply {
                    moveTo(w * 0.22f, h * 0.78f)
                    lineTo(w * 0.72f, h * 0.28f)
                    lineTo(w * 0.78f, h * 0.38f)
                    lineTo(w * 0.28f, h * 0.88f)
                    close()
                }
                drawPath(path, bladeGrad)
                drawCircle(Color(0xFFFFB300), radius = w * 0.12f, center = Offset(w * 0.72f, h * 0.28f))
            }
            "Clay" -> Canvas(modifier = Modifier.size(24.dp)) {
                val w = size.width; val h = size.height
                val clayGrad = Brush.radialGradient(
                    colors = listOf(Color(0xFFE8E8E8), Color(0xFF949494), Color(0xFF383838)),
                    center = Offset(w * 0.35f, h * 0.35f),
                    radius = w * 0.48f
                )
                drawCircle(Color(0xFF151515), radius = w * 0.34f, center = Offset(w * 0.5f, h * 0.55f))
                drawCircle(clayGrad, radius = w * 0.33f, center = Offset(w * 0.5f, h * 0.5f))
            }
            "Clay Strips" -> Canvas(modifier = Modifier.size(24.dp)) {
                val w = size.width; val h = size.height
                val stripGrad = Brush.linearGradient(
                    colors = listOf(Color(0xFFE0E0E0), Color(0xFF707070)),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
                drawRect(Color(0xFF151515), topLeft = Offset(w * 0.20f, h * 0.27f), size = Size(w * 0.62f, h * 0.22f))
                drawRect(stripGrad, topLeft = Offset(w * 0.18f, h * 0.23f), size = Size(w * 0.60f, h * 0.22f))
                drawRect(Color(0xFF151515), topLeft = Offset(w * 0.20f, h * 0.57f), size = Size(w * 0.62f, h * 0.22f))
                drawRect(stripGrad, topLeft = Offset(w * 0.18f, h * 0.53f), size = Size(w * 0.60f, h * 0.22f))
            }
            "Inflate" -> Canvas(modifier = Modifier.size(24.dp)) {
                val w = size.width; val h = size.height
                val sphereGrad = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFF9E9E9E), Color(0xFF333333)),
                    center = Offset(w * 0.35f, h * 0.35f),
                    radius = w * 0.3f
                )
                drawCircle(sphereGrad, radius = w * 0.24f, center = Offset(w * 0.5f, h * 0.5f))
                drawCircle(Color(0xFFE0E0E0), radius = w * 0.38f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(width = w * 0.08f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
            }
            "Crease" -> Canvas(modifier = Modifier.size(24.dp)) {
                val w = size.width; val h = size.height
                val path = Path().apply {
                    moveTo(w * 0.20f, h * 0.30f)
                    lineTo(w * 0.50f, h * 0.75f)
                    lineTo(w * 0.80f, h * 0.30f)
                }
                drawPath(path, Color(0xFF101010), style = Stroke(width = w * 0.22f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, Color(0xFFE0E0E0), style = Stroke(width = w * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            "Smooth" -> Canvas(modifier = Modifier.size(24.dp)) {
                val w = size.width; val h = size.height
                val smoothGrad = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFF888888)),
                    start = Offset(w * 0.2f, h * 0.6f),
                    end = Offset(w * 0.8f, h * 0.4f)
                )
                val path = Path().apply {
                    moveTo(w * 0.18f, h * 0.55f)
                    cubicTo(w * 0.35f, h * 0.25f, w * 0.65f, h * 0.75f, w * 0.82f, h * 0.45f)
                }
                drawPath(path, smoothGrad, style = Stroke(width = w * 0.18f, cap = StrokeCap.Round))
            }
            "Flatten" -> Canvas(modifier = Modifier.size(24.dp)) {
                val w = size.width; val h = size.height
                val plateGrad = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFF757575), Color(0xFF303030))
                )
                drawRect(plateGrad, topLeft = Offset(w * 0.18f, h * 0.32f), size = Size(w * 0.64f, h * 0.20f))
                drawRect(Color(0xFFAAAAAA), topLeft = Offset(w * 0.22f, h * 0.64f), size = Size(w * 0.56f, h * 0.08f))
            }
            "Grab" -> Canvas(modifier = Modifier.size(24.dp)) {
                val w = size.width; val h = size.height
                val grabGrad = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFF808080))
                )
                val path = Path().apply {
                    moveTo(w * 0.20f, h * 0.5f)
                    lineTo(w * 0.80f, h * 0.5f)
                    moveTo(w * 0.5f, h * 0.20f)
                    lineTo(w * 0.5f, h * 0.80f)
                }
                drawPath(path, grabGrad, style = Stroke(width = w * 0.16f, cap = StrokeCap.Round))
                drawCircle(Color(0xFFFFB300), radius = w * 0.14f, center = Offset(w * 0.5f, h * 0.5f))
            }
            "Mask" -> Canvas(modifier = Modifier.size(24.dp)) {
                val w = size.width; val h = size.height
                drawRect(Color(0xFF333333), topLeft = Offset(w * 0.22f, h * 0.22f), size = Size(w * 0.56f, h * 0.56f))
                val maskBorder = Path().apply {
                    addRect(Rect(w * 0.22f, h * 0.22f, w * 0.78f, h * 0.78f))
                }
                drawPath(maskBorder, Color(0xFFE0E0E0), style = Stroke(width = w * 0.12f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
            }
            else -> Text(
                text = label.split(" ").let { parts ->
                    if (parts.size > 1) "${parts[0].take(1)}${parts[1].take(1)}" else parts[0].take(2)
                }.uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp
            )
        }
    }
}

/** Tamaño de la ventana del overlay (px), anclada BOTTOM|CENTER_HORIZONTAL.
 *  Recortada al dock: los toques fuera de ella llegan a Blender (passthrough). */
fun wheelWindowSize(context: Context): Pair<Int, Int> {
    val m = wheelMetrics(context)
    val density = context.resources.displayMetrics.density
    val w = ((m.boxW + 6) * density).toInt()
    val h = ((LABEL_H_DP + m.boxH + 6) * density).toInt()
    return w to h
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
