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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.roundToInt

private val BTN_W_DP = 56.dp
private val BTN_H_DP = 40.dp
private val BTN_GAP_DP = 4.dp
private val BTN_COLOR = Color(0x88000000)
private val BTN_BORDER = Color(0x44FFFFFF)
private val BTN_TEXT = Color(0xCCFFFFFF)
private val BTN_BAR_PAD = 12.dp

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
    LaunchedEffect(Unit) {
        OverlayState.cursorX = pointerPosition.x.toInt()
        OverlayState.cursorY = pointerPosition.y.toInt()
    }

    val dp = LocalDensity.current
    val btnWPx = with(dp) { BTN_W_DP.toPx() }
    val btnHPx = with(dp) { BTN_H_DP.toPx() }
    val btnGapPx = with(dp) { BTN_GAP_DP.toPx() }
    val barPadPx = with(dp) { BTN_BAR_PAD.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(
                    screenW, screenH, btnWPx, btnHPx, btnGapPx, barPadPx
                ) {
                    val numBtns = 4
                    val barWidth = numBtns * btnWPx + (numBtns - 1) * btnGapPx
                    val barLeft = screenW - barWidth - barPadPx
                    val barTop = screenH - btnHPx - barPadPx

                    fun btnIndex(x: Float, y: Float): Int {
                        if (x < barLeft || y < barTop || x > barLeft + barWidth || y > barTop + btnHPx) return -1
                        return ((x - barLeft) / (btnWPx + btnGapPx)).toInt().coerceIn(0, numBtns - 1)
                    }

                    awaitPointerEventScope {
                        var leftFingerId = -1L
                        var leftDown = false
                        var cursorPrevPos = Offset.Zero
                        var leftPrevPos = Offset.Zero
                        var tracking = false

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
                                        leftPrevPos = change.position
                                        OBLNativeActivity.oblSetValueStatic(
                                            "10010,${pointerPosition.x.toInt()},${pointerPosition.y.toInt()}"
                                        )
                                        OBLNativeActivity.oblSetValueStatic("10004,")
                                    } else if (idx in 0..2 && pendingTapFinger < 0) {
                                        pendingTapIdx = idx
                                        pendingTapFinger = id
                                        pendingTapStart = System.currentTimeMillis()
                                        pendingTapPos = change.position
                                    } else if (idx < 0 && !tracking) {
                                        cursorPrevPos = change.position
                                        tracking = true
                                    }
                                }

                                // cursor movement: non-L finger
                                if (change.pressed && tracking && id != leftFingerId) {
                                    val delta = change.position - cursorPrevPos
                                    cursorPrevPos = change.position
                                    pointerPosition = Offset(
                                        x = (pointerPosition.x + delta.x).coerceIn(0f, screenW),
                                        y = (pointerPosition.y + delta.y).coerceIn(0f, screenH)
                                    )
                                    OverlayState.cursorX = pointerPosition.x.toInt()
                                    OverlayState.cursorY = pointerPosition.y.toInt()
                                    OBLNativeActivity.oblSetValueStatic(
                                        "10010,${pointerPosition.x.toInt()},${pointerPosition.y.toInt()}"
                                    )
                                }

                                // cursor movement: L-finger drawing
                                if (id == leftFingerId && leftDown && change.pressed) {
                                    val delta = change.position - leftPrevPos
                                    leftPrevPos = change.position
                                    pointerPosition = Offset(
                                        x = (pointerPosition.x + delta.x).coerceIn(0f, screenW),
                                        y = (pointerPosition.y + delta.y).coerceIn(0f, screenH)
                                    )
                                    OverlayState.cursorX = pointerPosition.x.toInt()
                                    OverlayState.cursorY = pointerPosition.y.toInt()
                                    OBLNativeActivity.oblSetValueStatic(
                                        "10010,${pointerPosition.x.toInt()},${pointerPosition.y.toInt()}"
                                    )
                                }

                                if (!change.pressed) {
                                    if (id == leftFingerId && leftDown) {
                                        OBLNativeActivity.oblSetValueStatic("10005,")
                                        leftDown = false
                                        leftFingerId = -1
                                        tracking = false
                                    } else if (id == pendingTapFinger && pendingTapIdx >= 0) {
                                        val elapsed = System.currentTimeMillis() - pendingTapStart
                                        val dist = (change.position - pendingTapPos).getDistance()
                                        if (elapsed < 400 && dist < 48f) {
                                            when (pendingTapIdx) {
                                                0 -> onScroll(1f)
                                                1 -> onScroll(-1f)
                                                2 -> onRightClick(pointerPosition.x.toInt(), pointerPosition.y.toInt())
                                            }
                                        }
                                        pendingTapIdx = -1
                                        pendingTapFinger = -1
                                    } else if (id != leftFingerId && tracking) {
                                        tracking = false
                                    }
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
                .padding(BTN_BAR_PAD),
            horizontalArrangement = Arrangement.spacedBy(BTN_GAP_DP)
        ) {
            BtnView("\u2191", BTN_W_DP, BTN_H_DP)
            BtnView("\u2193", BTN_W_DP, BTN_H_DP)
            BtnView("R", BTN_W_DP, BTN_H_DP)
            BtnView("L", BTN_W_DP, BTN_H_DP, bluish = true)
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
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, BTN_BORDER, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = text,
            color = BTN_TEXT,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
