package com.epai.oblender

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Blender eContextObjectMode: CTX_MODE_SCULPT (BKE_context.h). */
const val CTX_MODE_SCULPT = 9

/**
 * Sculpt tool arc overlay.
 *
 * A 180° semicircle anchored at the bottom of the screen, opened upward. The
 * ACTIVE sculpt tool sits at the apex (top center). The 11 sculpt tools are
 * arranged around the arc in a carousel centered on the active tool; the user
 * presses and drags toward a tool and releases to switch to it.
 *
 * The overlay is FLAG_NOT_TOUCHABLE: touches pass through to the GL surface and
 * GHOST receives them. GHOST suppresses strokes that start inside the arc band
 * (GetAsyncKeyState(102)) so holding on the arc never draws. The finger position
 * comes from the GHOST cursor position (== finger position in touch mode).
 *
 * A small chevron at the apex is the hide/show handle: swipe it DOWN to collapse
 * the arc to just the handle, swipe UP to expand again.
 */
@Composable
fun SculptArcContent() {
    val windowSize = LocalWindowInfo.current.containerSize
    val screenW = windowSize.width.coerceAtLeast(1)
    val screenH = windowSize.height.coerceAtLeast(1)

    var activeIndex by remember { mutableStateOf(-1) }
    var highlightIndex by remember { mutableStateOf(-1) }
    var collapsed by remember { mutableStateOf(false) }
    var logCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        var lastDown = false
        var gestureArmed = false
        var gestureStartY = 0f
        var gestureLastY = 0f
        var onHandle = false

        while (isActive) {
            val toolId = OBLNativeActivity.getActiveToolIdStatic()
            val mode = OBLNativeActivity.getActiveModeStatic()
            val workspace = OBLNativeActivity.getActiveWorkspaceStatic()
            // The sculpt arc is assigned to the "Sculpting" workspace: it must
            // disappear when switching to "General", "2D Animation", etc. (the
            // object mode alone is NOT enough — switching workspaces does not
            // change the active object's mode).
            val inSculptWorkspace = workspace.startsWith("Sculpting")
            val idx = if (mode == CTX_MODE_SCULPT && inSculptWorkspace)
                SculptTools.indexOfFirst { it.id == toolId } else -1
            val real = OBLNativeActivity.getCursorPositionStatic()
            val x = if (real != null && real.size == 2) real[0] else -1
            val y = if (real != null && real.size == 2) real[1] else -1
            val down = OBLNativeActivity.getTouchDownStatic()

            // Arc geometry — MUST match sculpt_arc_hit_test() in GHOST_SystemAndroid.cc.
            // Flattened half-ellipse ("parenthesis"), wider and flatter.
            val minWh = min(screenW, screenH).toFloat()
            val Rx = minWh * 0.75f
            val Ry = minWh * 0.10f
            val bandHalf = max(28f, screenW * 0.03f)
            val arrowHole = max(30f, screenW * 0.04f)
            val cx = screenW * 0.5f
            val cy = screenH.toFloat()
            val apexX = cx
            val apexY = cy - Ry
            val handleY = apexY + 80f

            val inSculpt = idx >= 0
            OverlayState.sculptArcActive = inSculpt
            if (!inSculpt) {
                OverlayState.sculptArcCollapsed = false
                collapsed = false
                highlightIndex = -1
                gestureArmed = false
                lastDown = down
                if (logCount++ % 200 == 0) {
                    android.util.Log.d("OBL.ARC", "poll: not in sculpt (tool=$toolId mode=$mode ws=$workspace)")
                }
                delay(16)
                continue
            }
            collapsed = OverlayState.sculptArcCollapsed
            activeIndex = idx

            val distToHandle = if (x >= 0 && y >= 0) hypot(x - apexX, y - handleY) else 1e9f

            if (down && !lastDown) {
                // DOWN: arm the gesture. Handle gesture if the finger lands on the chevron.
                gestureArmed = true
                onHandle = distToHandle <= arrowHole
                gestureStartY = y.toFloat()
                gestureLastY = y.toFloat()
            } else if (!down && lastDown && gestureArmed) {
                // UP: commit the gesture.
                if (onHandle) {
                    val swipe = gestureLastY - gestureStartY
                    val threshold = max(24f, arrowHole * 0.6f)
                    if (collapsed) {
                        // Swipe UP (negative y delta) expands.
                        if (gestureLastY < gestureStartY - threshold) {
                            OverlayState.sculptArcCollapsed = false
                            android.util.Log.d("OBL.ARC", "handle: expand")
                        }
                    } else {
                        // Swipe DOWN (positive y delta) collapses.
                        if (gestureLastY > gestureStartY + threshold) {
                            OverlayState.sculptArcCollapsed = true
                            android.util.Log.d("OBL.ARC", "handle: collapse")
                        }
                    }
                } else if (!collapsed) {
                    val sel = highlightIndex
                    if (sel >= 0 && sel != idx) {
                        val tool = SculptTools[sel]
                        android.util.Log.d(
                            "OBL.ARC",
                            "select ${tool.id} key=${tool.key} shift=${tool.shift}"
                        )
                        emitToolKey(tool)
                    }
                }
                gestureArmed = false
                onHandle = false
                highlightIndex = -1
            }

            if (down && gestureArmed) {
                if (onHandle) {
                    gestureLastY = y.toFloat()
                } else if (!collapsed && x >= 0 && y >= 0) {
                    // Finger angle around the ellipse → nearest tool slot.
                    val dx = x - cx
                    val dy = cy - y
                    val nx = dx / Rx
                    val ny = dy / Ry
                    val distN = hypot(nx, ny)
                    val bandN = bandHalf / Ry
                    if (y <= cy && distN >= (1f - bandN) && distN <= (1f + bandN)) {
                        val angDeg = Math.toDegrees(Math.atan2(nx.toDouble(), ny.toDouble())).toFloat()
                        val step = 180f / (SculptTools.size - 1)
                        var off = (angDeg / step).roundToInt().coerceIn(-5, 5)
                        var hi = Math.floorMod(idx + off, SculptTools.size)
                        if (hi < 0) hi += SculptTools.size
                        highlightIndex = hi
                    } else {
                        highlightIndex = -1
                    }
                }
            }

            if (logCount++ % 100 == 0) {
                android.util.Log.d(
                    "OBL.ARC",
                    "DEBUG: idx=$idx tool=$toolId mode=$mode ws=$workspace active=$inSculpt"
                )
            }

            lastDown = down
            delay(16)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (activeIndex >= 0) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val minWh = min(size.width, size.height).toFloat()
                val Rx = minWh * 0.75f
                val Ry = minWh * 0.10f
                val bandHalf = max(28f, size.width * 0.03f)
                val cx = size.width / 2f
                val cy = size.height
                val apexX = cx
                val apexY = cy - Ry
                val handleY = apexY + 80f
                val step = 180f / (SculptTools.size - 1)

                // Arc band hint.
                if (!collapsed) {
                    val bandPath = Path()
                    val outerX = Rx + bandHalf
                    val outerY = Ry + bandHalf
                    val innerX = Rx - bandHalf
                    val innerY = Ry - bandHalf
                    for (i in 0..180) {
                        val a = Math.toRadians((i - 90).toDouble()).toFloat()
                        val px = cx + outerX * sin(a)
                        val py = cy - outerY * cos(a)
                        if (i == 0) bandPath.moveTo(px, py) else bandPath.lineTo(px, py)
                    }
                    for (i in 180 downTo 0) {
                        val a = Math.toRadians((i - 90).toDouble()).toFloat()
                        val px = cx + innerX * sin(a)
                        val py = cy - innerY * cos(a)
                        bandPath.lineTo(px, py)
                    }
                    bandPath.close()
                    drawPath(
                        path = bandPath,
                        color = Color(0xFF1B1B1B).copy(alpha = 0.60f)
                    )
                }

                // Tool slots in carousel order centered on the active tool.
                if (!collapsed) {
                    for (off in -5..5) {
                        val t = Math.floorMod(activeIndex + off, SculptTools.size)
                        val angRad = Math.toRadians((off * step).toDouble()).toFloat()
                        val tx = cx + Rx * sin(angRad)
                        val ty = (cy - Ry * cos(angRad)).coerceAtMost(cy - 80f)
                        val isActive = off == 0
                        val isHighlight = t == highlightIndex
                        drawToolSlot(tx, ty, SculptTools[t].label, isActive, isHighlight)
                    }
                }

                // Hide/show chevron at the handle position.
                drawChevron(apexX, handleY, collapsed)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawToolSlot(
    cx: Float,
    cy: Float,
    label: String,
    isActive: Boolean,
    isHighlight: Boolean
) {
    val r = if (isActive) 46f else 38f
    val highlighted = isActive || isHighlight

    // Amber/orange glow behind the active (or being-selected) tool.
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

    // Clay sphere: radial gradient, highlight top-left, shadow bottom-right.
    val brush = Brush.radialGradient(
        colors = listOf(Color(0xFFE0E0E0), Color(0xFF9E9E9E), Color(0xFF404040)),
        center = Offset(cx - r * 0.35f, cy - r * 0.4f),
        radius = r * 1.25f
    )
    drawCircle(brush, r, Offset(cx, cy))

    // Label below the sphere.
    drawContext.canvas.nativeCanvas.drawText(
        label,
        cx,
        cy + r + 18f,
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = if (highlighted) {
                android.graphics.Color.rgb(0xFF, 0xC1, 0x07)
            } else {
                android.graphics.Color.rgb(0xD0, 0xD0, 0xD0)
            }
            textSize = if (isActive) 15f else 13f
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = isActive || isHighlight
        }
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChevron(cx: Float, cy: Float, collapsed: Boolean) {
    val w = 48f
    val h = 24f
    val left = cx - w / 2f
    val top = cy - h / 2f
    drawRoundRect(
        color = Color(0xCC1B1B1B),
        topLeft = Offset(left, top),
        size = androidx.compose.ui.geometry.Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f)
    )
    val r = 7f
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
}

private fun emitToolKey(tool: SculptTool) {
    if (tool.shift) {
        OBLNativeActivity.oblSetValueOnStatic("0") // Shift down
        OBLNativeActivity.oblSetValueStatic(tool.key.toString())
        OBLNativeActivity.oblSetValueOffStatic("0") // Shift up
    } else {
        OBLNativeActivity.oblSetValueStatic(tool.key.toString())
    }
}

data class SculptTool(val id: String, val label: String, val key: Int, val shift: Boolean = false)

/** The 11 sculpt tools in carousel order. key = OBLButtonID ordinal (see OBLButtonID.h). */
val SculptTools = listOf(
    SculptTool("builtin_brush.Draw", "Draw", 27),
    SculptTool("builtin_brush.Clay", "Clay", 29),
    SculptTool("builtin_brush.Smooth", "Smooth", 44, shift = true),
    SculptTool("builtin_brush.Flatten", "Flatten", 22, shift = true),
    SculptTool("builtin_brush.Pinch", "Pinch", 73),
    SculptTool("builtin_brush.Grab", "Grab", 45),
    SculptTool("builtin_brush.Snake Hook", "Snake Hook", 71),
    SculptTool("builtin_brush.Inflate", "Inflate", 23),
    SculptTool("builtin_brush.Layer", "Layer", 72),
    SculptTool("builtin_brush.Crease", "Crease", 29, shift = true),
    SculptTool("builtin_brush.Mask", "Mask", 31)
)

fun createSculptArcOverlay(context: android.content.Context, lifecycleOwner: LifecycleOwner): ComposeView {
    android.util.Log.d("OBL", "createSculptArcOverlay: start")
    val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            SculptArcContent()
        }
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    composeView.setViewTreeLifecycleOwner(lifecycleOwner)
    composeView.setViewTreeSavedStateRegistryOwner(SimpleSavedStateRegistryOwner())

    val wm = context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
    val lp = WindowManager.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        android.graphics.PixelFormat.TRANSPARENT
    )

    Handler(Looper.getMainLooper()).post {
        try {
            android.util.Log.d("OBL", "createSculptArcOverlay: adding to WindowManager on UI thread")
            wm.addView(composeView, lp)
            android.util.Log.d("OBL", "createSculptArcOverlay: added successfully")
        } catch (e: Exception) {
            android.util.Log.e("OBL", "createSculptArcOverlay: ADD FAILED", e)
        }
    }

    return composeView
}
