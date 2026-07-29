package com.epai.oblender

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.movtery.layer_controller.EDITOR_VERSION
import com.movtery.layer_controller.data.*
import com.movtery.layer_controller.data.lang.createTranslatable
import com.movtery.layer_controller.layout.ControlLayout
import com.movtery.layer_controller.layout.EmptyControlLayout
import com.movtery.layer_controller.layout.createNewLayer
import com.movtery.layer_controller.layout.loadLayoutFromFile
import com.movtery.layer_controller.observable.ObservableControlLayout
import com.movtery.layer_controller.utils.saveToFile
import androidx.compose.ui.graphics.toArgb
import com.epai.oblender.ui.screens.main.control_editor.ControlEditor
import com.epai.oblender.viewmodel.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object OverlayState {
    @JvmStatic
    var hostView by mutableStateOf<View?>(null)
    @JvmStatic
    var layoutFile by mutableStateOf<File?>(null)
    @JvmStatic
    var layoutReady by mutableStateOf(false)
    @JvmStatic
    var isEditMode by mutableStateOf(false)
    val runtimeButtonViews = mutableListOf<View>()
    @JvmStatic
    var cursorMode = 0 // 0=Touch, 1=Virtual, 2=Precision
    @JvmStatic
    var virtualCursorActive = false
    @JvmStatic
    var cursorX = 0
    @JvmStatic
    var cursorY = 0
    @JvmStatic
    var crosshairView: View? = null
    @JvmStatic
    var crosshairDragOffsetX = 0
    @JvmStatic
    var crosshairDragOffsetY = 0
}

fun setControlOverlayEditMode(editMode: Boolean) { OverlayState.isEditMode = editMode }
fun getControlOverlayEditMode(): Boolean = OverlayState.isEditMode

private class SimpleSavedStateRegistryOwner : SavedStateRegistryOwner {
    override val lifecycle: Lifecycle = LifecycleRegistry(this)
    override val savedStateRegistry: SavedStateRegistry
    init {
        val ctrl = SavedStateRegistryController.create(this)
        ctrl.performRestore(null)
        savedStateRegistry = ctrl.savedStateRegistry
        (lifecycle as LifecycleRegistry).handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }
}

fun createOverlayComposeView(context: Context): ComposeView {
    val lifecycleOwner = ProcessLifecycleOwner.get()
    val savedStateRegistryOwner = SimpleSavedStateRegistryOwner()
    return ComposeView(context).apply {
        OverlayState.hostView = this
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = false
        setViewTreeLifecycleOwner(lifecycleOwner)
        setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
        setContent { OverlayContent() }
    }
}

fun showEditor(context: Context) {
    hideRuntimeButtons()
    OverlayState.layoutReady = false
    // If there's already a view attached, remove it first
    val existing = OverlayState.hostView
    if (existing != null && existing.isAttachedToWindow) {
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(existing)
        } catch (_: Exception) {}
        OverlayState.hostView = null
    }
    val view = createOverlayComposeView(context)
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val lp = WindowManager.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSPARENT
    )
    try { wm.addView(view, lp) } catch (_: Exception) {}
    OverlayState.isEditMode = true
}

fun hideEditor(context: Context) {
    val view = OverlayState.hostView ?: return
    OverlayState.layoutReady = false
    OverlayState.hostView = null
    if (view.isAttachedToWindow) {
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(view)
        } catch (_: Exception) {}
    }
}

private fun getLayoutFile(context: Context): File {
    return File(context.filesDir, "control_layout.json")
}

