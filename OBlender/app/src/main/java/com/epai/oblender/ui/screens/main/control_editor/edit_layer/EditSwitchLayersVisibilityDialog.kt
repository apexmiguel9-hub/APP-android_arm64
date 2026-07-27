package com.epai.oblender.ui.screens.main.control_editor.edit_layer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.layer_controller.observable.ObservableControlLayer
import com.movtery.layer_controller.observable.ObservableNormalData
import com.epai.oblender.R
import com.epai.oblender.ui.components.MarqueeText

@Composable
fun EditSwitchLayersVisibilityDialog(data: ObservableNormalData, layers: List<ObservableControlLayer>, type: ClickEvent.Type, onDismissRequest: () -> Unit) {
    val selectedLayers = remember { mutableStateListOf<ObservableControlLayer>() }
    val existing = remember { data.clickEvents.filter { it.type == type } }
    LaunchedEffect(existing) {
        selectedLayers.clear()
        existing.forEach { event -> layers.find { it.uuid == event.key }?.let { selectedLayers.add(it) } }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 6.dp) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Select Layers", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(layers) { layer ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selectedLayers.contains(layer), onCheckedChange = { checked -> if (checked) selectedLayers.add(layer) else selectedLayers.remove(layer) })
                            Text(text = layer.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    data.removeAllEvent(type)
                    selectedLayers.forEach { data.addEvent(ClickEvent(type, it.uuid)) }
                    onDismissRequest()
                }) { MarqueeText(text = stringResource(R.string.generic_confirm)) }
            }
        }
    }
}
