package com.epai.oblender.ui.screens.main.control_editor.edit_widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.movtery.layer_controller.observable.ObservableControlLayer
import com.epai.oblender.R
import com.epai.oblender.ui.components.MarqueeText

@Composable
fun SelectLayers(
    layers: List<ObservableControlLayer>,
    initLayer: ObservableControlLayer,
    onDismissRequest: () -> Unit,
    title: String,
    onConfirm: (selected: List<ObservableControlLayer>) -> Unit,
    confirmText: String = stringResource(R.string.generic_confirm)
) {
    val selectedLayers = remember { mutableStateListOf(initLayer) }
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 6.dp) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(layers) { layer ->
                        Row(modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable {
                            if (selectedLayers.contains(layer)) selectedLayers.remove(layer) else selectedLayers.add(layer)
                        }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selectedLayers.contains(layer), onCheckedChange = { checked -> if (checked) selectedLayers.add(layer) else selectedLayers.remove(layer) })
                            Text(text = layer.name, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilledTonalButton(modifier = Modifier.weight(1f), onClick = onDismissRequest) { MarqueeText(text = stringResource(R.string.generic_cancel)) }
                    Button(modifier = Modifier.weight(1f), onClick = { if (selectedLayers.isNotEmpty()) onConfirm(selectedLayers) }) { MarqueeText(text = confirmText) }
                }
            }
        }
    }
}
