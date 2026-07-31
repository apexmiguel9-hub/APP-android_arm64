package com.epai.oblender

import android.view.WindowManager
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.view.ViewGroup

private val cursorUpdateHandler = Handler(Looper.getMainLooper())

fun createCursorOverlay(context: android.content.Context): ImageView {
    val cursorSize = 32
    val density = context.resources.displayMetrics.density
    val sizePx = (cursorSize * density).toInt()

    val imageView = ImageView(context).apply {
        setImageResource(R.drawable.img_cursor)
        layoutParams = ViewGroup.LayoutParams(sizePx, sizePx)
        isClickable = false
        isFocusable = false
        isEnabled = false
    }

    val wm = context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
    val lp = WindowManager.LayoutParams(
        sizePx,
        sizePx,
        WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        android.graphics.PixelFormat.TRANSPARENT
    )
    lp.x = OverlayState.cursorX - (sizePx / 2)
    lp.y = OverlayState.cursorY - (sizePx / 2)

    try {
        wm.addView(imageView, lp)
    } catch (e: Exception) {
        android.util.Log.e("OBL", "createCursorOverlay: ADD FAILED", e)
    }

    val updateRunnable = object : Runnable {
        override fun run() {
            if (!imageView.isAttachedToWindow) return
            lp.x = OverlayState.cursorX - (sizePx / 2)
            lp.y = OverlayState.cursorY - (sizePx / 2)
            try { wm.updateViewLayout(imageView, lp) } catch (_: Exception) {}
            cursorUpdateHandler.postDelayed(this, 16)
        }
    }
    cursorUpdateHandler.postDelayed(updateRunnable, 16)

    return imageView
}

fun removeCursorOverlay(view: ImageView) {
    try {
        val wm = view.context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
        wm.removeView(view)
    } catch (_: Exception) {}
}
