package com.epai.oblender

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
    /** Runtime button views managed from Java/Kotlin */
    val runtimeButtonViews = mutableListOf<View>()
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

fun createControlOverlayView(context: Context): ComposeView {
    val lifecycleOwner = ProcessLifecycleOwner.get()
    val savedStateRegistryOwner = SimpleSavedStateRegistryOwner()
    return ComposeView(context).apply {
        OverlayState.hostView = this
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        setViewTreeLifecycleOwner(lifecycleOwner)
        setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
        setContent { OverlayContent() }
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

    for (layer in layout.layers) {
        if (layer.hide) continue
        for (btn in layer.normalButtons) {
            val btnView = TextView(context).apply {
                text = btn.text.default
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(android.graphics.Color.argb(180, 64, 64, 64))
                gravity = Gravity.CENTER
                textSize = 12f
                setPadding(8, 4, 8, 4)
                setOnClickListener {
                    // Route first ClickEvent key to Blender
                    val ev = btn.clickEvents.firstOrNull()
                    if (ev != null) {
                        OBLNativeActivity.routeClickEvent(ev)
                    }
                }
            }
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

            val lp = WindowManager.LayoutParams(
                if (w == ViewGroup.LayoutParams.WRAP_CONTENT) ViewGroup.LayoutParams.WRAP_CONTENT else w,
                if (h == ViewGroup.LayoutParams.WRAP_CONTENT) ViewGroup.LayoutParams.WRAP_CONTENT else h,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = x
                this.y = y
            }
            wm.addView(btnView, lp)
            OverlayState.runtimeButtonViews.add(btnView)
        }
    }
}

fun hideRuntimeButtons() {
    val views = OverlayState.runtimeButtonViews.toList()
    OverlayState.runtimeButtonViews.clear()
    for (v in views) {
        try {
            val wm = v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(v)
        } catch (_: Exception) {}
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

    // Reactively update touch modal when edit mode changes
    LaunchedEffect(OverlayState.isEditMode) {
        updateTouchModal(!OverlayState.isEditMode)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (OverlayState.isEditMode) {
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
                            updateTouchModal(true)
                            showRuntimeButtons(context)
                        },
                        menuExit = {
                            viewModel.showExitEditorDialog(
                                context = context,
                                onExit = {
                                    OverlayState.isEditMode = false
                                    updateTouchModal(true)
                                    showRuntimeButtons(context)
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

private fun updateTouchModal(touchModal: Boolean) {
    val view = OverlayState.hostView ?: return
    val wm = view.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
    val lp = view.layoutParams as? WindowManager.LayoutParams ?: return
    if (touchModal) {
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
    } else {
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
    }
    try { wm.updateViewLayout(view, lp) } catch (_: Exception) {}
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
