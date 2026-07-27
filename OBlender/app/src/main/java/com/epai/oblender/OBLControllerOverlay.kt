/*
 * OBlender — Unofficial Blender 3.6 Port for Android ARM64 (MediaTek/Mali)
 * Control overlay using Zalith Launcher 2's LayerController module.
 *
 * Copyright (C) 2025 MovTery <movtery228@qq.com> (LayerController)
 * Copyright (C) 2025 Contributors to Zalith Launcher 2
 *   https://github.com/ZalithLauncher/ZalithLauncher2
 *
 * Adapted for OBlender under GPL-3.0.
 * This is NOT an official Zalith Launcher 2 product.
 */
package com.epai.oblender

import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.movtery.layer_controller.ControlEditorLayer
import com.movtery.layer_controller.EDITOR_VERSION
import com.movtery.layer_controller.data.ButtonSize
import com.movtery.layer_controller.data.CenterPosition
import com.movtery.layer_controller.data.NormalData
import com.movtery.layer_controller.data.VisibilityType
import com.movtery.layer_controller.data.createWidgetWithUUID
import com.movtery.layer_controller.data.lang.createTranslatable
import com.movtery.layer_controller.layout.ControlLayout
import com.movtery.layer_controller.layout.EmptyControlLayout
import com.movtery.layer_controller.layout.createNewLayer
import com.movtery.layer_controller.layout.loadLayoutFromFile
import com.movtery.layer_controller.observable.ObservableControlLayer
import com.movtery.layer_controller.utils.saveToFile
import com.movtery.layer_controller.utils.snap.SnapMode
import com.movtery.layer_controller.observable.ObservableControlLayout
import com.movtery.layer_controller.observable.ObservableWidget
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
        ) {
            ControlEditorLayer(
                observedLayout = layout,
                selectedWidget = selectedWidget,
                onButtonTap = { widget, _ -> selectedWidget = widget },
                onBackgroundClick = { selectedWidget = null },
                floatingButtons = { },
                enableSnap = true,
                snapInAllLayers = false,
                snapMode = SnapMode.FullScreen
            )

            /* Toolbar at bottom: + button to add a new button */
            Surface(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.BottomCenter)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        val firstLayer = layout.layers.value.firstOrNull()
                        if (firstLayer != null) {
                            val newBtn = createWidgetWithUUID { uuid ->
                                NormalData(
                                    text = createTranslatable(default = "Btn"),
                                    uuid = uuid,
                                    position = CenterPosition,
                                    buttonSize = ButtonSize(
                                        type = ButtonSize.Type.Percentage,
                                        widthDp = 50f, heightDp = 50f,
                                        widthPercentage = 500, heightPercentage = 500,
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
                    },
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF4CAF50)
            ) {
                Text(
                    text = "＋ Añadir Botón",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }

            /* Close hint at top */
            Text(
                text = "Toca un botón para editarlo | + abajo para añadir",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopCenter)
            )
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
