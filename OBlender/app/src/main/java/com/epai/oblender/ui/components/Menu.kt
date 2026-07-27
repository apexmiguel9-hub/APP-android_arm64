package com.epai.oblender.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.epai.oblender.R
import com.epai.oblender.ui.screens.content.elements.DisabledAlpha
import kotlin.math.roundToInt

enum class MenuState {
    NONE { override fun next() = SHOW },
    SHOW { override fun next() = HIDE },
    HIDE { override fun next() = SHOW };
    abstract fun next(): MenuState
}

@Composable
fun DualMenuSubscreen(
    state: MenuState,
    closeScreen: () -> Unit,
    shape: Shape = RoundedCornerShape(21.dp),
    backgroundColor: Color = Color.Black.copy(alpha = 0.25f),
    leftMenuTitle: (@Composable BoxScope.() -> Unit)? = null,
    leftMenuContent: @Composable ColumnScope.() -> Unit = {},
    rightMenuTitle: (@Composable BoxScope.() -> Unit)? = null,
    rightMenuContent: @Composable ColumnScope.() -> Unit = {}
) {
    val visible = state == MenuState.SHOW
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize().background(color = backgroundColor).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = closeScreen))
        }
        Box(modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth(fraction = 1f / 3f).fillMaxHeight().padding(top = 12.dp, start = 12.dp, bottom = 12.dp)) {
            AnimatedVisibility(visible = visible, enter = fadeIn() + slideInHorizontally { -40 }, exit = fadeOut() + slideOutHorizontally { -40 }) {
                Card(shape = shape, modifier = Modifier.fillMaxSize(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)) {
                    leftMenuTitle?.let {
                        Surface(modifier = Modifier.height(48.dp).fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = it) }
                        HorizontalDivider()
                    }
                    Column(modifier = Modifier.weight(1f), content = leftMenuContent)
                }
            }
        }
        Box(modifier = Modifier.align(Alignment.CenterEnd).fillMaxWidth(fraction = 1f / 3f).fillMaxHeight().padding(top = 12.dp, end = 12.dp, bottom = 12.dp)) {
            AnimatedVisibility(visible = visible, enter = fadeIn() + slideInHorizontally { 40 }, exit = fadeOut() + slideOutHorizontally { 40 }) {
                Card(shape = shape, modifier = Modifier.fillMaxSize(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)) {
                    rightMenuTitle?.let {
                        Surface(modifier = Modifier.height(48.dp).fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = it) }
                        HorizontalDivider()
                    }
                    Column(modifier = Modifier.weight(1f), content = rightMenuContent)
                }
            }
        }
    }
}

@Composable
fun MenuTextButton(
    modifier: Modifier = Modifier,
    text: String,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    appendLayout: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    Surface(modifier = modifier, shape = shape, color = color, contentColor = contentColor, onClick = onClick, enabled = enabled) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            MarqueeText(modifier = Modifier.weight(1f).padding(all = 16.dp).alpha(if (enabled) 1f else DisabledAlpha), text = text, style = MaterialTheme.typography.titleSmall)
            appendLayout?.invoke()
        }
    }
}

@Composable
fun MenuSwitchButton(
    modifier: Modifier = Modifier,
    text: String,
    switch: Boolean,
    onSwitch: (Boolean) -> Unit,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(modifier = modifier, shape = shape, color = color, contentColor = contentColor, onClick = { onSwitch(!switch) }, enabled = enabled) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            MarqueeText(modifier = Modifier.weight(1f).padding(horizontal = 4.dp).alpha(if (enabled) 1f else DisabledAlpha), text = text, style = MaterialTheme.typography.titleSmall)
            Switch(checked = switch, onCheckedChange = onSwitch, enabled = enabled)
        }
    }
}

@Composable
fun <E> MenuListLayout(
    modifier: Modifier = Modifier,
    title: String,
    items: List<E>,
    currentItem: E,
    onItemChange: (E) -> Unit,
    getItemText: @Composable (E) -> String,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(modifier = modifier, shape = shape, color = color, contentColor = contentColor, onClick = {}, enabled = enabled) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.clickable(enabled = enabled) { expanded = !expanded }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    MarqueeText(text = title, style = MaterialTheme.typography.titleSmall)
                    Text(text = getItemText(currentItem), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (enabled && items.isNotEmpty()) {
                AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically() + fadeOut()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        items.forEach { item ->
                            Row(modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { if (expanded) { onItemChange(item); expanded = false } }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = currentItem == item, onClick = { onItemChange(item); expanded = false })
                                Text(text = getItemText(item), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
