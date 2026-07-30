package com.epai.oblender

import android.view.ViewGroup
import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
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

    var pointerPosition by remember {
        mutableStateOf(Offset(screenW / 2f, screenH / 2f))
    }
    // Sync initial position to OverlayState
    LaunchedEffect(Unit) {
        OverlayState.cursorX = pointerPosition.x.toInt()
        OverlayState.cursorY = pointerPosition.y.toInt()
    }

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
                            val currentPointers = event.changes.filter { it.pressed }
                            val pointerMap = mutableMapOf<Long, Offset>()
                            for (change in currentPointers) {
                                if (change.type == PointerType.Touch) {
                                    pointerMap[change.id.value] = change.position
                                }
                            }

                            when (currentPointers.size) {
                                0 -> {
                                    if (activePointers.size == 1 && !isDragging) {
                                        if (System.currentTimeMillis() - pointerDownTime < 300) {
                                            onLeftClick(pointerPosition.x.toInt(), pointerPosition.y.toInt())
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
                                        pointerDownTime = System.currentTimeMillis()
                                        activePointers[id] = pos
                                    } else if (activePointers.size == 1 && activePointers.containsKey(id)) {
                                        val prev = activePointers[id]!!
                                        val delta = pos - prev
                                        if (!isDragging && delta.getDistance() > 16f) isDragging = true

                                        if (isDragging) {
                                            pointerPosition = Offset(
                                                x = (pointerPosition.x + delta.x).coerceIn(0f, screenW),
                                                y = (pointerPosition.y + delta.y).coerceIn(0f, screenH)
                                            )
                                            OverlayState.cursorX = pointerPosition.x.toInt()
                                            OverlayState.cursorY = pointerPosition.y.toInt()
                                        }
                                        activePointers[id] = pos
                                    }
                                }
                                2 -> {
                                    val entries = pointerMap.entries.toList()
                                    val p1 = entries[0].value
                                    val p2 = entries[1].value
                                    val dist = (p1 - p2).getDistance()
                                    if (lastTwoFingerDist > 0f) onScroll((dist - lastTwoFingerDist) * 0.05f)
                                    lastTwoFingerDist = dist
                                    for (entry in pointerMap.entries) activePointers[entry.key] = entry.value
                                }
                            }
                            for (change in event.changes) change.consume()
                        }
                    }
                }
            }
    ) {
        // Render Cursor Image - HOTSPOT APROXIMADO (ajusta según imagen)
        Image(
            painter = painterResource(id = R.drawable.img_cursor),
            contentDescription = "Virtual Cursor",
            modifier = Modifier.offset {
                // Ajuste de hotspot: si la punta está arriba a la izquierda, 
                // esto centra el clic en esa punta.
                IntOffset(
                    (pointerPosition.x).roundToInt(), 
                    (pointerPosition.y).roundToInt()
                )
            }
        )
    }
}

fun createVirtualPointerOverlay(context: android.content.Context, lifecycleOwner: LifecycleOwner): ComposeView {
    android.util.Log.d("OBL", "createVirtualPointerOverlay: start")
    val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            VirtualPointerContent(
                onLeftClick = { x, y ->
                    android.util.Log.d("OBL", "VirtualPointer: leftClick at $x,$y")
                    OBLNativeActivity.oblSetValueStatic("10010,$x,$y")
                    OBLNativeActivity.oblSetValueStatic("10000,")
                },
                onRightClick = { x, y ->
                    android.util.Log.d("OBL", "VirtualPointer: rightClick at $x,$y")
                    OBLNativeActivity.oblSetValueStatic("10010,$x,$y")
                    OBLNativeActivity.oblSetValueStatic("10001,")
                },
                onScroll = { delta ->
                    android.util.Log.d("OBL", "VirtualPointer: scroll delta=$delta")
                    if (delta > 0) OBLNativeActivity.oblSetValueStatic("10002,")
                    else OBLNativeActivity.oblSetValueStatic("10003,")
                }
            )
        }
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    val activity = context as Activity
    val rootView = activity.findViewById<ViewGroup>(android.R.id.content)

    Handler(Looper.getMainLooper()).post {
        try {
            android.util.Log.d("OBL", "createVirtualPointerOverlay: adding to RootView on UI thread")
            rootView.addView(composeView)
            android.util.Log.d("OBL", "createVirtualPointerOverlay: added successfully")
        } catch (e: Exception) {
            android.util.Log.e("OBL", "createVirtualPointerOverlay: ADD FAILED", e)
        }
    }

    return composeView
}
