package com.epai.oblender

import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.movtery.layer_controller.ControlEditorLayer
import com.movtery.layer_controller.EDITOR_VERSION
import com.movtery.layer_controller.data.*
import com.movtery.layer_controller.data.lang.createTranslatable
import com.movtery.layer_controller.layout.ControlLayout
import com.movtery.layer_controller.layout.EmptyControlLayout
import com.movtery.layer_controller.layout.createNewLayer
import com.movtery.layer_controller.layout.loadLayoutFromFile
import com.movtery.layer_controller.observable.ObservableControlLayer
import com.movtery.layer_controller.observable.ObservableControlLayout
import com.movtery.layer_controller.observable.ObservableWidget
import com.movtery.layer_controller.utils.saveToFile
import com.movtery.layer_controller.utils.snap.SnapMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
        setContent {
            ControlOverlayContent()
        }
    }
}

private fun getLayoutFile(context: Context): File {
    return File(context.filesDir, "control_layout.json")
}

@Composable
fun ControlOverlayContent() {
    var observedLayout by remember { mutableStateOf<ObservableControlLayout?>(null) }
    var selectedWidget by remember { mutableStateOf<ObservableWidget?>(null) }
    var selectedWidgetLayer by remember { mutableStateOf<ObservableControlLayer?>(null) }
    var showLayerPanel by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val layoutFile = remember { getLayoutFile(context) }

    DisposableEffect(Unit) {
        val job = coroutineScope.launch {
            val layout = withContext(Dispatchers.IO) {
                if (!layoutFile.exists()) {
                    val default = createDefaultLayout()
                    default.saveToFile(layoutFile)
                    default
                } else {
                    try {
                        loadLayoutFromFile(layoutFile)
                    } catch (e: Exception) {
                        EmptyControlLayout
                    }
                }
            }
            observedLayout = ObservableControlLayout(layout)
        }
        onDispose { job.cancel() }
    }

    observedLayout?.let { layout ->
        Column(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))) {
            // Top toolbar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1E1E1E),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ToolbarButton("+ Btn") {
                            addNormalButton(layout)
                        }
                        ToolbarButton("+ Text") {
                            addTextBox(layout)
                        }
                        ToolbarButton("Layers") {
                            showLayerPanel = !showLayerPanel
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ToolbarButton("Save") {
                            saveLayout(layout, layoutFile, coroutineScope)
                        }
                        ToolbarButton("✕") {
                            closeOverlay(context)
                        }
                    }
                }
            }

            // Main content: editor canvas + layer panel
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ControlEditorLayer(
                    observedLayout = layout,
                    selectedWidget = selectedWidget,
                    onButtonTap = { widget, layer ->
                        selectedWidget = widget
                        selectedWidgetLayer = layer
                    },
                    onBackgroundClick = {
                        selectedWidget = null
                        selectedWidgetLayer = null
                    },
                    floatingButtons = {
                        selectedWidget?.let { widget ->
                            FloatingActionRow(
                                onDelete = {
                                    selectedWidgetLayer?.let { layer ->
                                        deleteWidget(layer, widget)
                                    }
                                    selectedWidget = null
                                    selectedWidgetLayer = null
                                },
                                onClone = {
                                    cloneWidget(layout, widget)
                                }
                            )
                        }
                    },
                    enableSnap = true,
                    snapInAllLayers = false,
                    snapMode = SnapMode.FullScreen,
                    isDark = true
                )

                // Layer panel overlay
                if (showLayerPanel) {
                    Surface(
                        modifier = Modifier
                            .width(200.dp)
                            .fillMaxHeight()
                            .align(Alignment.CenterStart),
                        color = Color(0xFF1E1E1E),
                        tonalElevation = 8.dp
                    ) {
                        LayerPanel(
                            layout = layout,
                            onAddLayer = {
                                val newLayer = layout.addLayer(createNewLayer("Layer ${layout.layers.value.size + 1}"))
                                layout.layers.value.lastOrNull()?.let { prev ->
                                    newLayer.visibilityType = prev.visibilityType
                                }
                            },
                            onSelectLayer = { layer ->
                                showLayerPanel = false
                            }
                        )
                    }
                }
            }

            // Bottom hint
            if (selectedWidget == null && !showLayerPanel) {
                Text(
                    text = "Tap a button to edit | + Btn / + Text to add",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                )
            }
        }
    }
}

