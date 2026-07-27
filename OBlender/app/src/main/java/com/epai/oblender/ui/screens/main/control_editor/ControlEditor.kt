package com.epai.oblender.ui.screens.main.control_editor

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.layer_controller.ControlEditorLayer
import com.movtery.layer_controller.data.*
import com.movtery.layer_controller.data.lang.createTranslatable
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.layer_controller.layout.createNewLayer
import com.movtery.layer_controller.observable.*
import com.epai.oblender.R
import com.epai.oblender.setting.AllSettings
import com.epai.oblender.ui.components.*
import com.epai.oblender.ui.screens.main.control_editor.edit_layer.EditControlLayerDialog
import com.epai.oblender.ui.screens.main.control_editor.edit_layer.EditSwitchLayersVisibilityDialog
import com.epai.oblender.ui.screens.main.control_editor.edit_style.StyleListDialog
import com.epai.oblender.ui.screens.main.control_editor.edit_translatable.EditTranslatableTextDialog
import com.epai.oblender.ui.screens.main.control_editor.edit_widget.*
import com.epai.oblender.utils.string.getMessageOrToString
import com.epai.oblender.viewmodel.EditorViewModel
import java.io.File

@Composable
fun BoxWithConstraintsScope.ControlEditor(
    viewModel: EditorViewModel, targetFile: File, exit: () -> Unit, menuExit: () -> Unit
) {
    val layers by viewModel.observableLayout.layers.collectAsStateWithLifecycle()
    val styles by viewModel.observableLayout.styles.collectAsStateWithLifecycle()
    val special by viewModel.observableLayout.special.collectAsStateWithLifecycle()
    val joystickStyle by special.joystickStyle.collectAsStateWithLifecycle()

    val defaultLayerName = stringResource(R.string.control_editor_edit_layer_default)
    val defaultButtonName = stringResource(R.string.control_editor_edit_button_default)
    val defaultTextName = stringResource(R.string.control_editor_edit_text_default)

    val density = LocalDensity.current
    val screenSize = rememberBoxSize()

    if (viewModel.isPreviewMode) {
        PreviewControlBox(modifier = Modifier.fillMaxSize(), observableLayout = viewModel.observableLayout,
            previewScenario = viewModel.previewScenario, previewHideLayerWhen = viewModel.previewHideLayerWhen, enableJoystick = viewModel.enableJoystick)
    } else {
        ControlEditorLayer(observedLayout = viewModel.observableLayout, selectedWidget = viewModel.selectedWidget?.data,
            onButtonTap = { data, layer ->
                val current = viewModel.selectedWidget?.data
                viewModel.selectedWidget = SelectedWidgetData(data, layer)
                if (current == data) viewModel.editorOperation = EditorOperation.SelectButton
            },
            onBackgroundClick = { viewModel.selectedWidget = null },
            floatingButtons = {
                ActionButton(painter = painterResource(R.drawable.ic_settings_filled), text = stringResource(R.string.generic_setting),
                    onClick = { if (viewModel.selectedWidget != null) viewModel.editorOperation = EditorOperation.SelectButton })
                ActionButton(painter = painterResource(R.drawable.ic_file_copy_filled), text = stringResource(R.string.control_editor_edit_dialog_clone_widget),
                    onClick = { viewModel.selectedWidget?.let { viewModel.editorWidgetOperation = EditorWidgetOperation.CloneButton(it.data, it.layer) } })
                ActionButton(painter = painterResource(R.drawable.ic_delete_filled), text = stringResource(R.string.generic_delete),
                    onClick = { viewModel.selectedWidget?.let { viewModel.editorWidgetOperation = EditorWidgetOperation.DeleteButton(it.data, it.layer) } })
            },
            enableSnap = AllSettings.editorEnableWidgetSnap.state,
            snapInAllLayers = AllSettings.editorSnapInAllLayers.state,
            snapMode = AllSettings.editorWidgetSnapMode.state,
            focusedLayer = viewModel.selectedLayer?.takeIf { viewModel.isLayerFocus },
            isDark = false)
    }

    EditorMenu(state = viewModel.editorMenu, closeScreen = { viewModel.editorMenu = MenuState.HIDE },
        layers = layers, onReorder = { from, to -> viewModel.observableLayout.reorder(from, to) },
        selectedLayer = viewModel.selectedLayer, onLayerSelected = { viewModel.selectedLayer = it },
        createLayer = { viewModel.editorOperation = EditorOperation.EditLayer(viewModel.observableLayout.addLayer(layer = createNewLayer(defaultLayerName = defaultLayerName))) },
        onAttribute = { viewModel.editorOperation = EditorOperation.EditLayer(it) },
        onHideSwitch = { layer -> layer.editorHide = !layer.editorHide; if (layer.editorHide && viewModel.selectedWidget?.layer == layer) viewModel.selectedWidget = null },
        addNewButton = { viewModel.addWidget(layers) { layer -> layer.addNormalButton(createWidgetWithUUID { uuid -> NormalData(text = createTranslatable(default = defaultButtonName), uuid = uuid, position = CenterPosition, buttonSize = createAdaptiveButtonSize(referenceLength = screenSize.height, density = density.density), visibilityType = VisibilityType.ALWAYS, isSwipple = false, isPenetrable = false, isToggleable = false) } ) } },
        addNewText = { viewModel.addWidget(layers) { layer -> layer.addTextBox(createWidgetWithUUID { uuid -> TextData(text = createTranslatable(default = defaultTextName), uuid = uuid, position = CenterPosition, buttonSize = createAdaptiveButtonSize(referenceLength = screenSize.height, density = density.density, type = ButtonSize.Type.WrapContent), visibilityType = VisibilityType.ALWAYS) } ) } },
        openStyleList = { viewModel.editorOperation = EditorOperation.OpenStyleList },
        onEditJoystickStyle = { viewModel.editorOperation = if (joystickStyle == null) EditorOperation.CreateJoystickStyle else EditorOperation.EditJoystickStyle },
        isLayerFocus = viewModel.isLayerFocus, onLayerFocusChanged = { viewModel.isLayerFocus = it },
        isPreviewMode = viewModel.isPreviewMode, onPreviewChanged = { viewModel.applyEditorHide(); viewModel.isPreviewMode = it },
        previewScenario = viewModel.previewScenario, onPreviewScenarioChanged = { viewModel.previewScenario = it },
        previewHideLayerWhen = viewModel.previewHideLayerWhen, onPreviewHideLayerChanged = { viewModel.previewHideLayerWhen = it },
        enableJoystick = viewModel.enableJoystick, onJoystickSwitch = { viewModel.enableJoystick = it },
        onJoystickTip = { viewModel.editorOperation = EditorOperation.TipJoystick },
        onSave = { viewModel.save(targetFile, onSaved = {}) }, saveAndExit = { viewModel.save(targetFile, onSaved = exit) }, onExit = menuExit)

    MenuBox(position = viewModel.editorBallPosition, onPositionChanged = { viewModel.editorBallPosition = it }, opened = viewModel.editorMenu == MenuState.SHOW) { viewModel.switchMenu() }

    EditWidgetDialog(data = viewModel.selectedWidget, visible = viewModel.editorOperation == EditorOperation.SelectButton, styles = styles,
        onDismissRequest = { viewModel.editorOperation = EditorOperation.None },
        onDelete = { data, layer -> viewModel.editorWidgetOperation = EditorWidgetOperation.DeleteButton(data, layer) },
        onClone = { data, layer -> viewModel.editorWidgetOperation = EditorWidgetOperation.CloneButton(data, layer) },
        onEditWidgetText = { viewModel.editorWidgetOperation = EditorWidgetOperation.EditWidgetText(it) },
        switchControlLayers = { data, type -> viewModel.editorWidgetOperation = EditorWidgetOperation.SwitchLayersVisibility(data, type) },
        sendText = { viewModel.editorWidgetOperation = EditorWidgetOperation.SendText(it) },
        openStyleList = { viewModel.editorOperation = EditorOperation.OpenStyleList })

    EditorOperationHandler(operation = viewModel.editorOperation, changeOperation = { viewModel.editorOperation = it },
        onDeleteLayer = { val isWidgetLayer = viewModel.selectedWidget?.layer == it; viewModel.removeLayer(it); if (isWidgetLayer) viewModel.selectedWidget = null },
        onMergeDownward = { viewModel.observableLayout.mergeDownward(it) },
        onCopy = { layer -> val baseLayer = layer.pack(); viewModel.editorOperation = EditorOperation.EditLayer(viewModel.observableLayout.addLayer(layer = createNewLayer(defaultLayerName = defaultLayerName).copy(hide = baseLayer.hide, hideWhenMouse = baseLayer.hideWhenMouse, hideWhenGamepad = baseLayer.hideWhenGamepad, hideWhenJoystick = baseLayer.hideWhenJoystick, visibilityType = baseLayer.visibilityType, normalButtons = baseLayer.normalButtons, textBoxes = baseLayer.textBoxes))) },
        onHideChange = { hide, layer -> if (hide && viewModel.selectedWidget?.layer == layer) viewModel.selectedWidget = null },
        onEditStyle = { viewModel.selectedStyle = it; viewModel.editorOperation = EditorOperation.EditButtonStyle },
        onCreateStyle = { viewModel.createNewStyle(it) }, onCloneStyle = { viewModel.cloneStyle(it) },
        onDeleteStyle = { viewModel.removeStyle(it) },
        onCreateJoystickStyle = { special.setJoystickStyle(DefaultObservableJoystickStyle); viewModel.editorOperation = EditorOperation.EditJoystickStyle },
        onDeleteJoystickStyle = { special.setJoystickStyle(null); viewModel.editorOperation = EditorOperation.None },
        styles = styles)

    EditorWidgetOperationHandler(operation = viewModel.editorWidgetOperation, changeOperation = { viewModel.editorWidgetOperation = it },
        controlLayers = layers,
        onCloneWidgets = { widget, layers -> viewModel.cloneWidgetToLayers(widget, layers) },
        onDeleteWidget = { widget, layer -> viewModel.removeWidget(layer, widget); viewModel.selectedWidget = null; viewModel.editorOperation = EditorOperation.None })

    EditorWarningOperationHandler(operation = viewModel.editorWarningOperation, changeOperation = { viewModel.editorWarningOperation = it })
}

