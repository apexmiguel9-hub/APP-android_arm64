package com.epai.oblender

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
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
    @JvmStatic
    var runtimeContainer: FrameLayout? = null
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

    // First pass: collect all visible button screen positions+sizes to compute bounding box,
    // and build all button views with their screen-absolute positions.
    data class BtnSlot(val view: TextView, val screenX: Int, val screenY: Int, val w: Int, val h: Int)
    val slots = mutableListOf<BtnSlot>()

    for (layer in layout.layers) {
        if (layer.hide) continue
        for (btn in layer.normalButtons) {
            val btnStyle = btn.buttonStyle?.let { uuid -> layout.styles.find { it.uuid == uuid } } ?: DefaultButtonStyle
            val style = if (btnStyle.commonStyle) btnStyle.lightStyle else btnStyle.lightStyle

            val btnView = TextView(context).apply {
                text = btn.text.default
                gravity = Gravity.CENTER
                setOnClickListener {
                    val ev = btn.clickEvents.firstOrNull()
                    if (ev != null) {
                        OBLNativeActivity.routeClickEvent(ev)
                    }
                }
            }
            // Build styled background drawable
            val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
                val bdr = style.borderRadius
                cornerRadii = floatArrayOf(
                    bdr.topStart * density, bdr.topStart * density,
                    bdr.topEnd * density, bdr.topEnd * density,
                    bdr.bottomEnd * density, bdr.bottomEnd * density,
                    bdr.bottomStart * density, bdr.bottomStart * density
                )
                setColor(style.backgroundColor.copy(alpha = style.backgroundColor.alpha * style.alpha).toArgb())
                if (style.borderWidth > 0) {
                    setStroke(
                        (style.borderWidth * density).toInt(),
                        style.borderColor.copy(alpha = style.borderColor.alpha * style.alpha).toArgb()
                    )
                }
            }
            btnView.background = bgDrawable
            btnView.setTextColor(style.contentColor.copy(alpha = style.contentColor.alpha * style.alpha).toArgb())
            if (style.fontSize != null) {
                btnView.textSize = style.fontSize.toFloat()
            }
            btnView.setPadding(8, 4, 8, 4)

            // Compute pixel position and size
            val pos = btn.position
            val size = btn.buttonSize
            val w = when (size.type) {
                ButtonSize.Type.Dp -> (size.widthDp * density).toInt()
                ButtonSize.Type.Percentage -> {
                    val ref = if (size.widthReference == ButtonSize.Reference.ScreenWidth) screenW else screenH
                    (ref * size.widthPercentage / 10000f).toInt()
                }
                ButtonSize.Type.WrapContent -> ViewGroup.LayoutParams.WRAP_CONTENT
            }
            val h = when (size.type) {
                ButtonSize.Type.Dp -> (size.heightDp * density).toInt()
                ButtonSize.Type.Percentage -> {
                    val ref = if (size.heightReference == ButtonSize.Reference.ScreenHeight) screenH else screenW
                    (ref * size.heightPercentage / 10000f).toInt()
                }
                ButtonSize.Type.WrapContent -> ViewGroup.LayoutParams.WRAP_CONTENT
            }
            val xPct = pos.xPercentage()
            val yPct = pos.yPercentage()
            val x = ((screenW - (if (w == ViewGroup.LayoutParams.WRAP_CONTENT) 0 else w)) * xPct).toInt()
            val y = ((screenH - (if (h == ViewGroup.LayoutParams.WRAP_CONTENT) 0 else h)) * yPct).toInt()

            slots.add(BtnSlot(btnView, x, y, w, h))
        }
    }

    if (slots.isEmpty()) return

    // Compute bounding box of all buttons (container will be exactly this rect)
    var minX = Int.MAX_VALUE
    var minY = Int.MAX_VALUE
    var maxX = Int.MIN_VALUE
    var maxY = Int.MIN_VALUE
    val estMinW = (72 * density).toInt()
    val estMinH = (36 * density).toInt()
    for (s in slots) {
        val sx = if (s.w == ViewGroup.LayoutParams.WRAP_CONTENT) 0 else s.w
        val sy = if (s.h == ViewGroup.LayoutParams.WRAP_CONTENT) 0 else s.h
        if (s.screenX < minX) minX = s.screenX
        if (s.screenY < minY) minY = s.screenY
        if (s.screenX + sx > maxX) maxX = s.screenX + sx
        if (s.screenY + sy > maxY) maxY = s.screenY + sy
    }
    var cw = maxX - minX
    var ch = maxY - minY
    if (cw <= 0) cw = 1
    if (ch <= 0) ch = 1

    // Create container sized to bounding box, positioned at (minX, minY)
    val container = FrameLayout(context).apply {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = false
        isFocusable = false
    }
    for (s in slots) {
        val lp = FrameLayout.LayoutParams(
            if (s.w == ViewGroup.LayoutParams.WRAP_CONTENT) ViewGroup.LayoutParams.WRAP_CONTENT else s.w,
            if (s.h == ViewGroup.LayoutParams.WRAP_CONTENT) ViewGroup.LayoutParams.WRAP_CONTENT else s.h
        ).apply {
            leftMargin = s.screenX - minX
            topMargin = s.screenY - minY
        }
        container.addView(s.view, lp)
    }

    val containerLp = WindowManager.LayoutParams(
        cw, ch,
        WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSPARENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = minX
        y = minY
    }
    wm.addView(container, containerLp)
    OverlayState.runtimeContainer = container
}

fun hideRuntimeButtons() {
    OverlayState.runtimeContainer?.let { container ->
        OverlayState.runtimeContainer = null
        if (container.isAttachedToWindow) {
            try {
                val wm = container.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(container)
            } catch (_: Exception) {}
        }
    }
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
