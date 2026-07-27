package com.epai.oblender.ui.screens.main.control_editor

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.movtery.layer_controller.data.HideLayerWhen
import com.movtery.layer_controller.data.VisibilityType
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.layer_controller.observable.ObservableButtonStyle
import com.movtery.layer_controller.observable.ObservableControlLayer
import com.movtery.layer_controller.observable.ObservableNormalData
import com.movtery.layer_controller.observable.ObservableTranslatableString
import com.movtery.layer_controller.observable.ObservableWidget
import com.movtery.layer_controller.utils.snap.SnapMode
import com.epai.oblender.R
import com.epai.oblender.bridge.CURSOR_DISABLED
import com.epai.oblender.bridge.CURSOR_ENABLED
import com.epai.oblender.setting.AllSettings
import com.epai.oblender.ui.components.*

sealed interface EditorOperation {
    data object None : EditorOperation
    data object SelectButton : EditorOperation
    data class EditLayer(val layer: ObservableControlLayer) : EditorOperation
    data class DeleteLayer(val layer: ObservableControlLayer) : EditorOperation
    data object OpenStyleList : EditorOperation
    data object CreateStyle : EditorOperation
    data object EditButtonStyle : EditorOperation
    data class DeleteButtonStyle(val style: ObservableButtonStyle) : EditorOperation
    data object CreateJoystickStyle : EditorOperation
    data object TipJoystick : EditorOperation
    data object EditJoystickStyle : EditorOperation
    data object DeleteJoystickStyle : EditorOperation
    data object Saving : EditorOperation
    data class SaveFailed(val error: Throwable) : EditorOperation
}

sealed interface EditorWidgetOperation {
    data object None : EditorWidgetOperation
    data class CloneButton(val data: ObservableWidget, val layer: ObservableControlLayer) : EditorWidgetOperation
    data class DeleteButton(val data: ObservableWidget, val layer: ObservableControlLayer) : EditorWidgetOperation
    data class EditWidgetText(val string: ObservableTranslatableString) : EditorWidgetOperation
    data class SwitchLayersVisibility(val data: ObservableNormalData, val type: ClickEvent.Type) : EditorWidgetOperation
    data class SendText(val data: ObservableNormalData) : EditorWidgetOperation
}

sealed interface EditorWarningOperation {
    data object None : EditorWarningOperation
    data object WarningNoLayers : EditorWarningOperation
    data object WarningNoSelectLayer : EditorWarningOperation
}

enum class PreviewScenario(val textRes: Int, val cursorMode: Int, val isCursorGrabbing: Boolean = cursorMode == CURSOR_DISABLED) {
    InGame(R.string.control_editor_menu_preview_mode_in_game, cursorMode = CURSOR_DISABLED),
    InMenu(R.string.control_editor_menu_preview_mode_in_menu, cursorMode = CURSOR_ENABLED)
}

@Composable
fun VisibilityType.getVisibilityText(): String {
    return stringResource(when (this) {
        VisibilityType.ALWAYS -> R.string.control_editor_edit_visibility_always
        VisibilityType.IN_GAME -> R.string.control_editor_edit_visibility_in_game
        VisibilityType.IN_MENU -> R.string.control_editor_edit_visibility_in_menu
    })
}

@Composable
fun MenuBox(position: Offset, onPositionChanged: (Offset) -> Unit, opened: Boolean, onClick: () -> Unit) {
    FloatingBall(position = position, onPositionChanged = onPositionChanged, onClick = onClick) {
        Box(modifier = Modifier.padding(all = 2.dp).size(28.dp), contentAlignment = Alignment.Center) {
            Crossfade(opened) { state ->
                Icon(modifier = Modifier.size(24.dp), painter = painterResource(if (state) R.drawable.ic_menu_open else R.drawable.ic_menu), contentDescription = null)
            }
        }
    }
}

