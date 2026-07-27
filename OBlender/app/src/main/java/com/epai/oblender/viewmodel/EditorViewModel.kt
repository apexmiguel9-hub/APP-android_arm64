package com.epai.oblender.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.movtery.layer_controller.data.HideLayerWhen
import com.movtery.layer_controller.layout.ControlLayout
import com.movtery.layer_controller.observable.ObservableButtonStyle
import com.movtery.layer_controller.observable.ObservableControlLayer
import com.movtery.layer_controller.observable.ObservableControlLayout
import com.movtery.layer_controller.observable.ObservableNormalData
import com.movtery.layer_controller.observable.ObservableTextData
import com.movtery.layer_controller.observable.ObservableWidget
import com.movtery.layer_controller.observable.cloneNormal
import com.movtery.layer_controller.observable.cloneText
import com.movtery.layer_controller.utils.saveToFile
import com.epai.oblender.ui.components.MenuState
import com.epai.oblender.ui.screens.main.control_editor.EditorOperation
import com.epai.oblender.ui.screens.main.control_editor.EditorWarningOperation
import com.epai.oblender.ui.screens.main.control_editor.EditorWidgetOperation
import com.epai.oblender.ui.screens.main.control_editor.PreviewScenario
import com.epai.oblender.ui.screens.main.control_editor.edit_widget.SelectedWidgetData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class EditorViewModel : ViewModel() {
    lateinit var observableLayout: ObservableControlLayout
        private set
    var selectedLayer by mutableStateOf<ObservableControlLayer?>(null)
    var selectedWidget by mutableStateOf<SelectedWidgetData?>(null)
    var selectedStyle by mutableStateOf<ObservableButtonStyle?>(null)
    var editorMenu by mutableStateOf(MenuState.HIDE)
    var editorBallPosition by mutableStateOf(Offset.Zero)
    var editorOperation by mutableStateOf<EditorOperation>(EditorOperation.None)
    var editorWidgetOperation by mutableStateOf<EditorWidgetOperation>(EditorWidgetOperation.None)
    var editorWarningOperation by mutableStateOf<EditorWarningOperation>(EditorWarningOperation.None)
    var isLayerFocus by mutableStateOf(false)
    var isPreviewMode by mutableStateOf(false)
    var previewScenario by mutableStateOf(PreviewScenario.InMenu)
    var previewHideLayerWhen by mutableStateOf(HideLayerWhen.None)
    var enableJoystick by mutableStateOf(false)

    fun initLayout(layout: ControlLayout) {
        if (!::observableLayout.isInitialized) {
            this.observableLayout = ObservableControlLayout(layout)
        }
    }

    fun switchMenu() {
        editorMenu = editorMenu.next()
    }

    fun removeLayer(layer: ObservableControlLayer) {
        if (layer == selectedLayer) selectedLayer = null
        observableLayout.removeLayer(layer.uuid)
    }

    fun addWidget(layers: List<ObservableControlLayer>, addToLayer: (ObservableControlLayer) -> Unit) {
        val layer = selectedLayer
        if (layers.isEmpty()) {
            editorWarningOperation = EditorWarningOperation.WarningNoLayers
        } else if (layer == null) {
            editorWarningOperation = EditorWarningOperation.WarningNoSelectLayer
        } else {
            addToLayer(layer)
        }
    }

    fun removeWidget(layer: ObservableControlLayer, widget: ObservableWidget) {
        when (widget) {
            is ObservableNormalData -> layer.removeNormalButton(widget.uuid)
            is ObservableTextData -> layer.removeTextBox(widget.uuid)
        }
    }

    fun cloneWidgetToLayers(widget: ObservableWidget, layers: List<ObservableControlLayer>) {
        when (widget) {
            is ObservableNormalData -> {
                layers.forEach { layer ->
                    val newData = widget.cloneNormal()
                    layer.addNormalButton(newData)
                }
            }
            is ObservableTextData -> {
                layers.forEach { layer ->
                    val newData = widget.cloneText()
                    layer.addTextBox(newData)
                }
            }
        }
    }

    fun createNewStyle(name: String) {
        observableLayout.addStyle(com.movtery.layer_controller.data.createNewButtonStyle(name))
    }

    fun cloneStyle(style: ObservableButtonStyle) {
        observableLayout.cloneStyle(style)
    }

    fun removeStyle(style: ObservableButtonStyle) {
        observableLayout.removeStyle(style.uuid)
    }

    fun applyEditorHide() {
        observableLayout.applyEditorHide()
    }

    fun save(targetFile: File, onSaved: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            editorOperation = EditorOperation.Saving
            val layout = observableLayout.pack()
            runCatching {
                layout.saveToFile(targetFile)
            }.onFailure { e ->
                editorOperation = EditorOperation.SaveFailed(e)
            }.onSuccess {
                editorOperation = EditorOperation.None
                onSaved()
            }
        }
    }

    fun onBackPressed(context: Context, onExit: () -> Unit) {
        if (editorOperation is EditorOperation.SelectButton || editorOperation is EditorOperation.EditButtonStyle) {
            editorOperation = EditorOperation.None
        } else {
            showExitEditorDialog(context = context, onExit = onExit)
        }
    }

    private val checkModified = Mutex()

    fun showExitEditorDialog(context: Context, onExit: () -> Unit) {
        viewModelScope.launch {
            val isModified = checkModified.withLock { observableLayout.isModified() }
            if (isModified) {
                showExitEditorDialogSuspend(context = context, onExit = onExit)
            } else {
                onExit()
            }
        }
    }

    private suspend fun showExitEditorDialogSuspend(context: Context, onExit: () -> Unit) = withContext(Dispatchers.Main) {
        MaterialAlertDialogBuilder(context)
            .setTitle(android.R.string.dialog_alert_title)
            .setMessage("You have unsaved changes. Exit anyway?")
            .setPositiveButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .setNegativeButton("Exit Without Saving") { dialog, _ -> dialog.dismiss(); onExit() }
            .show()
    }
}