fun showRuntimeButtons(context: Context) {
    hideRuntimeButtons()
    val file = getLayoutFile(context)
    if (!file.exists()) return
    val layout = try { loadLayoutFromFile(file) } catch (_: Exception) { return }
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val density = context.resources.displayMetrics.density
    val screenW = context.resources.displayMetrics.widthPixels
    val screenH = context.resources.displayMetrics.heightPixels
    // Per-button toggle state (UUID → pressed)
    val toggleStates = mutableMapOf<String, Boolean>()

    for (layer in layout.layers) {
        if (layer.hide) continue
        for (btn in layer.normalButtons) {
            val btnStyle = btn.buttonStyle?.let { uuid -> layout.styles.find { it.uuid == uuid } } ?: DefaultButtonStyle
            val style = if (btnStyle.commonStyle) btnStyle.lightStyle else btnStyle.lightStyle

            // Compute pixel position and size
            val pos = btn.position
            val size = btn.buttonSize
            val wRaw = when (size.type) {
                ButtonSize.Type.Dp -> (size.widthDp * density).toInt()
                ButtonSize.Type.Percentage -> {
                    val ref = if (size.widthReference == ButtonSize.Reference.ScreenWidth) screenW else screenH
                    (ref * size.widthPercentage / 10000f).toInt()
                }
                ButtonSize.Type.WrapContent -> ViewGroup.LayoutParams.WRAP_CONTENT
            }
            val hRaw = when (size.type) {
                ButtonSize.Type.Dp -> (size.heightDp * density).toInt()
                ButtonSize.Type.Percentage -> {
                    val ref = if (size.heightReference == ButtonSize.Reference.ScreenHeight) screenH else screenW
                    (ref * size.heightPercentage / 10000f).toInt()
                }
                ButtonSize.Type.WrapContent -> ViewGroup.LayoutParams.WRAP_CONTENT
            }
            // Resolve actual pixel dimensions (WRAP_CONTENT → estimate via text)
            val actualW = if (wRaw == ViewGroup.LayoutParams.WRAP_CONTENT) {
                val text = btn.text.default
                var est = 0
                for (c in text) est += 12 + 2 // rough per-char estimate at 12sp
                est + 16
            } else wRaw
            val actualH = if (hRaw == ViewGroup.LayoutParams.WRAP_CONTENT) {
                (40 * density).toInt()
            } else hRaw

            val xPct = pos.xPercentage()
            val yPct = pos.yPercentage()
            val x = ((screenW - actualW) * xPct).toInt()
            val y = ((screenH - actualH) * yPct).toInt()

            // Styled colors as ARGB ints
            fun composeToArgb(c: androidx.compose.ui.graphics.Color, alpha: Float): Int {
                return android.graphics.Color.argb(
                    (c.alpha * alpha * 255).toInt(),
                    (c.red * 255).toInt(),
                    (c.green * 255).toInt(),
                    (c.blue * 255).toInt()
                )
            }
            val bgColor = composeToArgb(style.backgroundColor, style.alpha)
            val txtColor = composeToArgb(style.contentColor, style.alpha)
            val borderColor = composeToArgb(style.borderColor, style.alpha)
            val radiusPx = style.borderRadius.topStart * density
            val borderPx = style.borderWidth * density

            // Render button as bitmap matching the ball-button pattern:
            // ImageView + setOnClickListener in TYPE_APPLICATION_PANEL works reliably.
            val bitmap = Bitmap.createBitmap(actualW, actualH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val rect = RectF(0f, 0f, actualW.toFloat(), actualH.toFloat())
            // Background
            Paint(Paint.ANTI_ALIAS_FLAG).let { p ->
                p.color = bgColor
                canvas.drawRoundRect(rect, radiusPx, radiusPx, p)
            }
            // Border
            if (borderPx > 0) {
                Paint(Paint.ANTI_ALIAS_FLAG).let { p ->
                    p.color = borderColor
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = borderPx
                    val inset = borderPx / 2f
                    canvas.drawRoundRect(
                        RectF(inset, inset, actualW - inset, actualH - inset),
                        radiusPx, radiusPx, p
                    )
                }
            }
            // Text centered
            val text = btn.text.default
            val fontSizePx = if (style.fontSize != null) style.fontSize * density else 12f * density
            Paint(Paint.ANTI_ALIAS_FLAG).let { p ->
                p.color = txtColor
                p.textSize = fontSizePx
                p.textAlign = Paint.Align.CENTER
                val fm = p.fontMetrics
                val baseline = actualH / 2f - (fm.ascent + fm.descent) / 2f
                canvas.drawText(text, actualW / 2f, baseline, p)
            }

            val btnView = ImageView(context).apply {
                setImageBitmap(bitmap)
                setOnClickListener {
                    val uuid = btn.uuid
                    val pressed = if (btn.isToggleable) {
                        val newState = !(toggleStates[uuid] ?: false)
                        toggleStates[uuid] = newState
                        // Visual feedback: dim/green overlay when pressed
                        alpha = if (newState) 0.6f else 1.0f
                        newState
                    } else {
                        true
                    }
                    OBLNativeActivity.routeClickEvents(btn.clickEvents, pressed)
                }
            }

            val lp = WindowManager.LayoutParams().apply {
                gravity = Gravity.TOP or Gravity.START
                width = actualW
                height = actualH
                this.x = x
                this.y = y
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                format = PixelFormat.TRANSLUCENT
            }
            wm.addView(btnView, lp)
            OverlayState.runtimeButtonViews.add(btnView)
        }
    }

    // Virtual cursor toggle button
    val cursorBtnSize = (44 * density).toInt()
    val cursorBitmap = Bitmap.createBitmap(cursorBtnSize, cursorBtnSize, Bitmap.Config.ARGB_8888)
    Canvas(cursorBitmap).apply {
        val cx = cursorBtnSize / 2f
        val cy = cursorBtnSize / 2f
        Paint(Paint.ANTI_ALIAS_FLAG).let { p ->
            p.color = android.graphics.Color.argb(100, 0, 0, 0)
            p.style = Paint.Style.FILL
            drawRoundRect(RectF(0f, 0f, cursorBtnSize.toFloat(), cursorBtnSize.toFloat()), 8f, 8f, p)
        }
        Paint(Paint.ANTI_ALIAS_FLAG).let { p ->
            p.color = android.graphics.Color.WHITE
            p.strokeWidth = 2.5f
            // Crosshair icon
            drawLine(cx, cy - 10f, cx, cy - 3f, p)
            drawLine(cx, cy + 3f, cx, cy + 10f, p)
            drawLine(cx - 10f, cy, cx - 3f, cy, p)
            drawLine(cx + 3f, cy, cx + 10f, cy, p)
            drawCircle(cx, cy, 3f, p.apply { style = Paint.Style.FILL })
        }
    }

    val cursorBtnView = ImageView(context).apply {
        setImageBitmap(cursorBitmap)
        alpha = if (OverlayState.virtualCursorActive) 0.6f else 1.0f
        setOnClickListener {
            val newActive = !OverlayState.virtualCursorActive
            OverlayState.virtualCursorActive = newActive
            OverlayState.cursorMode = if (newActive) 1 else 0
            alpha = if (newActive) 0.6f else 1.0f
            if (newActive) {
                OverlayState.cursorX = screenW / 2
                OverlayState.cursorY = screenH / 2
                showCrosshair(context)
            } else {
                removeCrosshair()
            }
        }
    }

    val cursorLp = WindowManager.LayoutParams().apply {
        gravity = Gravity.TOP or Gravity.START
        width = cursorBtnSize
        height = cursorBtnSize
        x = screenW - cursorBtnSize - 8
        y = 8
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        format = PixelFormat.TRANSLUCENT
    }
    wm.addView(cursorBtnView, cursorLp)
    OverlayState.runtimeButtonViews.add(cursorBtnView)

    // Restore crosshair if active
    if (OverlayState.virtualCursorActive) {
        showCrosshair(context)
    }
}

fun hideRuntimeButtons() {
    removeCrosshair()
    val views = OverlayState.runtimeButtonViews.toList()
    OverlayState.runtimeButtonViews.clear()
    for (v in views) {
        try {
            val wm = v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(v)
        } catch (_: Exception) {}
    }
}

fun removeCrosshair() {
    val oldCrosshair = OverlayState.crosshairView
    if (oldCrosshair != null) {
        OverlayState.crosshairView = null
        try {
            val wm = oldCrosshair.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(oldCrosshair)
        } catch (_: Exception) {}
    }
}

fun showCrosshair(context: Context) {
    removeCrosshair()
    val density = context.resources.displayMetrics.density
    val crosshairSize = (30 * density).toInt()

    val bitmap = Bitmap.createBitmap(crosshairSize, crosshairSize, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).apply {
        val cx = crosshairSize / 2f
        val cy = crosshairSize / 2f
        // Circle
        Paint(Paint.ANTI_ALIAS_FLAG).let { p ->
            p.color = android.graphics.Color.argb(120, 255, 255, 255)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f
            drawCircle(cx, cy, cx - 4f, p)
        }
        // Crosshair lines
        Paint(Paint.ANTI_ALIAS_FLAG).let { p ->
            p.color = android.graphics.Color.argb(180, 255, 255, 255)
            p.strokeWidth = 2f
            drawLine(cx, 2f, cx, cy - 4f, p)
            drawLine(cx, cy + 4f, cx, crosshairSize - 2f, p)
            drawLine(2f, cy, cx - 4f, cy, p)
            drawLine(cx + 4f, cy, crosshairSize - 2f, cy, p)
            // Center dot
            drawCircle(cx, cy, 3f, p.apply { style = Paint.Style.FILL })
        }
    }

    val crosshairView = ImageView(context).apply {
        setImageBitmap(bitmap)
        setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    OverlayState.crosshairDragOffsetX = event.x.toInt()
                    OverlayState.crosshairDragOffsetY = event.y.toInt()
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val rawX = event.rawX.toInt() - OverlayState.crosshairDragOffsetX
                    val rawY = event.rawY.toInt() - OverlayState.crosshairDragOffsetY
                    val screenW = context.resources.displayMetrics.widthPixels
                    val screenH = context.resources.displayMetrics.heightPixels
                    val newX = rawX.coerceIn(0, screenW - crosshairSize)
                    val newY = rawY.coerceIn(0, screenH - crosshairSize)
                    OverlayState.cursorX = newX + crosshairSize / 2
                    OverlayState.cursorY = newY + crosshairSize / 2
                    val lp = v.layoutParams as WindowManager.LayoutParams
                    lp.x = newX
                    lp.y = newY
                    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }
    }

    val crosshairSizePx = crosshairSize
    var startX = OverlayState.cursorX - crosshairSizePx / 2
    var startY = OverlayState.cursorY - crosshairSizePx / 2
    val screenW = context.resources.displayMetrics.widthPixels
    val screenH = context.resources.displayMetrics.heightPixels
    startX = startX.coerceIn(0, screenW - crosshairSizePx)
    startY = startY.coerceIn(0, screenH - crosshairSizePx)

    val lp = WindowManager.LayoutParams().apply {
        gravity = Gravity.TOP or Gravity.START
        width = crosshairSizePx
        height = crosshairSizePx
        this.x = startX
        this.y = startY
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        format = PixelFormat.TRANSLUCENT
    }
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    try { wm.addView(crosshairView, lp) } catch (_: Exception) {}
    OverlayState.crosshairView = crosshairView
}

