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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

data class WheelTool(val label: String, val id: String = "builtin_brush." + label.replace(" ", "_"))

val WheelTools = listOf(
    WheelTool("Draw"), WheelTool("Draw Sharp"), WheelTool("Clay"), WheelTool("Clay Strips"),
    WheelTool("Clay Tubes"), WheelTool("Layer"), WheelTool("Inflate"), WheelTool("Blob"),
    WheelTool("Crease"), WheelTool("Smooth"), WheelTool("Flatten"), WheelTool("Fill"),
    WheelTool("Scrape"), WheelTool("Pinch"), WheelTool("Grab"), WheelTool("Snake Hook"),
    WheelTool("Thumb"), WheelTool("Pose"), WheelTool("Nudge"), WheelTool("Rotate"),
    WheelTool("Slide Relax"), WheelTool("Boundary"), WheelTool("Cloth"), WheelTool("Simplify"),
    WheelTool("Mask"), WheelTool("Face Set"), WheelTool("Mesh Filter"), WheelTool("Color Filter"),
    WheelTool("Box Mask"), WheelTool("Lasso Mask"), WheelTool("Line Mask"), WheelTool("Poly Mask"),
    WheelTool("Box Trim"), WheelTool("Lasso Trim"), WheelTool("Slice"), WheelTool("Box Hide"),
    WheelTool("Lasso Hide"), WheelTool("Line Project"), WheelTool("Expand"), WheelTool("Sample Detail"),
    WheelTool("Multires Displacement"), WheelTool("Sculpt Curves")
)

private const val STEP = 20f

/** Single source of truth for wheel size (dp). Used by the Compose layout AND wheelWindowSize
 * so the overlay window and its content always agree and never clip. */
private fun wheelMetrics(context: Context): Triple<Float, Float, Float> {
    val sw = context.resources.displayMetrics.widthPixels.toFloat()
    val density = context.resources.displayMetrics.density
    val screenWd = sw / density
    val radius = screenWd * 0.44f
    val sphere = screenWd * 0.10f
    val box = 2 * radius + sphere
    val boxH = radius + sphere
    return Triple(radius, sphere, box)
}

