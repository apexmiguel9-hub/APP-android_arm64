package com.epai.oblender

import android.view.WindowManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.coroutineScope
import kotlin.math.roundToInt

/**
 * Virtual pointer overlay that moves cursor on touch.
 * Single finger: move cursor.
 * Two fingers: pinch to zoom (scroll).
 * Left/Right click handled by dedicated buttons in the control layout.
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
        // Start at the REAL GHOST cursor position so the icon is never desynced
        val real = OBLNativeActivity.getCursorPositionStatic()
        val sx = if (real != null && real.size == 2 && real[0] >= 0 && real[1] >= 0) {
            real[0].toFloat()
        } else {
            screenW / 2f
        }
        val sy = if (real != null && real.size == 2 && real[0] >= 0 && real[1] >= 0) {
            real[1].toFloat()
        } else {
            screenH / 2f
        }
        mutableStateOf(Offset(sx.coerceIn(0f, screenW), sy.coerceIn(0f, screenH)))
    }
    LaunchedEffect(Unit) {
        OverlayState.cursorX = pointerPosition.x.toInt()
        OverlayState.cursorY = pointerPosition.y.toInt()
        OBLNativeActivity.oblSetValueStatic("10010," + pointerPosition.x.toInt() + "," + pointerPosition.y.toInt())
    }

    var lastTwoFingerDist by remember { mutableStateOf(0f) }

    val stillnessTimeMs = remember { VirtualPointerSettings.getStillnessTimeMs() }
    val stillnessThresholdPx = remember { VirtualPointerSettings.getStillnessThresholdPx().toFloat() }
    val tapMaxDistPx = remember { VirtualPointerSettings.getTapMaxDistPx().toFloat() }
    val tapMaxTimeMs = remember { VirtualPointerSettings.getTapMaxTimeMs() }
    val sensitivity = remember { VirtualPointerSettings.getSensitivity() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var prevPos = mutableMapOf<Long, Offset>()
                    var touchDownTime = 0L
                    var touchDownPos = Offset.Zero
                    var leftDownSent = false
                    var fingerId = 0L
                    var stillStart = -1L

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
                                if (leftDownSent) {
                                    OBLNativeActivity.oblSetValueStatic("10005,")
                                    leftDownSent = false
                                } else if (touchDownTime > 0 &&
                                    System.currentTimeMillis() - touchDownTime < tapMaxTimeMs) {
                                    val downPos = touchDownPos
                                    val dist = (pointerMap.values.firstOrNull() ?: downPos) - downPos
                                    if (dist.getDistance() < tapMaxDistPx) {
                                        OBLNativeActivity.oblSetValueStatic(
                                            "10010,${OverlayState.cursorX},${OverlayState.cursorY}")
                                        OBLNativeActivity.oblSetValueStatic("10004,")
                                        OBLNativeActivity.oblSetValueStatic("10005,")
                                    }
                                }
                                lastTwoFingerDist = 0f
                                prevPos.clear()
                                touchDownTime = 0L
                                stillStart = -1L
                            }
                            1 -> {
                                val entry = pointerMap.entries.first()
                                val id = entry.key
                                val pos = entry.value

                                if (prevPos.isEmpty()) {
                                    fingerId = id
                                    touchDownTime = System.currentTimeMillis()
                                    touchDownPos = pos
                                    prevPos[id] = pos
                                    stillStart = -1L
                                }

                                if (id == fingerId) {
                                    val prev = prevPos[id]
                                    val now = System.currentTimeMillis()
                                    val elapsed = now - touchDownTime

                                    if (prev != null) {
                                        val delta = pos - prev
                                        val moved = delta.getDistance()

                                        if (moved > stillnessThresholdPx) {
                                            stillStart = -1L
                                        } else if (stillStart < 0 && moved <= stillnessThresholdPx && elapsed > 50) {
                                            stillStart = now
                                        }

                                        if (!leftDownSent && stillStart > 0 && (now - stillStart) > stillnessTimeMs) {
                                            OBLNativeActivity.oblSetValueStatic("10004,")
                                            leftDownSent = true
                                        }

                                        pointerPosition = Offset(
                                            x = (pointerPosition.x + delta.x * sensitivity).coerceIn(0f, screenW),
                                            y = (pointerPosition.y + delta.y * sensitivity).coerceIn(0f, screenH)
                                        )
                                        OverlayState.cursorX = pointerPosition.x.toInt()
                                        OverlayState.cursorY = pointerPosition.y.toInt()
                                        OBLNativeActivity.oblSetValueStatic(
                                            "10010,${pointerPosition.x.toInt()},${pointerPosition.y.toInt()}"
                                        )
                                    }
                                    prevPos[id] = pos
                                }
                            }
                            2 -> {
                                val entries = pointerMap.entries.toList()
                                val p1 = entries[0].value
                                val p2 = entries[1].value
                                val dist = (p1 - p2).getDistance()
                                if (lastTwoFingerDist > 0f) onScroll((dist - lastTwoFingerDist) * 0.05f)
                                lastTwoFingerDist = dist
                                for (entry in pointerMap.entries) prevPos[entry.key] = entry.value
                            }
                            else -> {
                                if (currentPointers.isEmpty()) {
                                    lastTwoFingerDist = 0f
                                    prevPos.clear()
                                }
                            }
                        }
                        for (change in event.changes) change.consume()
                    }
                }
            }
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_cursor),
            contentDescription = "Virtual Cursor",
            modifier = Modifier
                .size(32.dp)
                .offset {
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
    VirtualPointerSettings.init(context)
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

    composeView.setViewTreeLifecycleOwner(lifecycleOwner)
    composeView.setViewTreeSavedStateRegistryOwner(SimpleSavedStateRegistryOwner())

    val wm = context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
    val lp = WindowManager.LayoutParams(
        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        android.graphics.PixelFormat.TRANSPARENT
    )

    Handler(Looper.getMainLooper()).post {
        try {
            android.util.Log.d("OBL", "createVirtualPointerOverlay: adding to WindowManager on UI thread")
            wm.addView(composeView, lp)
            android.util.Log.d("OBL", "createVirtualPointerOverlay: added successfully")
        } catch (e: Exception) {
            android.util.Log.e("OBL", "createVirtualPointerOverlay: ADD FAILED", e)
        }
    }

    return composeView
}
