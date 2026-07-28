package com.epai.oblender.ui.screens.main.control_editor.edit_widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.layer_controller.data.ButtonPosition
import com.movtery.layer_controller.data.ButtonSize
import com.movtery.layer_controller.data.MIN_SIZE_DP
import com.movtery.layer_controller.data.SIZE_PERCENTAGE_EDITOR
import com.movtery.layer_controller.data.VisibilityType
import com.movtery.layer_controller.observable.ObservableNormalData
import com.movtery.layer_controller.observable.ObservableTextData
import com.movtery.layer_controller.observable.ObservableWidget
import com.epai.oblender.R
import com.epai.oblender.ui.screens.main.control_editor.InfoLayoutListItem
import com.epai.oblender.ui.screens.main.control_editor.InfoLayoutSliderItem
import com.epai.oblender.ui.screens.main.control_editor.getVisibilityText

@Composable
fun EditWidgetInfo(
    data: ObservableWidget
) {
    val screenSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val screenWidth = remember(screenSize, density) {
        with(density) { screenSize.width.toDp() }.value
    }
    val screenHeight = remember(screenSize, density) {
        with(density) { screenSize.height.toDp() }.value
    }

    LazyColumn(
        modifier = Modifier
            .padding(start = 4.dp, end = 8.dp)
            .fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (data) {
            is ObservableTextData -> {
                commonInfos(
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    visibilityType = data.visibilityType,
                    onVisibilityTypeChanged = { data.visibilityType = it },
                    position = data.position,
                    onPositionChanged = { data.position = it },
                    buttonSize = data.buttonSize,
                    onButtonSizeChanged = { data.buttonSize = it }
                )
            }
            is ObservableNormalData -> {
                commonInfos(
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    visibilityType = data.visibilityType,
                    onVisibilityTypeChanged = { data.visibilityType = it },
                    position = data.position,
                    onPositionChanged = { data.position = it },
                    buttonSize = data.buttonSize,
                    onButtonSizeChanged = { data.buttonSize = it }
                )
            }
        }
    }
}

private fun LazyListScope.commonInfos(
    screenWidth: Float,
    screenHeight: Float,
    visibilityType: VisibilityType,
    onVisibilityTypeChanged: (VisibilityType) -> Unit,
    position: ButtonPosition,
    onPositionChanged: (ButtonPosition) -> Unit,
    buttonSize: ButtonSize,
    onButtonSizeChanged: (ButtonSize) -> Unit,
) {
    item {
        InfoLayoutListItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_visibility),
            items = VisibilityType.entries,
            selectedItem = visibilityType,
            onItemSelected = { onVisibilityTypeChanged(it) },
            getItemText = { it.getVisibilityText() }
        )
    }

    item {
        Spacer(modifier = Modifier.height(4.dp))
    }

    item {
        InfoLayoutSliderItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_position_x),
            value = position.x / 100f,
            onValueChange = {
                onPositionChanged(position.copy(x = (it * 100).toInt()))
            },
            valueRange = 0f..100f,
            decimalFormat = "#0.00",
            suffix = "%"
        )
    }

    item {
        InfoLayoutSliderItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_position_y),
            value = position.y / 100f,
            onValueChange = {
                onPositionChanged(position.copy(y = (it * 100).toInt()))
            },
            valueRange = 0f..100f,
            decimalFormat = "#0.00",
            suffix = "%"
        )
    }

    item {
        Spacer(modifier = Modifier.height(4.dp))
    }

    item {
        InfoLayoutListItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_size_type),
            items = ButtonSize.Type.entries,
            selectedItem = buttonSize.type,
            onItemSelected = { onButtonSizeChanged(buttonSize.copy(type = it)) },
            getItemText = { type ->
                val textRes = when (type) {
                    ButtonSize.Type.Dp -> R.string.control_editor_edit_size_type_dp
                    ButtonSize.Type.Percentage -> R.string.control_editor_edit_size_type_percentage
                    ButtonSize.Type.WrapContent -> R.string.control_editor_edit_size_type_wrap_content
                }
                stringResource(textRes)
            }
        )
    }

    when (buttonSize.type) {
        ButtonSize.Type.Dp -> {
            item {
                InfoLayoutSliderItem(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.control_editor_edit_size_width),
                    value = buttonSize.widthDp,
                    onValueChange = {
                        onButtonSizeChanged(buttonSize.copy(widthDp = it))
                    },
                    valueRange = MIN_SIZE_DP..screenWidth,
                    suffix = "Dp"
                )
            }

            item {
                InfoLayoutSliderItem(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.control_editor_edit_size_height),
                    value = buttonSize.heightDp,
                    onValueChange = {
                        onButtonSizeChanged(buttonSize.copy(heightDp = it))
                    },
                    valueRange = MIN_SIZE_DP..screenHeight,
                    suffix = "Dp"
                )
            }
        }
        ButtonSize.Type.Percentage -> {
            @Composable fun ButtonSize.Reference.getReferenceText(): String {
                val textRes = when (this) {
                    ButtonSize.Reference.ScreenWidth -> R.string.control_editor_edit_size_reference_screen_width
                    ButtonSize.Reference.ScreenHeight -> R.string.control_editor_edit_size_reference_screen_height
                }
                return stringResource(textRes)
            }

            item {
                InfoLayoutSliderItem(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.control_editor_edit_size_width),
                    value = buttonSize.widthPercentage / 100f,
                    onValueChange = {
                        onButtonSizeChanged(buttonSize.copy(widthPercentage = (it * 100).toInt()))
                    },
                    valueRange = SIZE_PERCENTAGE_EDITOR,
                    decimalFormat = "#0.00",
                    suffix = "%"
                )
            }

            item {
                InfoLayoutListItem(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.control_editor_edit_size_width_reference),
                    items = ButtonSize.Reference.entries,
                    selectedItem = buttonSize.widthReference,
                    onItemSelected = {
                        onButtonSizeChanged(buttonSize.copy(widthReference = it))
                    },
                    getItemText = { it.getReferenceText() }
                )
            }

            item {
                InfoLayoutSliderItem(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.control_editor_edit_size_height),
                    value = buttonSize.heightPercentage / 100f,
                    onValueChange = {
                        onButtonSizeChanged(buttonSize.copy(heightPercentage = (it * 100).toInt()))
                    },
                    valueRange = SIZE_PERCENTAGE_EDITOR,
                    decimalFormat = "#0.00",
                    suffix = "%"
                )
            }

            item {
                InfoLayoutListItem(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.control_editor_edit_size_height_reference),
                    items = ButtonSize.Reference.entries,
                    selectedItem = buttonSize.heightReference,
                    onItemSelected = {
                        onButtonSizeChanged(buttonSize.copy(heightReference = it))
                    },
                    getItemText = { it.getReferenceText() }
                )
            }
        }
        ButtonSize.Type.WrapContent -> {}
    }
}
