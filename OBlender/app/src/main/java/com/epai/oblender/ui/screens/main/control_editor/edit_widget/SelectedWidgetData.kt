package com.epai.oblender.ui.screens.main.control_editor.edit_widget

import com.movtery.layer_controller.observable.ObservableControlLayer
import com.movtery.layer_controller.observable.ObservableWidget

data class SelectedWidgetData(
    val data: ObservableWidget,
    val layer: ObservableControlLayer
)
