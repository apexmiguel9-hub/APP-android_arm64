package com.epai.oblender.ui.screens.main.control_editor.edit_style

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
import com.movtery.layer_controller.observable.ObservableButtonStyle
import com.epai.oblender.R
import com.epai.oblender.ui.components.MarqueeText

@Composable
fun StyleListDialog(styles: List<ObservableButtonStyle>, onEditStyle: (ObservableButtonStyle) -> Unit, onCreate: () -> Unit, onClone: (ObservableButtonStyle) -> Unit, onDelete: (ObservableButtonStyle) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 6.dp) {
            Column(modifier = Modifier.padding(16.dp).widthIn(max = 400.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.control_editor_edit_style_config), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(styles) { style ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(modifier = Modifier.weight(1f), text = style.name, style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = { onEditStyle(style) }) { Text("Edit") }
                            TextButton(onClick = { onClone(style) }) { Text("Clone") }
                            TextButton(onClick = { onDelete(style) }) { Text("Del") }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilledTonalButton(onClick = onCreate) { MarqueeText(text = stringResource(R.string.control_manage_create_new)) }
                    Button(onClick = onClose) { MarqueeText(text = stringResource(R.string.generic_close)) }
                }
            }
        }
    }
}
