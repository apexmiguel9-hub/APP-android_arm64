package com.epai.oblender.ui.screens.main.control_editor

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.layer_controller.data.VisibilityType
import com.movtery.layer_controller.observable.ObservableButtonStyle
import com.movtery.layer_controller.observable.ObservableControlLayer
import com.movtery.layer_controller.observable.ObservableNormalData
import com.movtery.layer_controller.observable.ObservableTranslatableString
import com.movtery.layer_controller.observable.ObservableWidget
import com.epai.oblender.R
import com.epai.oblender.ui.components.FloatingBall
import com.epai.oblender.ui.components.MenuState
import com.epai.oblender.ui.components.MenuTextButton

sealed interface EditorOperation {
    data object None : EditorOperation
    data object SelectButton : EditorOperation
    data object OpenStyleList : EditorOperation
    data object CreateStyle : EditorOperation
    data object EditButtonStyle : EditorOperation
    data class DeleteButtonStyle(val style: ObservableButtonStyle) : EditorOperation
    data object Saving : EditorOperation
    data class SaveFailed(val error: Throwable) : EditorOperation
}

sealed interface EditorWidgetOperation {
    data object None : EditorWidgetOperation
    data class CloneButton(val data: ObservableWidget, val layer: ObservableControlLayer) : EditorWidgetOperation
    data class DeleteButton(val data: ObservableWidget, val layer: ObservableControlLayer) : EditorWidgetOperation
    data class EditWidgetText(val string: ObservableTranslatableString) : EditorWidgetOperation
    data class SwitchLayersVisibility(val data: ObservableNormalData, val type: ClickEvent.Type) : EditorWidgetOperation
    data class SendText(val data: ObservableNormalData) : EditorWidgetOperation
}

sealed interface EditorWarningOperation {
    data object None : EditorWarningOperation
    data object WarningNoLayers : EditorWarningOperation
    data object WarningNoSelectLayer : EditorWarningOperation
}

enum class PreviewScenario(val textRes: Int) {
    InGame(R.string.control_editor_menu_preview_mode_in_game),
    InMenu(R.string.control_editor_menu_preview_mode_in_menu)
}

@Composable
fun VisibilityType.getVisibilityText(): String {
    val textRes = when (this) {
        VisibilityType.ALWAYS -> R.string.control_editor_edit_visibility_always
        VisibilityType.IN_GAME -> R.string.control_editor_edit_visibility_in_game
        VisibilityType.IN_MENU -> R.string.control_editor_edit_visibility_in_menu
    }
    return stringResource(textRes)
}

@Composable
fun MenuBox(
    position: Offset,
    onPositionChanged: (Offset) -> Unit,
    opened: Boolean,
    onClick: () -> Unit
) {
    FloatingBall(
        position = position,
        onPositionChanged = onPositionChanged,
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .padding(all = 2.dp)
                .size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(opened) { state ->
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(
                        if (state) R.drawable.ic_menu_open else R.drawable.ic_menu
                    ),
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
fun EditorMenu(
    state: MenuState,
    closeScreen: () -> Unit,
    addNewButton: () -> Unit,
    saveAndExit: () -> Unit
) {
    if (state == MenuState.SHOW) {
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .wrapContentWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            onClick = {}
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MenuTextButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.control_editor_menu_new_widget_button),
                    onClick = { addNewButton(); closeScreen() },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                MenuTextButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.control_editor_menu_save_and_exit),
                    onClick = { saveAndExit() },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


