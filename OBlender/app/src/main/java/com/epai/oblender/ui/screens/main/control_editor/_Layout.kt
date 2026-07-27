package com.epai.oblender.ui.screens.main.control_editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.epai.oblender.R
import com.epai.oblender.ui.components.MarqueeText
import com.epai.oblender.ui.screens.content.elements.DisabledAlpha

@Composable
fun InfoLayoutSliderItem(
    modifier: Modifier = Modifier,
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    suffix: String? = null,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    InfoLayoutItem(modifier = modifier, onClick = {}, enabled = enabled, color = color, contentColor = contentColor) {
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, onValueChangeFinished = onValueChangeFinished, enabled = enabled)
        }
    }
}

@Composable
fun <E> InfoLayoutListItem(
    modifier: Modifier = Modifier,
    title: String,
    items: List<E>,
    selectedItem: E,
    onItemSelected: (E) -> Unit,
    getItemText: @Composable (E) -> String,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = color, contentColor = contentColor) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                MarqueeText(text = title, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = getItemText(selectedItem), style = MaterialTheme.typography.labelSmall)
            }
            if (items.isNotEmpty()) {
                AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically() + fadeOut()) {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).padding(vertical = 4.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                        items(items) { item ->
                            Row(modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onItemSelected(item); expanded = false }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedItem == item, onClick = { onItemSelected(item); expanded = false })
                                MarqueeText(text = getItemText(item), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoLayoutSwitchItem(
    modifier: Modifier = Modifier,
    title: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    InfoLayoutItem(modifier = modifier, onClick = { onValueChange(!value) }, enabled = enabled, color = color, contentColor = contentColor) {
        MarqueeText(modifier = Modifier.weight(1f).alpha(if (enabled) 1f else DisabledAlpha), text = title, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = value, onCheckedChange = onValueChange, enabled = enabled)
    }
}

@Composable
fun <E> InfoLayoutSelectItem(
    modifier: Modifier = Modifier,
    title: String,
    options: List<E>,
    current: E,
    onClick: (E) -> Unit,
    label: @Composable RowScope.(E) -> Unit,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    InfoLayoutItem(modifier = modifier, onClick = {}, color = color, contentColor = contentColor) {
        MarqueeText(modifier = Modifier.weight(1f), text = title, style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow {
            options.forEachIndexed { index, option ->
                SegmentedButton(selected = current == option, shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size), onClick = { onClick(option) }, label = { label(option) })
            }
        }
    }
}

@Composable
fun InfoLayoutTextItem(
    modifier: Modifier = Modifier,
    title: String,
    onClick: () -> Unit,
    showArrow: Boolean = true,
    selected: Boolean = false,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
) {
    InfoLayoutItem(modifier = modifier, onClick = onClick, selected = selected, color = color, contentColor = contentColor, enabled = enabled) {
        MarqueeText(modifier = Modifier.weight(1f).padding(vertical = 8.dp), text = title, style = MaterialTheme.typography.bodyMedium)
        if (showArrow) {
            Text(text = ">", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun InfoLayoutItem(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    selected: Boolean = false,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.large,
    borderColor: Color = MaterialTheme.colorScheme.primary,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable RowScope.() -> Unit
) {
    val borderWidth by animateDpAsState(if (selected) 2.dp else 0.dp)
    Surface(modifier = modifier.border(width = borderWidth, color = borderColor, shape = shape), color = color, contentColor = contentColor, shape = shape, onClick = onClick, enabled = enabled) {
        Row(modifier = Modifier.fillMaxWidth().clip(shape).padding(all = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
