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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.movtery.layer_controller.layout.ControlLayout
import com.movtery.layer_controller.layout.EmptyControlLayout
import com.movtery.layer_controller.layout.createNewLayer
import com.movtery.layer_controller.layout.loadLayoutFromFile
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
                floatingButtons = {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            modifier = Modifier.padding(4.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF4CAF50)
                        ) {
                            Text(
                                text = "＋",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                },
                enableSnap = true,
                snapInAllLayers = false,
                snapMode = SnapMode.FullScreen
            )
        }
    }
}

private fun createDefaultLayout(): ControlLayout {
    val layer = createNewLayer("Guía")
    return ControlLayout(
        info = ControlLayout.Info(name = "OBlender Controls"),
        layers = listOf(layer),
        editorVersion = com.movtery.layer_controller.EDITOR_VERSION
    )
}