@Composable
fun OverlayContent() {
    val context = LocalContext.current
    val file = remember { getLayoutFile(context) }
    val viewModel = remember { EditorViewModel() }
    val coroutineScope = rememberCoroutineScope()
    var observableLayout by remember { mutableStateOf<ObservableControlLayout?>(null) }

    DisposableEffect(Unit) {
        val job = coroutineScope.launch {
            val layout = withContext(Dispatchers.IO) {
                if (!file.exists()) {
                    val default = createDefaultLayout()
                    default.saveToFile(file)
                    default
                } else {
                    try {
                        loadLayoutFromFile(file)
                    } catch (_: Exception) {
                        EmptyControlLayout
                    }
                }
            }
            observableLayout = ObservableControlLayout(layout)
            viewModel.initLayout(layout)
            OverlayState.layoutFile = file
            OverlayState.layoutReady = true
        }
        onDispose { job.cancel() }
    }

    if (!OverlayState.layoutReady) return

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xCC000000.toInt())
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                ControlEditor(
                    viewModel = viewModel,
                    targetFile = file,
                    exit = {
                        OverlayState.isEditMode = false
                        showRuntimeButtons(context)
                        hideEditor(context)
                    },
                    menuExit = {
                        viewModel.showExitEditorDialog(
                            context = context,
                            onExit = {
                                OverlayState.isEditMode = false
                                showRuntimeButtons(context)
                                hideEditor(context)
                            }
                        )
                    }
                )
            }
        }
    }
}

