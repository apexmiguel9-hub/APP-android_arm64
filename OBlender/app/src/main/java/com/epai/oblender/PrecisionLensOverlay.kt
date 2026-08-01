package com.epai.oblender

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/**
 * Precision-cursor magnifying lens overlay.
 *
 * In PRECISION mode the finger still draws directly (native touch pipeline, like TOUCH
 * mode), but a floating lens shows the area under the finger magnified so strokes can
 * be placed precisely without the finger hiding the exact point.
 *
 * The lens is FLAG_NOT_TOUCHABLE: touches pass straight through to the GL surface, so
 * Blender receives them as real touch. The lens position comes from the GHOST cursor
 * position (== finger position in touch mode) plus the native isTouchDown() flag.
 */
@Composable
fun PrecisionLensContent() {
    val windowSize = LocalWindowInfo.current.containerSize
    val screenW = windowSize.width.coerceAtLeast(1)
    val screenH = windowSize.height.coerceAtLeast(1)
    val density = LocalDensity.current.density

    val lensRadiusDp = 84.dp
    val zoom = 3f

    var fingerDown by remember { mutableStateOf(false) }
    var fingerPos by remember { mutableStateOf(Offset.Zero) }
    var lensBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) {
        var lastX = -1
        var lastY = -1
        var lastDown = false
        while (isActive) {
            val real = OBLNativeActivity.getCursorPositionStatic()
            val down = OBLNativeActivity.getTouchDownStatic()
            val x = if (real != null && real.size == 2) real[0] else -1
            val y = if (real != null && real.size == 2) real[1] else -1
            val surface = OverlayState.renderSurface

            if (down && x >= 0 && y >= 0 && surface != null && surface.isValid) {
                fingerDown = true
                fingerPos = Offset(x.toFloat(), y.toFloat())

                // Re-capture only when the finger moved or just went down.
                if (!lastDown || x != lastX || y != lastY) {
                    val lensPx = (lensRadiusDp.value * 2f * density).roundToInt()
                    val srcSide = (lensPx / zoom).roundToInt().coerceAtLeast(8)
                    val half = srcSide / 2

                    val srcLeft = (x - half).coerceIn(0, screenW - 1)
                    val srcTop = (y - half).coerceIn(0, screenH - 1)
                    val srcRight = (srcLeft + srcSide).coerceIn(0, screenW)
                    val srcBottom = (srcTop + srcSide).coerceIn(0, screenH)

                    val dst = Bitmap.createBitmap(srcRight - srcLeft, srcBottom - srcTop, Bitmap.Config.ARGB_8888)
                    val srcRect = Rect(srcLeft, srcTop, srcRight, srcBottom)
                    try {
                        requestPixelCopy(surface, srcRect, dst) { result ->
                            if (result == PixelCopySuccess) {
                                lensBitmap = dst
                            }
                        }
                    } catch (_: Exception) {
                        lensBitmap = null
                    }
                }
            } else {
                fingerDown = false
                lensBitmap = null
            }
            lastDown = down
            lastX = x
            lastY = y
            delay(16)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (fingerDown && lensBitmap != null) {
            val bmp = lensBitmap ?: return@Box
            val pos = fingerPos
            val radiusPx = lensRadiusDp.value * density
            val diameterPx = radiusPx * 2f
            // Offset the lens above the finger so the finger does not cover it.
            val centerX = pos.x - radiusPx
            val centerY = pos.y - radiusPx * 2.4f
            Canvas(
                modifier = Modifier
                    .size(lensRadiusDp * 2f)
                    .offset {
                        IntOffset(
                            (centerX.coerceIn(0f, screenW.toFloat() - diameterPx)).roundToInt(),
                            (centerY.coerceIn(0f, screenH.toFloat() - diameterPx)).roundToInt()
                        )
                    }
            ) {
                drawCircle(color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                val image = bmp.asImageBitmap()
                val side = size.width
                val circlePath = Path().apply {
                    addOval(androidx.compose.ui.geometry.Rect(0f, 0f, side, side))
                }
                clipPath(circlePath) {
                    drawImage(
                        image = image,
                        dstSize = androidx.compose.ui.unit.IntSize(side.roundToInt(), side.roundToInt()),
                        alpha = 1f
                    )
                }
                drawCircle(
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
    }
}

fun createPrecisionLensOverlay(context: android.content.Context, lifecycleOwner: LifecycleOwner): ComposeView {
    android.util.Log.d("OBL", "createPrecisionLensOverlay: start")
    val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            PrecisionLensContent()
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
            android.util.Log.d("OBL", "createPrecisionLensOverlay: adding to WindowManager on UI thread")
            wm.addView(composeView, lp)
            android.util.Log.d("OBL", "createPrecisionLensOverlay: added successfully")
        } catch (e: Exception) {
            android.util.Log.e("OBL", "createPrecisionLensOverlay: ADD FAILED", e)
        }
    }

    return composeView
}

// android.graphics.PixelCopy exists at runtime (API 26+, device is API 28+) but the
// android.jar resolved by this AGP in CI does not expose it; call it via reflection.
private const val PixelCopySuccess = 0

@Suppress("UNCHECKED_CAST")
private fun requestPixelCopy(
    surface: android.view.Surface,
    srcRect: Rect,
    dst: Bitmap,
    callback: (Int) -> Unit
) {
    try {
        val pixelCopyClass = Class.forName("android.graphics.PixelCopy")
        val listenerIface = Class.forName("android.graphics.PixelCopy\$OnPixelCopyFinishedListener")
        val listener = java.lang.reflect.Proxy.newProxyInstance(
            listenerIface.classLoader,
            arrayOf(listenerIface)
        ) { _, method, args ->
            if (method.name == "onPixelCopyFinished") {
                callback((args?.getOrNull(0) as? Int) ?: -1)
            }
            null
        }
        pixelCopyClass.getMethod(
            "request",
            android.view.Surface::class.java,
            Rect::class.java,
            Bitmap::class.java,
            listenerIface
        ).invoke(null, surface, srcRect, dst, listener)
    } catch (e: Throwable) {
        callback(-1)
    }
}
