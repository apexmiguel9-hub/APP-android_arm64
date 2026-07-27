package com.epai.oblender.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun SimpleAlertDialog(
    title: String,
    text: String,
    confirmText: String = "OK",
    dismissByDialog: Boolean = true,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (dismissByDialog) onDismiss() },
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text = text) },
        confirmButton = { Button(onClick = onDismiss) { MarqueeText(text = confirmText) } }
    )
}

@Composable
fun SimpleAlertDialog(
    title: String,
    text: String,
    confirmText: String = "OK",
    dismissText: String = "Cancel",
    dismissByDialog: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (dismissByDialog) onDismiss() },
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text = text) },
        confirmButton = { Button(onClick = onConfirm) { MarqueeText(text = confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { MarqueeText(text = dismissText) } }
    )
}

@Composable
fun SimpleEditDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    extraBody: @Composable (() -> Unit)? = null,
    onDismissRequest: () -> Unit = {},
    onConfirm: () -> Unit = {}
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                extraBody?.invoke()
                val focusManager = LocalFocusManager.current
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = value,
                    onValueChange = onValueChange,
                    label = label,
                    singleLine = singleLine,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton(modifier = Modifier.weight(1f), onClick = onDismissRequest) { MarqueeText(text = "Cancel") }
                    Button(modifier = Modifier.weight(1f), onClick = onConfirm) { MarqueeText(text = "Confirm") }
                }
            }
        }
    }
}

@Composable
fun ProgressDialog(
    title: String = "Please wait…",
    text: String? = null
) {
    Dialog(onDismissRequest = {}) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 6.dp) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                text?.let { Text(text = it, style = MaterialTheme.typography.labelSmall) }
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
