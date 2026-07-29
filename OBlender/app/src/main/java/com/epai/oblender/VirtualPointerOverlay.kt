package com.epai.oblender

import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope

/**
 * Virtual pointer overlay that intercepts touch events and renders a crosshair cursor.
 * Adapted from Zalith Launcher 2's VirtualPointerLayout.
 *
 * Single finger: drag to move cursor, tap to left-click at cursor position.
 * Two fingers: pinch to zoom (scroll).
 * Two-finger tap: right-click at cursor position.
 */
@Composable
fun VirtualPointerContent(
    onLeftClick: (x: Int, y: Int) -> Unit,
    onRightClick: (x: Int, y: Int) -> Unit,
    onScroll: (Float) -> Unit
) {
    val windowSize = LocalWindowInfo.current.containerSize
    val screenW = windowSize.width.toFloat().coerceAtLeast(1f)
    val screenH = windowSize.height.toFloat().coerceAtLeast(1f)

    val density = LocalDensity.current
    val crosshairSize = with(density) { 32.dp.toPx() }

    var pointerPosition by remember {
        mutableStateOf(Offset(screenW / 2f, screenH / 2f))
    }
    // Sync initial position to OverlayState
    LaunchedEffect(Unit) {
        OverlayState.cursorX = pointerPosition.x.toInt()
        OverlayState.cursorY = pointerPosition.y.toInt()
    }

    var lastPointerDown by remember { mutableStateOf<Offset?>(null) }
    var pointerDownTime by remember { mutableStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }
    var lastTwoFingerDist by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                coroutineScope {
                    var activePointers = mutableMapOf<Long, Offset>()

                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()

                            // Track pointer count
                            val currentPointers = event.changes.filter { it.pressed }
                            val pointerMap = mutableMapOf<Long, Offset>()
                            for (change in currentPointers) {
                                if (change.type == PointerType.Touch) {
                                    pointerMap[change.id.value] = change.position
                                }
                            }

                            when (currentPointers.size) {
                                0 -> {
                                    // All pointers released
                                    if (activePointers.size == 1 && !isDragging) {
                                        val pos = activePointers.values.first()
                                        val topArea = 0f
                                        if (pos.y > topArea) {
                                            if (System.currentTimeMillis() - pointerDownTime < 300) {
                                                onLeftClick(
                                                    pointerPosition.x.toInt(),
                                                    pointerPosition.y.toInt()
                                                )
                                            }
                                        }
                                    }
                                    activePointers.clear()
                                    isDragging = false
                                    lastTwoFingerDist = 0f
                                }
                                1 -> {
                                    val entry = pointerMap.entries.first()
                                    val id = entry.key
                                    val pos = entry.value

                                    if (activePointers.isEmpty()) {
                                        // First touch down
                                        lastPointerDown = pos
                                        pointerDownTime = System.currentTimeMillis()
                                        activePointers[id] = pos
                                    } else if (activePointers.size == 1 && activePointers.containsKey(id)) {
                                        val prev = activePointers[id]!!
                                        val delta = pos - prev

                                        // Check if dragging beyond touch slop
                                        if (!isDragging && delta.getDistance() > 16f) {
                                            isDragging = true
                                        }

                                        if (isDragging) {
                                            pointerPosition = Offset(
                                                x = (pointerPosition.x + delta.x).coerceIn(0f, screenW),
                                                y = (pointerPosition.y + delta.y).coerceIn(0f, screenH)
                                            )
                                            OverlayState.cursorX = pointerPosition.x.toInt()
                                            OverlayState.cursorY = pointerPosition.y.toInt()
                                        }
                                        activePointers[id] = pos
                                    } else if (activePointers.size == 2) {
                                        // Transitioned from 2 to 1 finger — reset
                                        activePointers.clear()
                                        activePointers[id] = pos
                                        lastPointerDown = pos
                                        pointerDownTime = System.currentTimeMillis()
                                        isDragging = false
                                    }
                                }
                                2 -> {
                                    val entries = pointerMap.entries.toList()
                                    val p1 = entries[0].value
                                    val p2 = entries[1].value
                                    val dist = (p1 - p2).getDistance()

                                    if (lastTwoFingerDist > 0f) {
                                        val delta = dist - lastTwoFingerDist
                                        // Scale factor: 1px pinch ≈ 0.05 scroll units
                                        onScroll(delta * 0.05f)
                                    }
                                    lastTwoFingerDist = dist

                                    // Update activePointers with new positions
                                    for (entry in pointerMap.entries) {
                                        activePointers[entry.key] = entry.value
                                    }

                                    // Check for two-finger tap (quick touch and release)
                                    if (activePointers.size >= 2 && !isDragging) {
                                        val now = System.currentTimeMillis()
                                        if (now - pointerDownTime < 300) {
                                            // This will be handled on release
                                        }
                                    }
                                }
                                else -> { /* 3+ fingers — ignore */ }
                            }

                            // Consume all touch changes
                            for (change in event.changes) {
                                change.consume()
                            }
                        }
                    }
                }
            }
    ) {
        // Render crosshair
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = pointerPosition.x
            val cy = pointerPosition.y
            val halfSize = crosshairSize / 2f
            val gap = 4f

            drawCircle(
                color = Color.White.copy(alpha = 0.5f),
                radius = halfSize + 2f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5f)
            )
            drawLine(
                color = Color.White,
                start = Offset(cx, cy - halfSize + gap),
                end = Offset(cx, cy + halfSize - gap),
                strokeWidth = 1.5f
            )
            drawLine(
                color = Color.White,
                start = Offset(cx - halfSize + gap, cy),
                end = Offset(cx + halfSize - gap, cy),
                strokeWidth = 1.5f
            )
            drawCircle(
                color = Color.White,
                radius = 2.5f,
                center = Offset(cx, cy)
            )
        }
    }
}

fun createVirtualPointerOverlay(context: android.content.Context): ComposeView {
    val composeView = ComposeView(context).apply {
        setContent {
            VirtualPointerContent(
                onLeftClick = { x, y ->
                    OBLNativeActivity.oblSetValueStatic("10010,$x,$y")
                    OBLNativeActivity.oblSetValueStatic("10000,")
                },
                onRightClick = { x, y ->
                    OBLNativeActivity.oblSetValueStatic("10010,$x,$y")
                    OBLNativeActivity.oblSetValueStatic("10001,")
                },
                onScroll = { delta ->
                    if (delta > 0) OBLNativeActivity.oblSetValueStatic("10002,")
                    else OBLNativeActivity.oblSetValueStatic("10003,")
                }
            )
        }
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    val wm = context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
    val lp = WindowManager.LayoutParams(
        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        android.graphics.PixelFormat.TRANSPARENT
    )

    try {
        wm.addView(composeView, lp)
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return composeView
}
