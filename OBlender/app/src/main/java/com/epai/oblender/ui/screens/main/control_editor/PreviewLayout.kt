package com.epai.oblender.ui.screens.main.control_editor

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.layer_controller.ControlBoxLayout
import com.movtery.layer_controller.data.HideLayerWhen
import com.movtery.layer_controller.observable.ObservableControlLayout
import com.epai.oblender.ui.components.rememberBoxSize

@Composable
fun BoxWithConstraintsScope.PreviewControlBox(
    observableLayout: ObservableControlLayout,
    previewScenario: PreviewScenario,
    previewHideLayerWhen: HideLayerWhen,
    enableJoystick: Boolean,
    modifier: Modifier = Modifier,
) {
    val occupiedPointers = remember(observableLayout) { mutableStateSetOf<PointerId>() }
    val moveOnlyPointers = remember(observableLayout) { mutableStateSetOf<PointerId>() }
    val screenSize = rememberBoxSize()

    ControlBoxLayout(
        modifier = modifier.fillMaxSize(), observedLayout = observableLayout,
        checkOccupiedPointers = { occupiedPointers.contains(it) },
        markPointerAsMoveOnly = { moveOnlyPointers.add(it) },
        isUsingJoystick = previewScenario.isCursorGrabbing && enableJoystick,
        isCursorGrabbing = previewScenario.isCursorGrabbing,
        hideLayerWhen = previewHideLayerWhen, isDark = false
    ) {
        // Simplified preview without mouse/joystick layouts
    }
}
