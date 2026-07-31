package com.epai.oblender

import android.content.Context
import android.os.Handler
import android.os.Looper
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
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
    @JvmField
    var virtualCursorActive = false
    @JvmField
    var cursorX = 0
    @JvmField
    var cursorY = 0
    @JvmField
    var virtualPointerView: View? = null
    @JvmField
    var leftMouseHeld = false
}

fun setControlOverlayEditMode(editMode: Boolean) { OverlayState.isEditMode = editMode }
fun getControlOverlayEditMode(): Boolean = OverlayState.isEditMode

internal class SimpleSavedStateRegistryOwner : SavedStateRegistryOwner {
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

fun showRuntimeButtons(context: Context, lifecycleOwner: LifecycleOwner) {
    hideRuntimeButtons()
    CursorModeManager.init(context)
    val file = getLayoutFile(context)
    if (!file.exists()) return
    val layout = try { loadLayoutFromFile(file) } catch (_: Exception) { return }
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val density = context.resources.displayMetrics.density
    val screenW = context.resources.displayMetrics.widthPixels
    val screenH = context.resources.displayMetrics.heightPixels

    // Show virtual pointer overlay FIRST (below buttons) if mode is Virtual
    val mode = CursorModeManager.getMode()
    android.util.Log.d("OBL", "showRuntimeButtons: cursorMode=$mode")
    if (mode == CURSOR_MODE_VIRTUAL) {
        OverlayState.virtualCursorActive = true
        android.util.Log.d("OBL", "showRuntimeButtons: showing virtual pointer overlay")
        showVirtualPointerOverlay(context, lifecycleOwner)
    }

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
            CursorModeManager.setMode(if (newActive) CURSOR_MODE_VIRTUAL else CURSOR_MODE_TOUCH)
            alpha = if (newActive) 0.6f else 1.0f
            if (newActive) {
        showVirtualPointerOverlay(context, lifecycleOwner)
            } else {
                hideVirtualPointerOverlay()
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

}

fun hideRuntimeButtons() {
    hideVirtualPointerOverlay()
    val views = OverlayState.runtimeButtonViews.toList()
    OverlayState.runtimeButtonViews.clear()
    for (v in views) {
        try {
            val wm = v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(v)
        } catch (_: Exception) {}
    }
}

fun showVirtualPointerOverlay(context: Context, lifecycleOwner: LifecycleOwner) {
    hideVirtualPointerOverlay()
    OverlayState.virtualPointerView = createVirtualPointerOverlay(context, lifecycleOwner)
}

fun hideVirtualPointerOverlay() {
    val old = OverlayState.virtualPointerView ?: return
    android.util.Log.d("OBL", "hideVirtualPointerOverlay: removing view, thread=${Thread.currentThread().name}")
    
    Handler(Looper.getMainLooper()).post {
        try {
            val wm = old.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(old)
            android.util.Log.d("OBL", "hideVirtualPointerOverlay: removed successfully")
        } catch (e: Exception) {
            android.util.Log.e("OBL", "hideVirtualPointerOverlay: REMOVE FAILED", e)
        }
    }
    OverlayState.virtualPointerView = null
}

@Composable
fun OverlayContent() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
                        showRuntimeButtons(context, lifecycleOwner)
                        hideEditor(context)
                    },
                    menuExit = {
                        viewModel.showExitEditorDialog(
                            context = context,
                            onExit = {
                                OverlayState.isEditMode = false
                                showRuntimeButtons(context, lifecycleOwner)
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
