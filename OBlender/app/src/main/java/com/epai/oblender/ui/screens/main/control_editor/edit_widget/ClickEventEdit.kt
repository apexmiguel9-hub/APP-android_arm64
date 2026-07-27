package com.epai.oblender.ui.screens.main.control_editor.edit_widget

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.layer_controller.observable.ObservableNormalData
import com.epai.oblender.R
import com.epai.oblender.ui.screens.main.control_editor.InfoLayoutSwitchItem
import com.epai.oblender.ui.screens.main.control_editor.InfoLayoutTextItem

@Composable
fun EditWidgetClickEvent(
    screenKey: com.epai.oblender.ui.screens.TitledNavKey,
    currentKey: com.epai.oblender.ui.screens.TitledNavKey?,
    data: ObservableNormalData,
    switchControlLayers: (ObservableNormalData, ClickEvent.Type) -> Unit,
    sendText: (ObservableNormalData) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Events", style = MaterialTheme.typography.titleMedium)
        InfoLayoutSwitchItem(modifier = Modifier.fillMaxWidth(), title = stringResource(R.string.control_editor_edit_event_swipple), value = data.isSwipple, onValueChange = { data.isSwipple = it })
        InfoLayoutSwitchItem(modifier = Modifier.fillMaxWidth(), title = stringResource(R.string.control_editor_edit_event_penetrable), value = data.isPenetrable, onValueChange = { data.isPenetrable = it })
        InfoLayoutSwitchItem(modifier = Modifier.fillMaxWidth(), title = stringResource(R.string.control_editor_edit_event_toggleable), value = data.isToggleable, onValueChange = { data.isToggleable = it })
        InfoLayoutTextItem(modifier = Modifier.fillMaxWidth(), title = stringResource(R.string.control_editor_edit_switch_layers), onClick = { switchControlLayers(data, ClickEvent.Type.SwitchLayer) })
        InfoLayoutTextItem(modifier = Modifier.fillMaxWidth(), title = stringResource(R.string.control_editor_edit_show_layers), onClick = { switchControlLayers(data, ClickEvent.Type.ShowLayer) })
        InfoLayoutTextItem(modifier = Modifier.fillMaxWidth(), title = stringResource(R.string.control_editor_edit_hide_layers), onClick = { switchControlLayers(data, ClickEvent.Type.HideLayer) })
    }
}
