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
import androidx.compose.ui.geometry.Size
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Blender eContextObjectMode: CTX_MODE_SCULPT (BKE_context.h). */
const val CTX_MODE_SCULPT = 9

/**
 * Sculpt tool arc overlay (Curved Bottom Carousel, per Gemini spec).
 *
 * A wide, shallow "bridge" parabola anchored at the bottom-center of the screen
 * (60% of the screen width, apex at cy - arcH). The ACTIVE sculpt tool sits at
 * the apex and is highlighted (scale 1.1); the neighboring tools fan out along
 * the curve (scale down to 0.8). Only the 7 tools around the current center are
 * rendered (culling), and only the centered tool is "selected".
 *
 * Interaction model:
 *  - Horizontal drag rotates the carousel; on release it snaps (animated) so
 *    the nearest tool ends up exactly at the apex.
 *  - Tap on a tool selects it: the key is emitted and the carousel animates to
 *    center that tool at the apex.
 *  - Swiping the chevron handle DOWN collapses the arc to just the handle;
 *    swiping UP expands it.
 *
 * The overlay is FLAG_NOT_TOUCHABLE: touches pass through to the GL surface and
 * GHOST receives them. GHOST suppresses strokes that start inside the arc band
 * (GetAsyncKeyState(102)) so holding on the arc never draws. The finger position
 * comes from the GHOST cursor position (== finger position in touch mode).
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
        var lastX = 0f
        var lastY = 0f
        var dragAccum = 0f
        var onHandle = false
        var snapActive = false
        var snapFrom = 0f
        var snapTarget = 0f
        var snapT = 0f

        // Arc geometry — MUST match sculpt_arc_hit_test() in GHOST_SystemAndroid.cc.
        // "Bridge" parabola (quadratic bezier, per Gemini spec): apex at cy-arcH,
        // base at cy, total width = 60% of screen, contained at bottom-center.
        val step = 20f
        val halfW = screenW * 0.30f
        val arcH = screenW * 0.16f
        val bandHalf = max(28f, screenW * 0.03f)
        val arrowHole = max(30f, screenW * 0.04f)
        val cx = screenW * 0.5f
        val cy = screenH.toFloat()
        val apexX = cx
        val apexY = cy - arcH
        val handleY = apexY + 90f

        fun nearestToolIndex(px: Float, py: Float): Int {
            var best = -1
            var bestD = 1e9f
            for (i in SculptTools.indices) {
                val a = (i * step) + OverlayState.scrollOffset
                val angRad = Math.toRadians(a.toDouble()).toFloat()
                val tx = cx + halfW * sin(angRad)
                val ty = cy - arcH * cos(angRad) * cos(angRad)
                val d = hypot(px - tx, py - ty)
                if (d < bestD) { bestD = d; best = i }
            }
            return best
        }

        fun nearestToApexIndex(): Int {
            var best = -1
            var bestD = 1e9f
            for (i in SculptTools.indices) {
                val a = (i * step) + OverlayState.scrollOffset
                val d = abs(((a % 180f) + 180f) % 180f)
                if (d < bestD) { bestD = d; best = i }
            }
            return best
        }

        fun startSnap(target: Float) {
            snapFrom = OverlayState.scrollOffset
            snapTarget = target
            snapT = 0f
            snapActive = true
        }

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
                // DOWN: arm the gesture only if the finger lands on the band or the chevron.
                // If it lands elsewhere, we don't arm and the touch passes to Blender
                // (brush stroke / sculpting keeps working).
                gestureArmed = false
                onHandle = distToHandle <= arrowHole * 2f
                // Parabola band test (same as sculpt_arc_hit_test in C++): solve
                // theta from x, compare y distance to the curve.
                val sx = if (x >= 0) (x - cx) / halfW else 2f
                val inBand = if (x >= 0 && y >= 0 && sx >= -1f && sx <= 1f) {
                    val theta = Math.asin(sx.coerceIn(-1f, 1f).toDouble()).toFloat()
                    val curveY = cy - arcH * cos(theta) * cos(theta)
                    abs(y - curveY) <= bandHalf * 2f
                } else false
                android.util.Log.d(
                    "OBL.ARC",
                    "down x=$x y=$y inBand=$inBand onHandle=$onHandle"
                )
                if (inBand || onHandle) {
                    gestureArmed = true
                    gestureStartY = y.toFloat()
                    gestureLastY = y.toFloat()
                    lastX = x.toFloat()
                    lastY = y.toFloat()
                    dragAccum = 0f
                    if (!onHandle) {
                        // Nearest tool by screen position (tap selects it).
                        val best = nearestToolIndex(x.toFloat(), y.toFloat())
                        highlightIndex = best
                    }
                }
            } else if (down && gestureArmed) {
                // MOVE: rotate the carousel by horizontal finger delta (degrees per px).
                if (!onHandle && !collapsed && x >= 0 && y >= 0) {
                    val degPerPx = 90f / halfW
                    val delta = (x - lastX) * degPerPx
                    if (abs(delta) < 60f) {
                        OverlayState.scrollOffset += delta
                        dragAccum += abs(delta)
                    }
                    lastX = x.toFloat()
                    lastY = y.toFloat()

                    // Nearest tool to the apex (offset 0) → highlight.
                    highlightIndex = nearestToApexIndex()
                }
                if (onHandle) {
                    gestureLastY = y.toFloat()
                }
            } else if (!down && lastDown && gestureArmed) {
                // UP: commit the gesture.
                if (onHandle) {
                    val swipe = gestureLastY - gestureStartY
                    val threshold = max(24f, arrowHole * 0.6f)
                    if (collapsed) {
                        if (gestureLastY < gestureStartY - threshold) {
                            OverlayState.sculptArcCollapsed = false
                            android.util.Log.d("OBL.ARC", "handle: expand")
                        }
                    } else {
                        if (gestureLastY > gestureStartY + threshold) {
                            OverlayState.sculptArcCollapsed = true
                            android.util.Log.d("OBL.ARC", "handle: collapse")
                        }
                    }
                } else if (!collapsed) {
                    // If it was essentially a tap (little horizontal movement), select.
                    if (dragAccum < 12f) {
                        val sel = highlightIndex
                        if (sel >= 0) {
                            val tool = SculptTools[sel]
                            android.util.Log.d(
                                "OBL.ARC",
                                "select ${tool.id} key=${tool.key} shift=${tool.shift}"
                            )
                            // Select the tool AND center it at the apex (animated snap).
                            if (sel != idx) emitToolKey(tool)
                            startSnap(-(sel * step).toFloat())
                        }
                    } else {
                        // It was a drag: snap so the nearest tool sits at the apex.
                        val best = nearestToApexIndex()
                        if (best >= 0) {
                            startSnap(-(best * step).toFloat())
                        }
                    }
                }
                gestureArmed = false
                onHandle = false
                highlightIndex = -1
            }

            // Animated snap: ease scrollOffset toward the target so the centered
            // tool ends up exactly at the apex.
            if (snapActive) {
                snapT += 0.12f
                if (snapT >= 1f) {
                    OverlayState.scrollOffset = snapTarget
                    snapActive = false
                } else {
                    val eased = 1f - (1f - snapT) * (1f - snapT) * (1f - snapT)
                    OverlayState.scrollOffset = snapFrom + (snapTarget - snapFrom) * eased
                }
            }

            // If the gesture isn't armed, we don't touch the scroll at all:
            // the touch belongs to Blender (sculpting). No early-continue here
            // so the state flags below still update every frame.
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
        if (OverlayState.sculptArcActive) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Geometry — MUST match sculpt_arc_hit_test() in GHOST_SystemAndroid.cc.
                val halfW = size.width * 0.30f
                val arcH = size.width * 0.16f
                val cx = size.width / 2f
                val cy = size.height.toFloat()
                val apexX = cx
                val apexY = cy - arcH
                val handleY = apexY + 90f

                // Background "bridge" band (parabola) anchored at bottom-center.
                if (!collapsed) {
                    val bandPath = Path()
                    val bandHalf = max(28f, size.width * 0.03f)
                    // Parabola y(x) = cy - arcH * cos^2(theta), theta in [-90,90].
                    val n = 120
                    for (i in 0..n) {
                        val theta = -90f + 180f * i / n
                        val a = Math.toRadians(theta.toDouble()).toFloat()
                        val px = cx + (halfW + bandHalf) * sin(a)
                        val py = cy - (arcH + bandHalf) * cos(a) * cos(a)
                        if (i == 0) bandPath.moveTo(px, py) else bandPath.lineTo(px, py)
                    }
                    for (i in n downTo 0) {
                        val theta = -90f + 180f * i / n
                        val a = Math.toRadians(theta.toDouble()).toFloat()
                        val px = cx + (halfW - bandHalf) * sin(a)
                        val py = cy - (arcH - bandHalf) * cos(a) * cos(a)
                        bandPath.lineTo(px, py)
                    }
                    bandPath.close()
                    drawPath(
                        path = bandPath,
                        color = Color(0xFF1B1B1B).copy(alpha = 0.60f)
                    )
                }

                // Tool slots in carousel order. Each tool sits at
                // angleDeg = (i * step) + scrollOffset (step = 20°). Only the 7
                // tools around the current center are drawn (culling per spec).
                if (!collapsed) {
                    val step = 20f
                    val activeId = OBLNativeActivity.getActiveToolIdStatic()
                    val centerIdx = Math.round(-OverlayState.scrollOffset / step).toInt()
                    val lo = (centerIdx - 3).coerceAtLeast(0)
                    val hi = (centerIdx + 3).coerceAtMost(SculptTools.size - 1)
                    for (i in lo..hi) {
                        val angleDeg = (i * step) + OverlayState.scrollOffset
                        // Clamp near the edges so tools don't run off the parabola.
                        val a = Math.toRadians(angleDeg.toDouble()).toFloat()
                        val tx = cx + halfW * sin(a)
                        val ty = (cy - arcH * cos(a) * cos(a)).coerceAtMost(cy - 10f)
                        val isActive = SculptTools[i].id == activeId
                        val isHighlight = i == highlightIndex
                        // Dynamic scale: center = 1.1, edges = 0.8.
                        val scale = (1.1f - 0.3f * abs(sin(a))).coerceAtLeast(0.8f)
                        drawToolSlot(tx, ty, SculptTools[i].label, scale, isActive, isHighlight)
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
    scale: Float,
    isActive: Boolean,
    isHighlight: Boolean
) {
    val r = (if (isActive) 46f else 38f) * scale
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
