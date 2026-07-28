package com.epai.oblender

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
import com.movtery.layer_controller.event.EventHandler
import com.movtery.layer_controller.ControlBoxLayout
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
}

/** Java bridge for OverlayState.isEditMode */
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

    // Reactively update touch modal when edit mode changes (from ball button toggle in Java)
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
                        },
                        menuExit = {
                            viewModel.showExitEditorDialog(
                                context = context,
                                onExit = {
                                    OverlayState.isEditMode = false
                                    updateTouchModal(true)
                                }
                            )
                        }
                    )
                }
            }
        } else {
            ControlBoxLayout(
                modifier = Modifier.fillMaxSize(),
                observedLayout = observableLayout,
                isUsingJoystick = false,
                isCursorGrabbing = false,
                checkOccupiedPointers = { false },
                eventHandler = EventHandler { event, pressed ->
                    // Route key events to Blender via OBLNativeActivity
                }
            ) { }
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
                borderWidth = 1f,
                borderColor = Color.White
            ),
            darkStyle = DefaultButtonStyleConfig.copy(
                borderRadius = ButtonShape(8f),
                backgroundColor = Color(0x80FFFFFF.toInt()),
                contentColor = Color.Black,
                borderWidth = 1f,
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
                borderWidth = 2f,
                borderColor = Color.White,
                borderRadius = ButtonShape(4f)
            ),
            darkStyle = DefaultButtonStyleConfig.copy(
                backgroundColor = Color.Transparent,
                contentColor = Color.Black,
                borderWidth = 2f,
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