@Composable
private fun ActionButton(painter: androidx.compose.ui.graphics.painter.Painter, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.semantics { role = Role.Button }, shape = ButtonDefaults.shape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, onClick = onClick) {
        Row(modifier = Modifier.padding(all = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(modifier = Modifier.padding(start = 6.dp).size(20.dp), painter = painter, contentDescription = text)
            Text(modifier = Modifier.padding(end = 6.dp), text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun EditorOperationHandler(
    operation: EditorOperation, changeOperation: (EditorOperation) -> Unit,
    onDeleteLayer: (ObservableControlLayer) -> Unit, onMergeDownward: (ObservableControlLayer) -> Unit,
    onCopy: (ObservableControlLayer) -> Unit, onHideChange: (Boolean, ObservableControlLayer) -> Unit,
    onEditStyle: (ObservableButtonStyle) -> Unit, onCreateStyle: (name: String) -> Unit,
    onCloneStyle: (ObservableButtonStyle) -> Unit, onDeleteStyle: (ObservableButtonStyle) -> Unit,
    onCreateJoystickStyle: () -> Unit, onDeleteJoystickStyle: () -> Unit,
    styles: List<ObservableButtonStyle>
) {
    when (operation) {
        is EditorOperation.None, is EditorOperation.SelectButton, is EditorOperation.EditButtonStyle, is EditorOperation.EditJoystickStyle -> {}
        is EditorOperation.EditLayer -> {
            val layer = operation.layer
            EditControlLayerDialog(layer = layer, onDismissRequest = { changeOperation(EditorOperation.None) },
                onDelete = { changeOperation(EditorOperation.DeleteLayer(layer)) },
                onMergeDownward = { onMergeDownward(layer) }, onCopy = { onCopy(layer) },
                onHideChange = { onHideChange(it, layer) })
        }
        is EditorOperation.DeleteLayer -> {
            val layer = operation.layer
            SimpleAlertDialog(title = stringResource(R.string.generic_delete), text = stringResource(R.string.control_editor_layers_delete, layer.name),
                onDismiss = { changeOperation(EditorOperation.None) }, onConfirm = { onDeleteLayer(layer); changeOperation(EditorOperation.None) })
        }
        is EditorOperation.OpenStyleList -> {
            StyleListDialog(styles = styles, onEditStyle = onEditStyle,
                onCreate = { changeOperation(EditorOperation.CreateStyle) },
                onClone = { onCloneStyle(it) }, onDelete = { changeOperation(EditorOperation.DeleteButtonStyle(it)) },
                onClose = { changeOperation(EditorOperation.None) })
        }
        is EditorOperation.CreateStyle -> {
            var name by remember { mutableStateOf("") }
            SimpleEditDialog(title = stringResource(R.string.control_editor_edit_style_config_name), value = name, onValueChange = { name = it },
                singleLine = true, onDismissRequest = { changeOperation(EditorOperation.None) },
                onConfirm = { onCreateStyle(name); changeOperation(EditorOperation.OpenStyleList) })
        }
        is EditorOperation.DeleteButtonStyle -> {
            SimpleAlertDialog(title = stringResource(R.string.generic_delete), text = stringResource(R.string.control_editor_edit_style_config_delete, operation.style.name),
                onDismiss = { changeOperation(EditorOperation.None) }, onConfirm = { onDeleteStyle(operation.style); changeOperation(EditorOperation.None) })
        }
        is EditorOperation.TipJoystick -> {
            SimpleAlertDialog(title = stringResource(R.string.control_editor_special_joystick_style_tip_title), text = stringResource(R.string.control_editor_special_joystick_style_tip_summary),
                onDismiss = { changeOperation(EditorOperation.None) })
        }
        is EditorOperation.CreateJoystickStyle -> {
            SimpleAlertDialog(title = stringResource(R.string.control_editor_special_joystick_style_create_title), text = stringResource(R.string.control_editor_special_joystick_style_create_summary),
                confirmText = stringResource(R.string.control_manage_create_new), onConfirm = onCreateJoystickStyle,
                onDismiss = { changeOperation(EditorOperation.None) })
        }
        is EditorOperation.DeleteJoystickStyle -> {
            SimpleAlertDialog(title = stringResource(R.string.control_editor_special_joystick_style_delete_title), text = stringResource(R.string.control_editor_special_joystick_style_delete_summary),
                confirmText = stringResource(R.string.generic_delete), onConfirm = onDeleteJoystickStyle,
                onDismiss = { changeOperation(EditorOperation.None) })
        }
        is EditorOperation.Saving -> { ProgressDialog(title = stringResource(R.string.control_manage_saving)) }
        is EditorOperation.SaveFailed -> {
            SimpleAlertDialog(title = stringResource(R.string.control_manage_failed_to_save), text = operation.error.getMessageOrToString()) { changeOperation(EditorOperation.None) }
        }
    }
}

@Composable
private fun EditorWidgetOperationHandler(
    operation: EditorWidgetOperation, changeOperation: (EditorWidgetOperation) -> Unit,
    controlLayers: List<ObservableControlLayer>,
    onCloneWidgets: (ObservableWidget, List<ObservableControlLayer>) -> Unit,
    onDeleteWidget: (ObservableWidget, ObservableControlLayer) -> Unit,
) {
    when (operation) {
        is EditorWidgetOperation.None -> {}
        is EditorWidgetOperation.CloneButton -> {
            SelectLayers(layers = controlLayers, initLayer = operation.layer, onDismissRequest = { changeOperation(EditorWidgetOperation.None) },
                title = stringResource(R.string.control_editor_edit_dialog_clone_widget_title),
                confirmText = stringResource(R.string.control_editor_edit_dialog_clone_widget),
                onConfirm = { onCloneWidgets(operation.data, it); changeOperation(EditorWidgetOperation.None) })
        }
        is EditorWidgetOperation.DeleteButton -> {
            SimpleAlertDialog(title = stringResource(R.string.generic_delete), text = stringResource(R.string.control_editor_edit_dialog_delete_widget),
                onDismiss = { changeOperation(EditorWidgetOperation.None) }, onConfirm = { onDeleteWidget(operation.data, operation.layer); changeOperation(EditorWidgetOperation.None) })
        }
        is EditorWidgetOperation.EditWidgetText -> {
            EditTranslatableTextDialog(text = operation.string, singleLine = false, onClose = { changeOperation(EditorWidgetOperation.None) })
        }
        is EditorWidgetOperation.SendText -> {
            var value by remember { mutableStateOf(operation.data.clickEvents.find { it.type == ClickEvent.Type.SendText }?.key ?: "") }
            SimpleEditDialog(title = stringResource(R.string.control_editor_edit_event_launcher_send_text), value = value, onValueChange = { value = it },
                extraBody = { Text(text = stringResource(R.string.control_editor_edit_event_launcher_send_text_summary), style = MaterialTheme.typography.labelSmall) },
                label = { Text(text = stringResource(R.string.control_editor_edit_event_launcher_send_text_hint)) }, singleLine = true,
                onConfirm = { operation.data.removeAllEvent(ClickEvent.Type.SendText); if (value.isNotEmpty()) operation.data.addEvent(ClickEvent(ClickEvent.Type.SendText, value)); changeOperation(EditorWidgetOperation.None) })
        }
        is EditorWidgetOperation.SwitchLayersVisibility -> {
            EditSwitchLayersVisibilityDialog(data = operation.data, layers = controlLayers, type = operation.type,
                onDismissRequest = { changeOperation(EditorWidgetOperation.None) })
        }
    }
}

@Composable
private fun EditorWarningOperationHandler(operation: EditorWarningOperation, changeOperation: (EditorWarningOperation) -> Unit) {
    when (operation) {
        is EditorWarningOperation.None -> {}
        is EditorWarningOperation.WarningNoLayers -> {
            SimpleAlertDialog(title = stringResource(R.string.control_editor_menu_no_layers_title), text = stringResource(R.string.control_editor_menu_no_layers_message)) { changeOperation(EditorWarningOperation.None) }
        }
        is EditorWarningOperation.WarningNoSelectLayer -> {
            SimpleAlertDialog(title = stringResource(R.string.control_editor_menu_no_selected_layer_title), text = stringResource(R.string.control_editor_menu_no_selected_layer_message)) { changeOperation(EditorWarningOperation.None) }
        }
    }
}
