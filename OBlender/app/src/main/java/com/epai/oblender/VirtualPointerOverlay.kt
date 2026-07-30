package com.epai.oblender

import android.view.WindowManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.roundToInt

private val BTN_W_PX = 48f
private val BTN_H_PX = 36f
private val BTN_GAP = 4f
private val BTN_COLOR = Color(0x88000000)
private val BTN_BORDER = Color(0x44FFFFFF)
private val BTN_TEXT = Color(0xCCFFFFFF)

@Composable
fun VirtualPointerContent(
    onLeftClick: (x: Int, y: Int) -> Unit,
    onRightClick: (x: Int, y: Int) -> Unit,
    onScroll: (Float) -> Unit
) {
    val density = LocalDensity.current
    val btnWDp = with(density) { BTN_W_PX.toDp() }
    val btnHDp = with(density) { BTN_H_PX.toDp() }
    val barPad = 8.dp

    val windowSize = LocalWindowInfo.current.containerSize
    val screenW = windowSize.width.toFloat().coerceAtLeast(1f)
    val screenH = windowSize.height.toFloat().coerceAtLeast(1f)

    var pointerPosition by remember {
        mutableStateOf(Offset(screenW / 2f, screenH / 2f))
    }
    LaunchedEffect(Unit) {
        OverlayState.cursorX = pointerPosition.x.toInt()
        OverlayState.cursorY = pointerPosition.y.toInt()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(screenW, screenH, BTN_W_PX, BTN_H_PX, BTN_GAP) {
                    val numBtns = 4
                    val barWidth = numBtns * BTN_W_PX + (numBtns - 1) * BTN_GAP
                    val barLeft = screenW - barWidth - 16f
                    val barTop = screenH - BTN_H_PX - 16f

                    fun btnIndex(x: Float, y: Float): Int {
                        if (x < barLeft || x > barLeft + barWidth || y < barTop || y > barTop + BTN_H_PX) return -1
                        return ((x - barLeft) / (BTN_W_PX + BTN_GAP)).toInt().coerceIn(0, numBtns - 1)
                    }

                    awaitPointerEventScope {
                        var leftFingerId = -1L
                        var leftDown = false
                        var pendingTapIdx = -1
                        var pendingTapFinger = -1L
                        var pendingTapStart = 0L
                        var pendingTapPos = Offset.Zero

                        while (true) {
                            val event = awaitPointerEvent()
                            for (change in event.changes) {
                                if (change.type != PointerType.Touch) continue
                                val id = change.id.value

                                if (change.pressed) {
                                    val idx = btnIndex(change.position.x, change.position.y)

                                    if (idx == 3 && !leftDown) {
                                        leftFingerId = id
                                        leftDown = true
                                        OBLNativeActivity.oblSetValueStatic(
                                            "10010,${pointerPosition.x.toInt()},${pointerPosition.y.toInt()}"
                                        )
                                        OBLNativeActivity.oblSetValueStatic("10004,")
                                    } else if (idx in 0..2 && !leftDown) {
                                        pendingTapIdx = idx
                                        pendingTapFinger = id
                                        pendingTapStart = System.currentTimeMillis()
                                        pendingTapPos = change.position
                                    } else if (idx < 0 && !leftDown) {
                                        pointerPosition = Offset(
                                            x = change.position.x.coerceIn(0f, screenW),
                                            y = change.position.y.coerceIn(0f, screenH)
                                        )
                                        OverlayState.cursorX = pointerPosition.x.toInt()
                                        OverlayState.cursorY = pointerPosition.y.toInt()
                                        OBLNativeActivity.oblSetValueStatic(
                                            "10010,${pointerPosition.x.toInt()},${pointerPosition.y.toInt()}"
                                        )
                                    }
                                }

                                if (!change.pressed) {
                                    if (id == leftFingerId && leftDown) {
                                        OBLNativeActivity.oblSetValueStatic("10005,")
                                        leftDown = false
                                        leftFingerId = -1
                                    } else if (id == pendingTapFinger && pendingTapIdx >= 0) {
                                        val elapsed = System.currentTimeMillis() - pendingTapStart
                                        val dist = (change.position - pendingTapPos).getDistance()
                                        if (elapsed < 300 && dist < 24f) {
                                            when (pendingTapIdx) {
                                                0 -> onScroll(1f)
                                                1 -> onScroll(-1f)
                                                2 -> onRightClick(pointerPosition.x.toInt(), pointerPosition.y.toInt())
                                            }
                                        }
                                        pendingTapIdx = -1
                                        pendingTapFinger = -1
                                    }
                                }

                                // Track L-finger movement for drawing
                                if (id == leftFingerId && leftDown && change.pressed) {
                                    val clampedX = change.position.x.coerceIn(0f, screenW)
                                    val clampedY = change.position.y.coerceIn(0f, screenH)
                                    pointerPosition = Offset(clampedX, clampedY)
                                    OverlayState.cursorX = clampedX.toInt()
                                    OverlayState.cursorY = clampedY.toInt()
                                    OBLNativeActivity.oblSetValueStatic(
                                        "10010,${clampedX.toInt()},${clampedY.toInt()}"
                                    )
                                }

                                change.consume()
                            }
                        }
                    }
                }
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(barPad),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BtnView("\u2191", btnWDp, btnHDp)
            BtnView("\u2193", btnWDp, btnHDp)
            BtnView("R", btnWDp, btnHDp)
            BtnView("L", btnWDp, btnHDp, bluish = true)
        }

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

@Composable
private fun RowScope.BtnView(
    text: String,
    w: androidx.compose.ui.unit.Dp,
    h: androidx.compose.ui.unit.Dp,
    bluish: Boolean = false
) {
    val bg = if (bluish) Color(0x884466AA) else BTN_COLOR
    Box(
        modifier = Modifier
            .size(width = w, height = h)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, BTN_BORDER, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = text,
            color = BTN_TEXT,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
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
