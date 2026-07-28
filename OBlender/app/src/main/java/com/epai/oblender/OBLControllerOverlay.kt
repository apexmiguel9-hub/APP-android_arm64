package com.epai.oblender

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
}

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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xCC000000)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            ControlEditor(
                viewModel = viewModel,
                targetFile = file,
                exit = {
                    removeEditorView()
                },
                menuExit = {
                    viewModel.showExitEditorDialog(
                        context = context,
                        onExit = {
                            removeEditorView()
                        }
                    )
                }
            )
        }
    }
}

private fun removeEditorView() {
    OverlayState.hostView?.let { v ->
        try {
            val wm = v.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            wm?.removeView(v)
        } catch (_: Exception) {}
    }
    OverlayState.hostView = null
}

private fun createDefaultLayout(): ControlLayout {
    val layer = createNewLayer("Guía")
    return ControlLayout(
        info = ControlLayout.Info(
            name = createTranslatable("OBlender Controls"),
            author = createTranslatable("User"),
            description = createTranslatable("Default control layout"),
            versionCode = 1,
            versionName = "1.0"
        ),
        layers = listOf(layer),
        editorVersion = EDITOR_VERSION
    )
}