@Composable
fun EditorMenu(
    state: MenuState, closeScreen: () -> Unit,
    layers: List<ObservableControlLayer>, onReorder: (from: Int, to: Int) -> Unit,
    selectedLayer: ObservableControlLayer?, onLayerSelected: (ObservableControlLayer?) -> Unit,
    createLayer: () -> Unit, onAttribute: (ObservableControlLayer) -> Unit,
    onHideSwitch: (ObservableControlLayer) -> Unit,
    addNewButton: () -> Unit, addNewText: () -> Unit,
    openStyleList: () -> Unit, onEditJoystickStyle: () -> Unit,
    isLayerFocus: Boolean, onLayerFocusChanged: (Boolean) -> Unit,
    isPreviewMode: Boolean, onPreviewChanged: (Boolean) -> Unit,
    previewScenario: PreviewScenario, onPreviewScenarioChanged: (PreviewScenario) -> Unit,
    previewHideLayerWhen: HideLayerWhen, onPreviewHideLayerChanged: (HideLayerWhen) -> Unit,
    enableJoystick: Boolean, onJoystickSwitch: (Boolean) -> Unit,
    onJoystickTip: () -> Unit, onSave: () -> Unit, saveAndExit: () -> Unit, onExit: () -> Unit
) {
    DualMenuSubscreen(
        state = state, closeScreen = closeScreen,
        leftMenuTitle = { Text(modifier = Modifier.padding(all = 8.dp), text = stringResource(R.string.control_editor_menu_title), style = MaterialTheme.typography.titleMedium) },
        leftMenuContent = {
            EditorMenuContent(modifier = Modifier.weight(1f), closeScreen = closeScreen,
                addNewButton = addNewButton, addNewText = addNewText,
                openStyleList = openStyleList, onEditJoystickStyle = onEditJoystickStyle,
                isPreviewMode = isPreviewMode, onPreviewChanged = onPreviewChanged,
                previewScenario = previewScenario, onPreviewScenarioChanged = onPreviewScenarioChanged,
                previewHideLayerWhen = previewHideLayerWhen, onPreviewHideLayerChanged = onPreviewHideLayerChanged,
                enableJoystick = enableJoystick, onJoystickSwitch = onJoystickSwitch, onJoystickTip = onJoystickTip,
                onSave = onSave, saveAndExit = saveAndExit, onExit = onExit)
        },
        rightMenuTitle = {
            Text(modifier = Modifier.padding(all = 8.dp), text = stringResource(R.string.control_editor_layers_title), style = MaterialTheme.typography.titleMedium)
            IconButton(modifier = Modifier.align(Alignment.CenterEnd), onClick = { onLayerFocusChanged(!isLayerFocus) }, enabled = !isPreviewMode && selectedLayer != null) {
                Crossfade(isLayerFocus) { focus -> Icon(painter = painterResource(if (focus) R.drawable.ic_center_focus_strong_filled else R.drawable.ic_center_focus_strong_outlined), contentDescription = null) }
            }
        },
        rightMenuContent = {
            ControlLayerMenu(layers = layers, selectedLayer = selectedLayer, onLayerSelected = onLayerSelected, createLayer = createLayer, onAttribute = onAttribute, onHideSwitch = onHideSwitch, enabled = !isPreviewMode)
        }
    )
}

