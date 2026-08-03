package com.epai.oblender

import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
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
 * Interaction model (native Compose gestures — the overlay WINDOW is sized to
 * the arc band and is TOUCHABLE, so touches inside it never reach Blender):
 *  - Horizontal drag rotates the carousel; on release it snaps (animated) so
 *    the nearest tool ends up exactly at the apex.
 *  - Tap on a tool selects it: the key is emitted via JNI (emitToolKey) and
 *    the carousel animates to center that tool at the apex.
 *  - The chevron handle collapses/expands the arc.
 *
 * Geometry MUST stay in sync with sculpt_arc_hit_test() in GHOST_SystemAndroid.cc
 * (same proportions, same bottom-center anchor).
 */
@Composable
fun SculptArcContent() {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenW = with(density) { config.screenWidthDp.dp.toPx() }
    // Arc geometry (full-screen basis, mirrors the C++ hit test).
    val bandHalf = max(28f, screenW * 0.03f)
    val arcH = screenW * 0.11f
    val arrowHole = max(30f, screenW * 0.04f)
    val step = 20f

    var collapsed by remember { mutableStateOf(OverlayState.sculptArcCollapsed) }
    var highlightIndex by remember { mutableStateOf(-1) }
    // Source of truth for which tool is SELECTED (the amber glow). Updated
    // immediately on tap/drag-select; the poll syncs it from Blender for
    // external tool changes, guarded by a grace period so a stale static read
    // right after a tap can't flip the outline back to the previous tool.
    var activeSel by remember { mutableStateOf(-1) }
    var lastUserSelMs by remember { mutableStateOf(0L) }
    val scrollOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun nearestToolIndex(w: Float, h: Float, px: Float, py: Float): Int {
        val cx = w / 2f
        val cy = h
        val halfW = w / 2f - bandHalf
        var best = -1
        var bestD = 1e9f
        for (i in SculptTools.indices) {
            val a = Math.toRadians(((i * step) + scrollOffset.value).toDouble()).toFloat()
            val tx = cx + halfW * sin(a)
            val ty = cy - arcH * cos(a) * cos(a)
            val d = hypot(px - tx, py - ty)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    fun nearestToApexIndex(): Int {
        // Center index = the tool closest to the apex. round(-offset/step) directly
        // avoids the mod-180 wrap aliasing bug (Mask @200° would wrap to Clay @20°).
        return Math.round(-scrollOffset.value / step).toInt()
            .coerceIn(0, SculptTools.size - 1)
    }

    fun selectTool(sel: Int, currentActive: Int) {
        if (sel < 0 || sel >= SculptTools.size) return
        val tool = SculptTools[sel]
        android.util.Log.d("OBL.ARC", "select ${tool.id} key=${tool.key} shift=${tool.shift}")
        if (sel != currentActive) emitToolKey(tool)
        // Mark as selected immediately so the glow never waits for the poll.
        activeSel = sel
        lastUserSelMs = System.currentTimeMillis()
        scope.launch { scrollOffset.animateTo(-(sel * step).toFloat()) }
    }

    // Poll for the active tool / mode / workspace: decides whether to show the
    // arc, re-centers the ACTIVE tool at the apex, and keeps the window
    // touchable/pass-through consistent. (No finger polling needed anymore —
    // gestures come from Compose pointerInput.)
    LaunchedEffect(Unit) {
        var lastIdx = -2
        var lastActive = OverlayState.sculptArcActive
        while (isActive) {
            val toolId = OBLNativeActivity.getActiveToolIdStatic()
            val mode = OBLNativeActivity.getActiveModeStatic()
            val workspace = OBLNativeActivity.getActiveWorkspaceStatic()
            val inSculptWorkspace = workspace.startsWith("Sculpting")
            val idx = if (mode == CTX_MODE_SCULPT && inSculptWorkspace)
                SculptTools.indexOfFirst { it.id == toolId } else -1
            val inSculpt = idx >= 0
            OverlayState.sculptArcActive = inSculpt
            if (inSculpt) {
                // Sync the selected-tool glow from Blender only when it is NOT
                // a just-made user selection (grace 600ms) — otherwise a stale
                // static read (tool change not yet processed) would flip the
                // glow back to the previous tool right after a tap.
                if (idx != activeSel &&
                    (System.currentTimeMillis() - lastUserSelMs) > 600) {
                    activeSel = idx
                }
                if (idx != lastIdx) {
                    lastIdx = idx
                    scope.launch { scrollOffset.animateTo(-(idx * step).toFloat()) }
                }
            } else {
                lastIdx = -2
                activeSel = -1
            }
            // When not in the Sculpting workspace the window must not block
            // touches anywhere (pass-through). When active it is touchable.
            if (inSculpt != lastActive) {
                lastActive = inSculpt
                updateSculptArcWindow(collapsed, inSculpt)
            }
            delay(200)
        }
    }

    // Keep the Compose `collapsed` state and the window size in sync.
    LaunchedEffect(collapsed) {
        OverlayState.sculptArcCollapsed = collapsed
        updateSculptArcWindow(collapsed, OverlayState.sculptArcActive)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { pos ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val cx = w / 2f
                        val handleY = if (collapsed) h / 2f else h - arcH + 90f
                        val active = OBLNativeActivity.getActiveToolIdStatic()
                        val activeIdx = SculptTools.indexOfFirst { it.id == active }
                        if (collapsed) {
                            // Tap anywhere on the small collapsed window expands.
                            OverlayState.sculptArcCollapsed = false
                            collapsed = false
                            return@detectTapGestures
                        }
                        // Tight handle hit-test: the chevron pill is 48x24, so the
                        // tap target must be small — otherwise the ACTIVE tool's
                        // label (drawn just ~22px above the chevron) would fall
                        // inside the handle zone and collapse the arc by accident.
                        if (abs(pos.x - cx) <= arrowHole * 0.9f &&
                            abs(pos.y - handleY) <= arrowHole * 0.4f
                        ) {
                            OverlayState.sculptArcCollapsed = true
                            collapsed = true
                            return@detectTapGestures
                        }
                        val sel = nearestToolIndex(w, h, pos.x, pos.y)
                        if (sel >= 0) {
                            // selectTool() marks activeSel immediately (instant
                            // glow); highlightIndex stays drag-only so it can't
                            // linger and double-highlight another tool.
                            selectTool(sel, activeIdx)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                var gestureArmed = false
                var onHandle = false
                var handleDelta = 0f
                var dragAccum = 0f
                detectDragGestures(
                    onDragStart = { start ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val cx = w / 2f
                        val cy = h
                        val halfW = w / 2f - bandHalf
                        val handleY = if (collapsed) h / 2f else h - arcH + 90f
                        gestureArmed = false
                        onHandle = abs(start.x - cx) <= arrowHole * 0.9f &&
                                abs(start.y - handleY) <= arrowHole * 0.4f
                        handleDelta = 0f
                        dragAccum = 0f
                        if (collapsed) {
                            // Any drag on the collapsed window expands it.
                            OverlayState.sculptArcCollapsed = false
                            collapsed = false
                            return@detectDragGestures
                        }
                        if (onHandle) {
                            gestureArmed = true
                            return@detectDragGestures
                        }
                        // Parabola band test (same as sculpt_arc_hit_test in C++).
                        val sx = (start.x - cx) / halfW
                        if (start.x >= 0 && start.y >= 0 && sx >= -1f && sx <= 1f) {
                            val theta = asin(sx.coerceIn(-1f, 1f))
                            val curveY = cy - arcH * cos(theta) * cos(theta)
                            if (abs(start.y - curveY) <= bandHalf * 2f) {
                                gestureArmed = true
                                dragAccum = 0f
                                highlightIndex = nearestToolIndex(w, h, start.x, start.y)
                            }
                        }
                    },
                    onDragEnd = {
                        val active = OBLNativeActivity.getActiveToolIdStatic()
                        val activeIdx = SculptTools.indexOfFirst { it.id == active }
                        if (onHandle) {
                            val threshold = max(24f, arrowHole * 0.6f)
                            if (collapsed) {
                                if (handleDelta < -threshold) {
                                    OverlayState.sculptArcCollapsed = false
                                    collapsed = false
                                }
                            } else {
                                if (handleDelta > threshold) {
                                    OverlayState.sculptArcCollapsed = true
                                    collapsed = true
                                }
                            }
                        } else if (gestureArmed) {
                            if (dragAccum < 12f) {
                                if (highlightIndex >= 0) selectTool(highlightIndex, activeIdx)
                            } else {
                                val best = nearestToApexIndex()
                                if (best >= 0) selectTool(best, activeIdx)
                            }
                        }
                        gestureArmed = false
                        onHandle = false
                        highlightIndex = -1
                    },
                    onDragCancel = {
                        gestureArmed = false
                        onHandle = false
                        highlightIndex = -1
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (!gestureArmed) return@detectDragGestures
                        if (onHandle) {
                            handleDelta += dragAmount.y
                            return@detectDragGestures
                        }
                        val halfW = size.width / 2f - bandHalf
                        val degPerPx = 180f / halfW
                        val delta = dragAmount.x * degPerPx
                        if (abs(delta) < 120f) {
                            val maxOffset = -((SculptTools.size - 1) * step).toFloat()
                            val target =
                                (scrollOffset.value + delta).coerceIn(maxOffset, 0f)
                            scope.launch { scrollOffset.snapTo(target) }
                            dragAccum += abs(delta)
                            highlightIndex = nearestToApexIndex()
                        }
                    }
                )
            }
    ) {
        if (OverlayState.sculptArcActive) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h
                val halfW = w / 2f - bandHalf
                val apexX = cx
                val apexY = cy - arcH
                val handleY = if (collapsed) h / 2f else apexY + 90f

                if (!collapsed) {
                    // Background "bridge" band (parabola) anchored at bottom-center.
                    val bandPath = Path()
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

                    // Tool slots in carousel order. Only the 7 tools around the
                    // current center are drawn (culling per spec).
                    val centerIdx = Math.round(-scrollOffset.value / step).toInt()
                    val lo = (centerIdx - 3).coerceAtLeast(0)
                    val hi = (centerIdx + 3).coerceAtMost(SculptTools.size - 1)
                    for (i in lo..hi) {
                        val angleDeg = (i * step) + scrollOffset.value
                        val a = Math.toRadians(angleDeg.toDouble()).toFloat()
                        val tx = cx + halfW * sin(a)
                        val ty = (cy - arcH * cos(a) * cos(a)).coerceAtMost(cy - 10f)
                        val isActive = i == activeSel
                        val isHighlight = i == highlightIndex
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

private fun DrawScope.drawToolSlot(
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

private fun DrawScope.drawChevron(cx: Float, cy: Float, collapsed: Boolean) {
    val w = 48f
    val h = 24f
    val left = cx - w / 2f
    val top = cy - h / 2f
    drawRoundRect(
        color = Color(0xCC1B1B1B),
        topLeft = Offset(left, top),
        size = Size(w, h),
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

/** Size of the arc overlay window (bottom-center anchored). Collapsed = handle only. */
private fun sculptArcWindowSize(context: android.content.Context, collapsed: Boolean): Pair<Int, Int> {
    val sw = context.resources.displayMetrics.widthPixels.toFloat()
    val halfW = sw * 0.30f
    val bandHalf = max(28f, sw * 0.03f)
    val arcH = sw * 0.11f
    val arrowHole = max(30f, sw * 0.04f)
    val w = if (collapsed) (arrowHole * 2 + 24).toInt()
    else ((halfW + bandHalf) * 2 + 8).toInt()
    val h = if (collapsed) (arrowHole * 2 + 24).toInt()
    else (arcH + bandHalf + 56).toInt()
    return w to h
}

/** Resize / re-touchability of the overlay window. Touchable only when active.
 * Uses the wheel geometry (wheelWindowSize) since the wheel replaced the arc. */
internal fun updateSculptArcWindow(collapsed: Boolean, touchable: Boolean) {
    val view = OverlayState.sculptArcView ?: return
    val lp = OverlayState.sculptArcLp ?: return
    val (w, h) = wheelWindowSize(view.context)
    lp.width = w
    lp.height = h
    lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    Handler(Looper.getMainLooper()).post {
        try {
            val wm = view.context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
            wm.updateViewLayout(view, lp)
            android.util.Log.d("OBL.ARC", "updateSculptArcWindow touchable=$touchable size=${w}x${h}")
        } catch (e: Exception) {
            android.util.Log.e("OBL.ARC", "updateSculptArcWindow: UPDATE FAILED", e)
        }
    }
}

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

    val (w, h) = sculptArcWindowSize(context, collapsed = false)
    val lp = WindowManager.LayoutParams(
        w,
        h,
        WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        android.graphics.PixelFormat.TRANSPARENT
    )
    lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
    OverlayState.sculptArcLp = lp

    Handler(Looper.getMainLooper()).post {
        try {
            android.util.Log.d("OBL", "createSculptArcOverlay: adding to WindowManager on UI thread")
            val wm = context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
            wm.addView(composeView, lp)
            android.util.Log.d("OBL", "createSculptArcOverlay: added successfully ${w}x$h")
        } catch (e: Exception) {
            android.util.Log.e("OBL", "createSculptArcOverlay: ADD FAILED", e)
        }
    }

    return composeView
}
