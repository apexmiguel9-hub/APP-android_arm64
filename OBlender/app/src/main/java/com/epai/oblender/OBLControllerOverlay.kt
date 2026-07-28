package com.epai.oblender

import android.content.Context
import androidx.compose.ui.graphics.Color
import android.view.ViewGroup
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.movtery.layer_controller.EDITOR_VERSION
import com.movtery.layer_controller.ControlBoxLayout
import com.movtery.layer_controller.data.HideLayerWhen
import com.movtery.layer_controller.data.lang.createTranslatable
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.layer_controller.event.EventHandler
import com.movtery.layer_controller.layout.ControlLayout
import com.movtery.layer_controller.layout.EmptyControlLayout
import com.movtery.layer_controller.layout.createNewLayer
import com.movtery.layer_controller.layout.loadLayoutFromFile
import com.movtery.layer_controller.observable.ObservableControlLayout
import com.movtery.layer_controller.utils.saveToFile
import com.epai.oblender.ui.components.FloatingBall
import com.epai.oblender.ui.components.MarqueeText
import com.epai.oblender.ui.screens.main.control_editor.ControlEditor
import com.epai.oblender.viewmodel.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object OverlayState {
    @JvmStatic
    var showEditor by mutableStateOf(false)
    @JvmStatic
    var showMenu by mutableStateOf(false)
    @JvmStatic
    var layoutFile by mutableStateOf<File?>(null)
    @JvmStatic
    var layoutReady by mutableStateOf(false)

    private var _eventHandler: EventHandler = EventHandler { event, pressed ->
        sendKeyEvent(event, pressed)
    }

    @JvmStatic
    val eventHandler: EventHandler get() = _eventHandler

    @JvmStatic
    fun setNatives(
        setValue: (String) -> Unit,
        setValueOn: (String) -> Unit,
        setValueOff: (String) -> Unit
    ) {
        _eventHandler = EventHandler { event, pressed ->
            sendKeyEvent(event, pressed)
        }
    }
}

private fun sendKeyEvent(event: ClickEvent, pressed: Boolean) {
    android.util.Log.d("OBL.Widget", "Event: ${event.type} key=${event.key} pressed=$pressed")
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

    // Widget display ALWAYS behind everything else
    ControlBoxLayout(
        modifier = Modifier.fillMaxSize(),
        observedLayout = observableLayout,
        eventHandler = OverlayState.eventHandler,
        isUsingJoystick = false,
        isCursorGrabbing = false,
        checkOccupiedPointers = { false },
        opacity = 1f,
        hideLayerWhen = HideLayerWhen.None,
        isDark = false
    ) {
    }

    // Game ball (always visible, toggles menu)
    FloatingBall(
        position = Offset(120f, 120f),
        onPositionChanged = {},
        onSavePos = {},
        onClick = { OverlayState.showMenu = !OverlayState.showMenu },
        alpha = 1f,
        color = Color(0x64404040),
        contentColor = Color.White.copy(alpha = 0.95f)
    ) {
        MarqueeText(
            text = "M",
            style = MaterialTheme.typography.titleMedium
        )
    }

    // Game menu (toggled by ball)
    if (OverlayState.showMenu) {
        GameMenuScreen(
            onEditLayout = {
                OverlayState.showMenu = false
                OverlayState.showEditor = true
            },
            onClose = { OverlayState.showMenu = false }
        )
    }

    // Editor (full screen, replaces everything)
    if (OverlayState.showEditor) {
        OverlayState.layoutFile?.let { target ->
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                ControlEditor(
                    viewModel = viewModel,
                    targetFile = target,
                    exit = {
                        OverlayState.showEditor = false
                        reloadLayout(coroutineScope, file) { observableLayout = it }
                    },
                    menuExit = {
                        viewModel.showExitEditorDialog(
                            context = context,
                            onExit = {
                                OverlayState.showEditor = false
                                reloadLayout(coroutineScope, file) { observableLayout = it }
                            }
                        )
                    }
                )
            }
        }
    }
}

private fun reloadLayout(scope: kotlinx.coroutines.CoroutineScope, file: File, onLoaded: (ObservableControlLayout) -> Unit) {
    scope.launch {
        val layout = withContext(Dispatchers.IO) {
            try {
                loadLayoutFromFile(file)
            } catch (_: Exception) {
                EmptyControlLayout
            }
        }
        onLoaded(ObservableControlLayout(layout))
    }
}

@Composable
private fun GameMenuScreen(
    onEditLayout: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xB4000000)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            val listState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState
            ) {
                item {
                    TextButton(
                        onClick = onEditLayout,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MarqueeText(text = "Editar diseño de controles")
                    }
                }
                item { HorizontalDivider() }
                item {
                    TextButton(
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MarqueeText(text = "Cerrar")
                    }
                }
            }
        }
    }
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
