package com.epai.oblender.ui.screens.main.control_editor.edit_widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.layer_controller.observable.*
import com.epai.oblender.R
import com.epai.oblender.ui.base.BaseScreen
import com.epai.oblender.ui.screens.TitledNavKey
import com.epai.oblender.ui.screens.content.elements.CategoryItem
import com.epai.oblender.ui.screens.main.control_editor.InfoLayoutTextItem

private enum class DialogAlpha(val alpha: Float, val buttonText: Int) {
    OPAQUE(1.0f, R.string.control_editor_edit_dialog_open_preview),
    SEMI_TRANSPARENT(0.3f, R.string.control_editor_edit_dialog_close_preview);
}

@Composable
fun EditWidgetDialog(
    visible: Boolean, data: SelectedWidgetData?, styles: List<ObservableButtonStyle>,
    onDismissRequest: () -> Unit, onDelete: (ObservableWidget, ObservableControlLayer) -> Unit,
    onClone: (ObservableWidget, ObservableControlLayer) -> Unit,
    onEditWidgetText: (ObservableTranslatableString) -> Unit,
    switchControlLayers: (ObservableNormalData, ClickEvent.Type) -> Unit,
    sendText: (ObservableNormalData) -> Unit, openStyleList: () -> Unit
) {
    AnimatedVisibility(modifier = Modifier.fillMaxSize(), visible = visible, enter = fadeIn(), exit = fadeOut()) {
        var dialogAlpha by remember { mutableStateOf(DialogAlpha.OPAQUE) }
        val alpha by animateFloatAsState(dialogAlpha.alpha)
        var currentPage by remember { mutableStateOf<EditWidgetCategory>(EditWidgetCategory.Info) }

        Box(modifier = Modifier.fillMaxSize().alpha(alpha), contentAlignment = Alignment.Center) {
            if (visible) {
                Box(modifier = Modifier.fillMaxSize().alpha(0f).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismissRequest))
            }
            if (data != null) {
                Surface(modifier = Modifier.fillMaxWidth(0.75f).fillMaxHeight().padding(all = 16.dp), shadowElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, shape = MaterialTheme.shapes.extraLarge) {
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            // Tab sidebar
                            Column(modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(modifier = Modifier.height(12.dp))
                                editWidgetCategories.forEach { category ->
                                    NavigationRailItem(selected = currentPage == category.key, onClick = { currentPage = category.key as EditWidgetCategory },
                                        icon = { category.icon() },
                                        label = { Text(text = stringResource(category.textRes), style = MaterialTheme.typography.labelMedium) })
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                            // Content area
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                when (currentPage) {
                                    EditWidgetCategory.Info -> EditWidgetInfo(data = data.data)
                                    EditWidgetCategory.TextStyle -> EditTextStyle(data = data.data, onEditWidgetText = onEditWidgetText)
                                    EditWidgetCategory.ClickEvent -> {
                                        if (data.data is ObservableNormalData) {
                                            EditWidgetClickEvent(data = data.data, switchControlLayers = switchControlLayers, sendText = sendText)
                                        }
                                    }
                                    EditWidgetCategory.Style -> EditWidgetStyle(data = data.data, styles = styles, openStyleList = openStyleList)
                                }
                            }
                        }
                        // Bottom bar
                        Row(modifier = Modifier.fillMaxWidth().padding(all = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            if (dialogAlpha != DialogAlpha.SEMI_TRANSPARENT) {
                                Button(onClick = { dialogAlpha = DialogAlpha.SEMI_TRANSPARENT }) { Text(stringResource(dialogAlpha.buttonText)) }
                                Spacer(Modifier.width(16.dp))
                            } else { Spacer(Modifier) }

                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                FilledTonalButton(onClick = { onDelete(data.data, data.layer) }) { Text(stringResource(R.string.generic_delete)) }
                                FilledTonalButton(onClick = { onClone(data.data, data.layer) }) { Text(stringResource(R.string.control_editor_edit_dialog_clone_widget)) }
                                Button(onClick = onDismissRequest) { Text(stringResource(R.string.generic_close)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditWidgetInfo(data: ObservableWidget) {
    // Simplified info page - just show basic info text
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Widget Info", style = MaterialTheme.typography.titleMedium)
        Text("ID: ${(data as? ObservableNormalData)?.uuid ?: (data as? ObservableTextData)?.uuid ?: ""}", style = MaterialTheme.typography.bodySmall)
    }
}
