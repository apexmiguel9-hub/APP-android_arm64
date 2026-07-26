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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.movtery.layer_controller.ControlBoxLayout
import com.movtery.layer_controller.event.EventHandler
import com.movtery.layer_controller.layout.EmptyControlLayout
import com.movtery.layer_controller.layout.loadLayoutFromFile
import com.movtery.layer_controller.utils.saveToFile
import com.movtery.layer_controller.observable.ObservableControlLayout
import com.movtery.layer_controller.data.HideLayerWhen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private class SimpleSavedStateRegistryOwner(
    lifecycleOwner: LifecycleOwner
) : SavedStateRegistryOwner {
    override val lifecycle = lifecycleOwner.lifecycle
    private val controller = SavedStateRegistryController.create(this).also {
        it.performRestore(null)
    }
    override val savedStateRegistry: SavedStateRegistry
        get() = controller.savedStateRegistry
}

fun createControlOverlayView(context: Context): ComposeView {
    val lifecycleOwner = ProcessLifecycleOwner.get()
    val savedStateRegistryOwner = SimpleSavedStateRegistryOwner(lifecycleOwner)

    return ComposeView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
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
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val layoutFile = remember { getLayoutFile(context) }

    DisposableEffect(Unit) {
        val job = coroutineScope.launch {
            val layout = withContext(Dispatchers.IO) {
                if (!layoutFile.exists()) {
                    EmptyControlLayout.saveToFile(layoutFile)
                }
                try {
                    loadLayoutFromFile(layoutFile)
                } catch (e: Exception) {
                    EmptyControlLayout
                }
            }
            observedLayout = ObservableControlLayout(layout)
        }
        onDispose { job.cancel() }
    }

    observedLayout?.let { layout ->
        ControlBoxLayout(
            modifier = Modifier.fillMaxSize(),
            observedLayout = layout,
            eventHandler = EventHandler(),
            isUsingJoystick = false,
            isCursorGrabbing = false,
            checkOccupiedPointers = { false },
            opacity = 1f,
            hideLayerWhen = HideLayerWhen.None,
            content = { }
        )
    }
}
