package com.epai.oblender.ui.screens.main.control_editor

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.movtery.layer_controller.data.VisibilityType
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.layer_controller.observable.ObservableButtonStyle
import com.movtery.layer_controller.observable.ObservableControlLayer
import com.movtery.layer_controller.observable.ObservableNormalData
import com.movtery.layer_controller.observable.ObservableTranslatableString
import com.movtery.layer_controller.observable.ObservableWidget
import com.epai.oblender.CURSOR_MODE_PRECISION
import com.epai.oblender.CURSOR_MODE_TOUCH
import com.epai.oblender.CURSOR_MODE_VIRTUAL
import com.epai.oblender.CursorModeManager
import com.epai.oblender.R
import com.epai.oblender.VirtualPointerSettings
import com.epai.oblender.ui.components.DualMenuSubscreen
import com.epai.oblender.ui.components.FloatingBall
import com.epai.oblender.ui.components.MarqueeText
import com.epai.oblender.ui.components.MenuListLayout
import com.epai.oblender.ui.components.MenuState
import kotlin.math.roundToInt
import com.epai.oblender.ui.components.MenuTextButton
import com.epai.oblender.ui.components.ScalingActionButton

sealed interface EditorOperation {
    data object None : EditorOperation
    data object SelectButton : EditorOperation
    data class EditLayer(val layer: ObservableControlLayer) : EditorOperation
    data class DeleteLayer(val layer: ObservableControlLayer) : EditorOperation
    data object OpenStyleList : EditorOperation
    data object CreateStyle : EditorOperation
    data object EditButtonStyle : EditorOperation
    data class DeleteButtonStyle(val style: ObservableButtonStyle) : EditorOperation
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

enum class PreviewScenario(val textRes: Int, val isCursorGrabbing: Boolean = false) {
    InGame(R.string.control_editor_menu_preview_mode_in_game, isCursorGrabbing = true),
    InMenu(R.string.control_editor_menu_preview_mode_in_menu, isCursorGrabbing = false)
}

@Composable
fun VisibilityType.getVisibilityText(): String {
    val textRes = when (this) {
        VisibilityType.ALWAYS -> R.string.control_editor_edit_visibility_always
        VisibilityType.IN_GAME -> R.string.control_editor_edit_visibility_in_game
        VisibilityType.IN_MENU -> R.string.control_editor_edit_visibility_in_menu
    }
    return stringResource(textRes)
}

@Composable
fun MenuBox(
    position: Offset,
    onPositionChanged: (Offset) -> Unit,
    opened: Boolean,
    onClick: () -> Unit
) {
    FloatingBall(
        position = position,
        onPositionChanged = onPositionChanged,
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .padding(all = 2.dp)
                .size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(opened) { state ->
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(
                        if (state) R.drawable.ic_menu_open else R.drawable.ic_menu
                    ),
                    contentDescription = null
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorMenu(
    state: MenuState,
    closeScreen: () -> Unit,
    layers: List<ObservableControlLayer>,
    selectedLayer: ObservableControlLayer?,
    onLayerSelected: (ObservableControlLayer?) -> Unit,
    createLayer: () -> Unit,
    onAttribute: (ObservableControlLayer) -> Unit,
    onHideSwitch: (ObservableControlLayer) -> Unit,
    addNewButton: () -> Unit,
    saveAndExit: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        CursorModeManager.init(context)
        VirtualPointerSettings.init(context)
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    DualMenuSubscreen(
        state = state, closeScreen = closeScreen,
        leftMenuTitle = { Text(modifier = Modifier.padding(all = 8.dp), text = stringResource(R.string.control_editor_menu_title), style = MaterialTheme.typography.titleMedium) },
        leftMenuContent = {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryTabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.editor_tab_editor)) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.editor_tab_mouse)) })
                }
                when (selectedTab) {
                    0 -> {
                        MenuTextButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.control_editor_menu_new_widget_button),
                            onClick = { addNewButton(); closeScreen() }
                        )
                        MenuTextButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.control_editor_menu_save_and_exit),
                            onClick = { saveAndExit() }
                        )
                    }
                    1 -> {
                        val currentMode = CursorModeManager.getMode()
                        val showVirtualSettings = currentMode == CURSOR_MODE_VIRTUAL || currentMode == CURSOR_MODE_PRECISION

                        MenuListLayout(
                            modifier = Modifier.fillMaxWidth(),
                            title = stringResource(R.string.cursor_mode),
                            items = listOf(CURSOR_MODE_TOUCH, CURSOR_MODE_VIRTUAL, CURSOR_MODE_PRECISION),
                            currentItem = currentMode,
                            onItemChange = { mode ->
                                android.util.Log.d("OBL", "EditorMenu: cursorMode changed to $mode")
                                CursorModeManager.setMode(mode)
                            },
                            getItemText = { mode ->
                                when (mode) {
                                    CURSOR_MODE_TOUCH -> stringResource(R.string.cursor_mode_touch)
                                    CURSOR_MODE_VIRTUAL -> stringResource(R.string.cursor_mode_virtual)
                                    CURSOR_MODE_PRECISION -> stringResource(R.string.cursor_mode_precision)
                                    else -> "?"
                                }
                            }
                        )
                        if (showVirtualSettings) {
                            Spacer(modifier = Modifier.height(8.dp))
                            VirtualPointerSettingsPanel()
                        }
                    }
                }
            }
        },
        rightMenuTitle = {
            Text(modifier = Modifier.padding(all = 8.dp), text = stringResource(R.string.control_editor_layers_title), style = MaterialTheme.typography.titleMedium)
        },
        rightMenuContent = {
            ControlLayerMenu(
                layers = layers,
                selectedLayer = selectedLayer,
                onLayerSelected = onLayerSelected,
                createLayer = createLayer,
                onAttribute = onAttribute,
                onHideSwitch = onHideSwitch
            )
        }
    )
}

