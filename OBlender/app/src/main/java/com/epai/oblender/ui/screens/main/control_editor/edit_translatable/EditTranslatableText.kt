package com.epai.oblender.ui.screens.main.control_editor.edit_translatable

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.movtery.layer_controller.observable.ObservableTranslatableString
import com.epai.oblender.ui.components.MarqueeText

@Composable
fun EditTranslatableTextDialog(text: ObservableTranslatableString, singleLine: Boolean = false, onClose: () -> Unit) {
    var value by remember { mutableStateOf(text.default) }

    Dialog(onDismissRequest = onClose) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 6.dp) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = "Edit Text", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = singleLine, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilledTonalButton(onClick = onClose) { MarqueeText(text = "Cancel") }
                    Button(onClick = { text.default = value; onClose() }) { MarqueeText(text = "Confirm") }
                }
            }
        }
    }
}