@Composable
private fun EditorMenuContent(
    closeScreen: () -> Unit, addNewButton: () -> Unit, addNewText: () -> Unit,
    openStyleList: () -> Unit, onEditJoystickStyle: () -> Unit,
    isPreviewMode: Boolean, onPreviewChanged: (Boolean) -> Unit,
    previewScenario: PreviewScenario, onPreviewScenarioChanged: (PreviewScenario) -> Unit,
    previewHideLayerWhen: HideLayerWhen, onPreviewHideLayerChanged: (HideLayerWhen) -> Unit,
    enableJoystick: Boolean, onJoystickSwitch: (Boolean) -> Unit, onJoystickTip: () -> Unit,
    onSave: () -> Unit, saveAndExit: () -> Unit, onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme.surfaceVariant
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(all = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { MenuTextButton(modifier = Modifier.fillMaxWidth(), enabled = !isPreviewMode, text = stringResource(R.string.control_editor_menu_new_widget_button), onClick = addNewButton, color = color, contentColor = contentColor) }
        item { MenuTextButton(modifier = Modifier.fillMaxWidth(), enabled = !isPreviewMode, text = stringResource(R.string.control_editor_menu_new_widget_text), onClick = addNewText, color = color, contentColor = contentColor) }
        item {
            MenuTextButton(modifier = Modifier.fillMaxWidth(), text = stringResource(R.string.control_editor_edit_style_config), enabled = !isPreviewMode,
                onClick = { openStyleList(); closeScreen() }, color = color, contentColor = contentColor)
        }
        item {
            MenuTextButton(modifier = Modifier.fillMaxWidth(), text = stringResource(R.string.control_editor_special_joystick_style), enabled = !isPreviewMode,
                appendLayout = {
                    IconButton(onClick = { onJoystickTip(); closeScreen() }, enabled = !isPreviewMode) {
                        Icon(painter = painterResource(R.drawable.ic_help_outlined), contentDescription = stringResource(R.string.generic_tip))
                    }
                },
                onClick = { onEditJoystickStyle(); closeScreen() }, color = color, contentColor = contentColor)
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item { MenuSwitchButton(modifier = Modifier.fillMaxWidth(), text = stringResource(R.string.control_editor_menu_preview_mode), switch = isPreviewMode, onSwitch = onPreviewChanged, color = color, contentColor = contentColor) }
        item {
            MenuListLayout(modifier = Modifier.fillMaxWidth(), title = stringResource(R.string.control_editor_menu_preview_mode_scenario),
                items = PreviewScenario.entries, currentItem = previewScenario, onItemChange = onPreviewScenarioChanged,
                getItemText = { stringResource(it.textRes) }, color = color, contentColor = contentColor, enabled = isPreviewMode)
        }
        item {
            MenuSwitchButton(modifier = Modifier.fillMaxWidth(), text = stringResource(R.string.control_editor_menu_preview_is_mouse),
                switch = previewHideLayerWhen == HideLayerWhen.WhenMouse,
                onSwitch = { onPreviewHideLayerChanged(if (it) HideLayerWhen.WhenMouse else HideLayerWhen.None) },
                color = color, contentColor = contentColor, enabled = isPreviewMode)
        }
        item {
            MenuSwitchButton(modifier = Modifier.fillMaxWidth(), text = stringResource(R.string.control_editor_menu_preview_is_gamepad),
                switch = previewHideLayerWhen == HideLayerWhen.WhenGamepad,
                onSwitch = { onPreviewHideLayerChanged(if (it) HideLayerWhen.WhenGamepad else HideLayerWhen.None) },
                color = color, contentColor = contentColor, enabled = isPreviewMode)
        }
        item {
            MenuSwitchButton(modifier = Modifier.fillMaxWidth(), text = stringResource(R.string.game_styles_joystick_enable),
                switch = enableJoystick, onSwitch = onJoystickSwitch, color = color, contentColor = contentColor, enabled = isPreviewMode)
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item {
            MenuSwitchButton(modifier = Modifier.fillMaxWidth(), text = stringResource(R.string.control_editor_menu_widget_snap),
                switch = AllSettings.editorEnableWidgetSnap.state, onSwitch = { AllSettings.editorEnableWidgetSnap.save(it) },
                color = color, contentColor = contentColor)
        }
        item {
            MenuSwitchButton(modifier = Modifier.fillMaxWidth(), text = stringResource(R.string.control_editor_menu_widget_snap_all_layers),
                switch = AllSettings.editorSnapInAllLayers.state, onSwitch = { AllSettings.editorSnapInAllLayers.save(it) },
                color = color, contentColor = contentColor)
        }
        item {
            MenuListLayout(modifier = Modifier.fillMaxWidth(), title = stringResource(R.string.control_editor_menu_widget_snap_mode),
                items = SnapMode.entries, currentItem = AllSettings.editorWidgetSnapMode.state,
                onItemChange = { AllSettings.editorWidgetSnapMode.save(it) },
                getItemText = { stringResource(when (it) { SnapMode.FullScreen -> R.string.control_editor_menu_widget_snap_mode_fullscreen; SnapMode.Local -> R.string.control_editor_menu_widget_snap_mode_local }) },
                color = color, contentColor = contentColor)
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item { MenuTextButton(modifier = Modifier.fillMaxWidth(), text = stringResource(R.string.generic_save), onClick = onSave, color = color, contentColor = contentColor) }
        item { MenuTextButton(modifier = Modifier.fillMaxWidth(), text = stringResource(R.string.control_editor_menu_save_and_exit), onClick = saveAndExit, color = color, contentColor = contentColor) }
        item { MenuTextButton(modifier = Modifier.fillMaxWidth(), text = stringResource(R.string.control_editor_exit_confirm), onClick = onExit, color = color, contentColor = contentColor) }
    }
}

@Composable
private fun ColumnScope.ControlLayerMenu(
    layers: List<ObservableControlLayer>,
    selectedLayer: ObservableControlLayer?, onLayerSelected: (ObservableControlLayer?) -> Unit,
    createLayer: () -> Unit, onAttribute: (ObservableControlLayer) -> Unit,
    onHideSwitch: (ObservableControlLayer) -> Unit,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true
) {
    val listState = rememberLazyListState()
    var previousSize by remember { mutableIntStateOf(0) }
    val currentSize = layers.size
    LaunchedEffect(currentSize) {
        if (currentSize != previousSize) {
            if (currentSize - previousSize > 0) runCatching { listState.animateScrollToItem(0) }
            previousSize = currentSize
        }
    }
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(all = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(layers, { it.uuid }) { layer ->
            ControlLayerItem(modifier = Modifier.fillMaxWidth(), layer = layer, selected = selectedLayer == layer,
                onSelected = { onLayerSelected(layer) }, onUnSelected = { onLayerSelected(null) },
                onAttribute = { onAttribute(layer) }, onHideSwitch = { onHideSwitch(layer) },
                color = color, contentColor = contentColor, enabled = enabled)
        }
    }
    ScalingActionButton(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 4.dp), onClick = createLayer) {
        MarqueeText(text = stringResource(R.string.control_editor_layers_create))
    }
}

@Composable
private fun ControlLayerItem(
    modifier: Modifier = Modifier, layer: ObservableControlLayer,
    selected: Boolean, onSelected: () -> Unit, onUnSelected: () -> Unit,
    onAttribute: () -> Unit, onHideSwitch: () -> Unit,
    color: Color, contentColor: Color,
    borderColor: Color = MaterialTheme.colorScheme.primary, shape: Shape = MaterialTheme.shapes.large,
    enabled: Boolean = true
) {
    val borderWidth by animateDpAsState(if (selected && enabled) 4.dp else 0.dp)
    Surface(modifier = modifier.border(width = borderWidth, color = borderColor, shape = shape),
        color = color, contentColor = contentColor, shape = shape, onClick = { if (selected) onUnSelected() else onSelected() }, enabled = enabled) {
        Row(modifier = Modifier.fillMaxWidth().clip(shape).padding(all = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onHideSwitch, enabled = enabled) {
                Crossfade(layer.editorHide) { hide -> Icon(painter = painterResource(if (hide) R.drawable.ic_visibility_off_outlined else R.drawable.ic_visibility_outlined), contentDescription = null) }
            }
            MarqueeText(modifier = Modifier.weight(1f), text = layer.name, style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = onAttribute, enabled = enabled) {
                Icon(painter = painterResource(R.drawable.ic_more_horiz), contentDescription = stringResource(R.string.control_editor_layers_attribute))
            }
        }
    }
}