private fun createDefaultLayout(): ControlLayout {
    val layer = createNewLayer("Guía")
    val defaultStyles = listOf(
        ButtonStyle(
            name = "Default",
            uuid = "default_style",
            animateSwap = false,
            commonStyle = true,
            lightStyle = DefaultButtonStyleConfig,
            darkStyle = DefaultButtonStyleConfig
        ),
        ButtonStyle(
            name = "Rounded",
            uuid = "rounded_style",
            animateSwap = true,
            commonStyle = true,
            lightStyle = DefaultButtonStyleConfig.copy(
                borderRadius = ButtonShape(8f),
                backgroundColor = Color(0x80000000.toInt()),
                contentColor = Color.White,
                borderWidth = 1,
                borderColor = Color.White
            ),
            darkStyle = DefaultButtonStyleConfig.copy(
                borderRadius = ButtonShape(8f),
                backgroundColor = Color(0x80FFFFFF.toInt()),
                contentColor = Color.Black,
                borderWidth = 1,
                borderColor = Color.Black
            )
        ),
        ButtonStyle(
            name = "Outline",
            uuid = "outline_style",
            animateSwap = true,
            commonStyle = true,
            lightStyle = DefaultButtonStyleConfig.copy(
                backgroundColor = Color.Transparent,
                contentColor = Color.White,
                borderWidth = 2,
                borderColor = Color.White,
                borderRadius = ButtonShape(4f)
            ),
            darkStyle = DefaultButtonStyleConfig.copy(
                backgroundColor = Color.Transparent,
                contentColor = Color.Black,
                borderWidth = 2,
                borderColor = Color.Black,
                borderRadius = ButtonShape(4f)
            )
        ),
        ButtonStyle(
            name = "Pill",
            uuid = "pill_style",
            animateSwap = true,
            commonStyle = true,
            lightStyle = DefaultButtonStyleConfig.copy(
                borderRadius = ButtonShape(50f),
                backgroundColor = Color(0xCC2196F3.toInt()),
                contentColor = Color.White
            ),
            darkStyle = DefaultButtonStyleConfig.copy(
                borderRadius = ButtonShape(50f),
                backgroundColor = Color(0xCC1976D2.toInt()),
                contentColor = Color.White
            )
        )
    )
    return ControlLayout(
        info = ControlLayout.Info(
            name = createTranslatable("OBlender Controls"),
            author = createTranslatable("User"),
            description = createTranslatable("Default control layout"),
            versionCode = 1,
            versionName = "1.0"
        ),
        layers = listOf(layer),
        editorVersion = EDITOR_VERSION,
        styles = defaultStyles
    )
}