@Composable
fun SculptWheelContent() {
    val density = LocalDensity.current
    val context = LocalContext.current
    val metrics = wheelMetrics(context)
    val radiusDp = metrics.first
    val sphereDp = metrics.second
    val boxDp = metrics.third
    val radius = radiusDp.dp
    val sphere = sphereDp.dp

    val angleSpacing = 18f
    val totalTools = WheelTools.size.toFloat()

    val boxWpx = with(density) { boxDp.dp.toPx() }
    val radiusPx = with(density) { radius.toPx() }
    val spherePx = with(density) { sphere.toPx() }
    // Lower-semicircle: circle center near the top of the box, apex (active) at the
    // box BOTTOM = screen bottom (bottom-center dock). centerY = sphere so the arc
    // rises from the apex up into the box without clipping.
    val cx = boxWpx / 2f
    val cy = spherePx
    val halfSphere = spherePx / 2f

    var collapsed by remember { mutableStateOf(OverlayState.sculptArcCollapsed) }
    var activeSel by remember { mutableStateOf(-1) }
    var lastUserSelMs by remember { mutableStateOf(0L) }

    val coroutineScope = rememberCoroutineScope()
    val dragAngle = remember { Animatable(0f) }

    // Poll: show/hide wheel based on sculpt mode + sync active highlight from Blender.
    LaunchedEffect(Unit) {
        var lastIdx = -2
        var lastActive = OverlayState.sculptArcActive
        while (true) {
            val toolId = OBLNativeActivity.getActiveToolIdStatic()
            val mode = OBLNativeActivity.getActiveModeStatic()
            val workspace = OBLNativeActivity.getActiveWorkspaceStatic()
            val inSculptWorkspace = workspace.startsWith("Sculpting")
            val idx = if (mode == CTX_MODE_SCULPT && inSculptWorkspace)
                WheelTools.indexOfFirst { it.id == toolId } else -1
            val inSculpt = idx >= 0
            OverlayState.sculptArcActive = inSculpt
            if (inSculpt) {
                if (idx != activeSel && (System.currentTimeMillis() - lastUserSelMs) > 600) {
                    activeSel = idx
                }
                if (idx != lastIdx) {
                    lastIdx = idx
                    coroutineScope.launch { dragAngle.animateTo(-(idx * STEP), spring(stiffness = 300f, dampingRatio = 0.8f)) }
                }
            } else {
                lastIdx = -2
                activeSel = -1
            }
            if (inSculpt != lastActive) {
                lastActive = inSculpt
                updateSculptArcWindow(collapsed, inSculpt)
            }
            delay(200)
        }
    }

    // Only render the wheel when in sculpt mode (sculptArcActive is observable state).
    if (!OverlayState.sculptArcActive) {
        Box(Modifier.fillMaxSize())
        return
    }

    // Root: fill the bottom-anchored overlay window; dock the wheel at bottom-center.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC202124)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val centerPos = dragAngle.value / STEP
            val apexIdx = ((centerPos.roundToInt() % WheelTools.size) + WheelTools.size) % WheelTools.size
            val toolAtApex = WheelTools[apexIdx]
            Text(
                text = toolAtApex.label,
                color = if (activeSel >= 0) Color(0xFFFFAB40) else Color(0xFFA0A0A0),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Container sized to fully hold the lower semicircle (no clipping).
            val boxHdp = (radiusDp + sphereDp).dp
            Box(
                modifier = Modifier
                    .size(boxDp.dp, boxHdp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val cur = dragAngle.value / STEP
                                val target = cur.roundToInt()
                                val targetAngle = target * STEP
                                coroutineScope.launch {
                                    dragAngle.animateTo(targetValue = targetAngle, animationSpec = spring(stiffness = 300f, dampingRatio = 0.8f))
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
                val centerPosVal = dragAngle.value / STEP

                WheelTools.forEachIndexed { index, tool ->
                    val diffUnwrapped = index - centerPosVal
                    var diff = diffUnwrapped % totalTools
                    if (diff > totalTools / 2f) diff -= totalTools
                    if (diff < -totalTools / 2f) diff += totalTools
                    if (abs(diff) <= 4.5f) {
                        // angle (radians): 0 = apex (bottom), +/- to the sides.
                        val angle = Math.toRadians((diff * angleSpacing).toDouble())
                        val sinA = sin(angle).toFloat()
                        val cosA = cos(angle).toFloat()
                        val distFromApex = abs(diff * angleSpacing)
                        val itemAlpha = (1f - (distFromApex / 75f)).coerceIn(0f, 1f)
                        val itemScale = (1f - (distFromApex / 130f)).coerceIn(0.5f, 1f)
                        val isSelected = distFromApex < (angleSpacing / 2f)
                        val sx = (cx + sinA * radiusPx - halfSphere).roundToInt()
                        val sy = (cy + cosA * radiusPx - halfSphere).roundToInt()
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(sx, sy) }
                                .size(sphere)
                                .clickable(interactionSource = interactionSource, indication = null) {
                                    android.util.Log.d("OBL.WHEEL", "select label=${tool.label} id=${tool.id}")
                                    OBLNativeActivity.oblSetToolByIdStatic(tool.id)
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
                            wheelToolIcon(tool)
                        }
                    }
                }

                // Dock baseline band.
                Canvas(modifier = Modifier.align(Alignment.BottomCenter)) {
                    val w = size.width
                    val h = size.height
                    drawPath(
                        Path().apply {
                            moveTo(0f, h)
                            cubicTo(w * 0.25f, h - 10f, w * 0.75f, h + 10f, w, h)
                            lineTo(w, h)
                            close()
                        },
                        Color(0xFF2C2C2C)
                    )
                    drawPath(
                        Path().apply {
                            moveTo(0f, h)
                            cubicTo(w * 0.25f, h - 10f, w * 0.75f, h + 10f, w, h)
                            lineTo(w, h)
                            close()
                        },
                        Color(0xFFE5C158),
                        style = Stroke(2.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun wheelToolIcon(tool: WheelTool) {
    val label = tool.label
    // 3D "clay" sphere (drawn behind).
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
    // Icon / initials centered above the sphere.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (label) {
            "Draw" -> Canvas(modifier = Modifier.size(24.dp)) {
                val w = size.width; val h = size.height
                val path = Path().apply {
                    moveTo(w * 0.25f, h * 0.65f)
                    cubicTo(w * 0.35f, h * 0.30f, w * 0.65f, h * 0.30f, w * 0.75f, h * 0.65f)
                }
                drawPath(path, Color.White, style = Stroke(width = w * 0.14f, cap = StrokeCap.Round))
            }
            "Draw Sharp", "Crease" -> Canvas(modifier = Modifier.size(24.dp)) {
                val w = size.width; val h = size.height
                val path = Path().apply {
                    moveTo(w * 0.25f, h * 0.35f)
                    lineTo(w * 0.50f, h * 0.70f)
                    lineTo(w * 0.75f, h * 0.35f)
                }
                drawPath(path, Color.White, style = Stroke(width = w * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            "Smooth" -> Canvas(modifier = Modifier.size(24.dp)) {
                val w = size.width; val h = size.height
                val path = Path().apply {
                    moveTo(w * 0.20f, h * 0.50f)
                    quadraticBezierTo(w * 0.38f, h * 0.30f, w * 0.50f, h * 0.50f)
                    quadraticBezierTo(w * 0.62f, h * 0.70f, w * 0.80f, h * 0.50f)
                }
                drawPath(path, Color.White, style = Stroke(width = w * 0.12f, cap = StrokeCap.Round))
            }
            else -> Text(
                text = label.split(" ").let { parts ->
                    if (parts.size > 1) "${parts[0].take(1)}${parts[1].take(1)}" else parts[0].take(2)
                }.uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

fun wheelWindowSize(context: Context): Pair<Int, Int> {
    val metrics = wheelMetrics(context)
    val density = context.resources.displayMetrics.density
    val box = metrics.third
    val boxH = metrics.first + metrics.second
    val w = ((box + 16) * density).toInt()
    val h = ((boxH + 72) * density).toInt()
    return w to h
}

fun createSculptWheelOverlay(context: Context, lifecycleOwner: LifecycleOwner): ComposeView {
    android.util.Log.d("OBL", "createSculptWheelOverlay: start")
    val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent { SculptWheelContent() }
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        // Start hidden + not-touchable; the poll reveals it only in sculpt mode.
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
