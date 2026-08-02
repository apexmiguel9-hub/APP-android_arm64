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
            val idx = if (mode == CTX_MODE_SCULPT) SculptTools.indexOfFirst { it.id == toolId } else -1
            val real = OBLNativeActivity.getCursorPositionStatic()
            val x = if (real != null && real.size == 2) real[0] else -1
            val y = if (real != null && real.size == 2) real[1] else -1
            val down = OBLNativeActivity.getTouchDownStatic()

            // Arc geometry — MUST match sculpt_arc_hit_test() in GHOST_SystemAndroid.cc.
            // Flattened half-ellipse ("parenthesis"), wider than tall.
            val minWh = min(screenW, screenH).toFloat()
            val Rx = minWh * 0.40f
            val Ry = minWh * 0.18f
            val bandHalf = max(28f, screenW * 0.03f)
            val arrowHole = max(30f, screenW * 0.04f)
            val cx = screenW * 0.5f
            val cy = screenH.toFloat()
            val apexX = cx
            val apexY = cy - Ry
            val handleY = apexY + arrowHole * 0.8f

            val inSculpt = idx >= 0
            OverlayState.sculptArcActive = inSculpt
            if (!inSculpt) {
                OverlayState.sculptArcCollapsed = false
                collapsed = false
                highlightIndex = -1
                gestureArmed = false
                lastDown = down
                if (logCount++ % 200 == 0) {
                    android.util.Log.d("OBL.ARC", "poll: not in sculpt (tool=$toolId)")
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

            if (logCount++ % 200 == 0) {
                android.util.Log.d(
                    "OBL.ARC",
                    "poll idx=$idx tool=$toolId down=$down x=$x y=$y " +
                        "highlight=$highlightIndex collapsed=$collapsed active=$inSculpt"
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
                val Rx = minWh * 0.40f
                val Ry = minWh * 0.18f
                val bandHalf = max(28f, size.width * 0.03f)
                val cx = size.width / 2f
                val cy = size.height
                val apexX = cx
                val apexY = cy - Ry
                val handleY = apexY + max(30f, size.width * 0.04f) * 0.8f
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
                        color = Color(0xFF3E4B61).copy(alpha = 0.30f)
                    )
                }

                // Tool slots in carousel order centered on the active tool.
                if (!collapsed) {
                    for (off in -5..5) {
                        val t = Math.floorMod(activeIndex + off, SculptTools.size)
                        val angRad = Math.toRadians((off * step).toDouble()).toFloat()
                        val tx = cx + Rx * sin(angRad)
                        val ty = (cy - Ry * cos(angRad)).coerceAtMost(cy - 24f)
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
    val w = if (isActive) 96f else 80f
    val h = if (isActive) 44f else 38f
    val bg = when {
        isHighlight -> Color(0xFF4FC3F7)
        isActive -> Color(0xFF3E8EF7)
        else -> Color(0xE6202A36)
    }
    val border = when {
        isHighlight -> Color(0xFFFFFFFF)
        isActive -> Color(0xFF9CC9FF)
        else -> Color(0x55AEBFD4)
    }
    val left = cx - w / 2f
    val top = cy - h / 2f
    drawRoundRect(
        color = bg,
        topLeft = Offset(left, top),
        size = androidx.compose.ui.geometry.Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f)
    )
    drawRoundRect(
        color = border,
        topLeft = Offset(left, top),
        size = androidx.compose.ui.geometry.Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f),
        style = Stroke(width = 2f)
    )
    drawContext.canvas.nativeCanvas.drawText(
        label,
        cx,
        cy + 4f,
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = if (isActive) 15f else 13f
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = isActive || isHighlight
        }
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChevron(cx: Float, cy: Float, collapsed: Boolean) {
    val w = 44f
    val h = 22f
    val left = cx - w / 2f
    val top = cy - h / 2f
    drawRoundRect(
        color = Color(0xE6202A36),
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
    drawPath(path, Color(0xFFAEBFD4))
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
