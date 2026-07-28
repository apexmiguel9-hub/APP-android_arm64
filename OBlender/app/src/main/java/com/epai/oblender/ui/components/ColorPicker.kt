package com.epai.oblender.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class SimpleColorPickerController(private val initial: Color) {
    private var _color = mutableStateOf(initial)
    val color: State<Color> = _color

    fun getOriginalColor(): Color = initial

    fun setColor(c: Color) {
        _color.value = c
    }
}

@Composable
fun rememberColorPickerController(initialColor: Color): SimpleColorPickerController {
    return remember(initialColor) { SimpleColorPickerController(initialColor) }
}

@Composable
fun TransparentChecker(
    modifier: Modifier = Modifier,
    gridSize: Float = 18f
) {
    Box(modifier = modifier.background(Color.White)) {
        val checkColor = Color(0xFFCCCCCC)
        Box(modifier = Modifier.fillMaxSize()) {
            for (row in 0 until 3) {
                for (col in 0 until 3) {
                    if ((row + col) % 2 == 0) {
                        Box(
                            modifier = Modifier
                                .size((gridSize / 3).dp)
                                .background(checkColor)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ColorPickerDialog(
    colorController: SimpleColorPickerController,
    onCancel: () -> Unit,
    onConfirm: (Color) -> Unit
) {
    val currentColor by colorController.color
    var tempRed by remember { mutableFloatStateOf(currentColor.red) }
    var tempGreen by remember { mutableFloatStateOf(currentColor.green) }
    var tempBlue by remember { mutableFloatStateOf(currentColor.blue) }

    LaunchedEffect(tempRed, tempGreen, tempBlue) {
        colorController.setColor(Color(tempRed, tempGreen, tempBlue))
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("ColorPicker") },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(currentColor, RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.height(8.dp))
                Text("Red", style = MaterialTheme.typography.labelSmall)
                Slider(value = tempRed, onValueChange = { tempRed = it }, valueRange = 0f..1f)
                Text("Green", style = MaterialTheme.typography.labelSmall)
                Slider(value = tempGreen, onValueChange = { tempGreen = it }, valueRange = 0f..1f)
                Text("Blue", style = MaterialTheme.typography.labelSmall)
                Slider(value = tempBlue, onValueChange = { tempBlue = it }, valueRange = 0f..1f)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentColor) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    )
}