@Composable
private fun ToolbarButton(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun FloatingActionRow(onDelete: () -> Unit, onClone: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(4.dp)
    ) {
        SmallFloatingActionButton(
            onClick = onDelete,
            containerColor = Color(0xFFE53935),
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Text("Del", fontSize = 12.sp)
        }
        SmallFloatingActionButton(
            onClick = onClone,
            containerColor = Color(0xFF1565C0),
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Text("Cpy", fontSize = 12.sp)
        }
    }
}

@Composable
private fun LayerPanel(
    layout: ObservableControlLayout,
    onAddLayer: () -> Unit,
    onSelectLayer: (ObservableControlLayer) -> Unit
) {
    val layers by layout.layers.collectAsState()
    Column(modifier = Modifier.padding(12.dp)) {
        Text(
            text = "Layers",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(layers) { layer ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF333333),
                    onClick = { onSelectLayer(layer) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = layer.name,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${layer.normalButtons.value.size + layer.textBoxes.value.size} widgets",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onAddLayer,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("+ Add Layer", fontSize = 13.sp)
        }
    }
}

// Actions
private fun addNormalButton(layout: ObservableControlLayout) {
    val firstLayer = layout.layers.value.firstOrNull() ?: return
    val newBtn = createWidgetWithUUID { uuid ->
        NormalData(
            text = createTranslatable(default = "Btn"),
            uuid = uuid,
            position = ButtonPosition(
                (3500..6500).random(),
                (3000..6000).random()
            ),
            buttonSize = ButtonSize(
                type = ButtonSize.Type.Percentage,
                widthDp = 50f, heightDp = 50f,
                widthPercentage = 600, heightPercentage = 500,
                widthReference = ButtonSize.Reference.ScreenHeight,
                heightReference = ButtonSize.Reference.ScreenHeight
            ),
            visibilityType = VisibilityType.ALWAYS,
            isSwipple = false,
            isPenetrable = false,
            isToggleable = false
        )
    }
    firstLayer.addNormalButton(newBtn)
}

private fun addTextBox(layout: ObservableControlLayout) {
    val firstLayer = layout.layers.value.firstOrNull() ?: return
    val newText = createWidgetWithUUID { uuid ->
        TextData(
            text = createTranslatable(default = "Text"),
            uuid = uuid,
            position = ButtonPosition(
                (3500..6500).random(),
                (3000..6000).random()
            ),
            buttonSize = ButtonSize(
                type = ButtonSize.Type.WrapContent,
                widthDp = 80f, heightDp = 40f,
                widthPercentage = 800, heightPercentage = 400,
                widthReference = ButtonSize.Reference.ScreenWidth,
                heightReference = ButtonSize.Reference.ScreenHeight
            ),
            visibilityType = VisibilityType.ALWAYS
        )
    }
    firstLayer.addTextBox(newText)
}

private fun deleteWidget(layer: ObservableControlLayer, widget: ObservableWidget) {
    val uuid = when (widget) {
        is com.movtery.layer_controller.observable.ObservableNormalData -> widget.uuid
        is com.movtery.layer_controller.observable.ObservableTextData -> widget.uuid
        else -> return
    }
    layer.removeNormalButton(uuid)
    layer.removeTextBox(uuid)
}

private fun cloneWidget(layout: ObservableControlLayout, widget: ObservableWidget) {
    val firstLayer = layout.layers.value.firstOrNull() ?: return
    when (widget) {
        is com.movtery.layer_controller.observable.ObservableNormalData -> {
            val clone = widget.packNormal().cloneNew()
            firstLayer.addNormalButton(clone)
        }
        is com.movtery.layer_controller.observable.ObservableTextData -> {
            val clone = widget.packText().cloneNew()
            firstLayer.addTextBox(clone)
        }
    }
}

private fun saveLayout(
    layout: ObservableControlLayout,
    layoutFile: File,
    scope: kotlinx.coroutines.CoroutineScope
) {
    scope.launch {
        withContext(Dispatchers.IO) {
            try {
                val packed = layout.pack()
                packed.saveToFile(layoutFile)
            } catch (e: Exception) {
                android.util.Log.e("OBL.Overlay", "save failed", e)
            }
        }
    }
}

private fun closeOverlay(context: Context) {
    android.util.Log.d("OBL.Overlay", "close requested")
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


