package com.epai.oblender.ui.screens.main.control_editor.edit_layer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.movtery.layer_controller.observable.ObservableControlLayer
import com.epai.oblender.R
import com.epai.oblender.ui.screens.main.control_editor.InfoLayoutTextItem
import com.epai.oblender.ui.screens.main.control_editor.InfoLayoutSwitchItem

@Composable
fun EditControlLayerDialog(layer: ObservableControlLayer, onDismissRequest: () -> Unit, onDelete: () -> Unit, onMergeDownward: () -> Unit, onCopy: () -> Unit, onHideChange: (Boolean) -> Unit) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 6.dp) {
            Column(modifier = Modifier.padding(16.dp).widthIn(max = 350.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = layer.name, style = MaterialTheme.typography.titleMedium)
                InfoLayoutSwitchItem(modifier = Modifier.fillMaxWidth(), title = "Hide", value = layer.editorHide, onValueChange = onHideChange)
                InfoLayoutTextItem(modifier = Modifier.fillMaxWidth(), title = stringResource(R.string.generic_delete), onClick = onDelete)
                InfoLayoutTextItem(modifier = Modifier.fillMaxWidth(), title = "Merge Downward", onClick = onMergeDownward)
                InfoLayoutTextItem(modifier = Modifier.fillMaxWidth(), title = "Copy", onClick = onCopy)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilledTonalButton(onClick = onDismissRequest) { Text(stringResource(R.string.generic_close)) }
                }
            }
        }
    }
}