@Composable
private fun ColumnScope.ControlLayerMenu(
    layers: List<ObservableControlLayer>,
    selectedLayer: ObservableControlLayer?,
    onLayerSelected: (ObservableControlLayer?) -> Unit,
    createLayer: () -> Unit,
    onAttribute: (ObservableControlLayer) -> Unit,
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
    LaunchedEffect(Unit) {
        runCatching {
            val index = layers.indexOfFirst { it == selectedLayer }
            if (index >= 0 && index < layers.size) listState.animateScrollToItem(index)
        }
    }
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(all = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(layers, { it.uuid }) { layer ->
            ControlLayerItem(modifier = Modifier.fillMaxWidth(), layer = layer,
                selected = selectedLayer == layer,
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
private fun ColumnScope.VirtualPointerSettingsPanel() {
    Text(
        text = stringResource(R.string.virtual_pointer_settings),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )

    var stillnessTime by remember { mutableIntStateOf(VirtualPointerSettings.getStillnessTimeMs()) }
    var stillnessThresh by remember { mutableIntStateOf(VirtualPointerSettings.getStillnessThresholdPx()) }
    var tapMaxDist by remember { mutableIntStateOf(VirtualPointerSettings.getTapMaxDistPx()) }
    var tapMaxTime by remember { mutableIntStateOf(VirtualPointerSettings.getTapMaxTimeMs()) }
    var sens by remember { mutableFloatStateOf(VirtualPointerSettings.getSensitivity()) }

    SettingSlider(
        label = stringResource(R.string.setting_stillness_time),
        value = stillnessTime.toFloat(),
        valueRange = 200f..1500f,
        step = 50f,
        valueDisplay = "${stillnessTime}ms",
        onValueChange = { stillnessTime = it.roundToInt(); VirtualPointerSettings.setStillnessTimeMs(it.roundToInt()) }
    )
    SettingSlider(
        label = stringResource(R.string.setting_stillness_threshold),
        value = stillnessThresh.toFloat(),
        valueRange = 1f..20f,
        step = 1f,
        valueDisplay = "${stillnessThresh}px",
        onValueChange = { stillnessThresh = it.roundToInt(); VirtualPointerSettings.setStillnessThresholdPx(it.roundToInt()) }
    )
    SettingSlider(
        label = stringResource(R.string.setting_tap_max_dist),
        value = tapMaxDist.toFloat(),
        valueRange = 4f..40f,
        step = 2f,
        valueDisplay = "${tapMaxDist}px",
        onValueChange = { tapMaxDist = it.roundToInt(); VirtualPointerSettings.setTapMaxDistPx(it.roundToInt()) }
    )
    SettingSlider(
        label = stringResource(R.string.setting_tap_max_time),
        value = tapMaxTime.toFloat(),
        valueRange = 100f..600f,
        step = 50f,
        valueDisplay = "${tapMaxTime}ms",
        onValueChange = { tapMaxTime = it.roundToInt(); VirtualPointerSettings.setTapMaxTimeMs(it.roundToInt()) }
    )
    SettingSlider(
        label = stringResource(R.string.setting_cursor_sensitivity),
        value = sens,
        valueRange = 0.25f..3.0f,
        step = 0.25f,
        valueDisplay = "%.2f".format(sens),
        onValueChange = { sens = it; VirtualPointerSettings.setSensitivity(it) }
    )
}

@Composable
private fun ColumnScope.SettingSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    valueDisplay: String,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = valueDisplay,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(56.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = if (step > 0f && valueRange.endInclusive - valueRange.start > step * 2)
                ((valueRange.endInclusive - valueRange.start) / step).toInt() - 1 else 0,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(80.dp)
        )
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
