package com.epai.oblender.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SimpleTextSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    decimalFormat: String = "#0.00",
    suffix: String? = null,
    onTextClick: (() -> Unit)? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    shorter: Boolean = false,
    fineTuningControl: Boolean = false,
    fineTuningStep: Float = 0.5f
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Slider(
            modifier = Modifier.weight(1f),
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished
        )
        Spacer(Modifier.width(4.dp))
        val display = if (suffix != null) "%.0f%s".format(value, suffix) else "%.0f".format(value)
        Text(
            modifier = Modifier.clickable(enabled = enabled && onTextClick != null) { onTextClick?.invoke() },
            text = display,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}

@Composable
fun DefaultSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled, modifier = modifier)
}

@Composable
fun LittleTextLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
    )
}

@Composable
fun SliderValueEditDialog(
    onDismissRequest: () -> Unit,
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)?
) {
    var textValue by remember(value) { mutableStateOf(value.toInt().toString()) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { newText ->
                        textValue = newText
                        newText.toFloatOrNull()?.let { v ->
                            if (v in valueRange) onValueChange(v)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    keyboardActions = KeyboardActions(onDone = { onDismissRequest() })
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    onValueChangeFinished = onValueChangeFinished
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text("OK") }
        }
    )
}
