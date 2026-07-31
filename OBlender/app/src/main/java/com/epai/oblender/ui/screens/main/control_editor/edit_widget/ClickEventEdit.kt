package com.epai.oblender.ui.screens.main.control_editor.edit_widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.layer_controller.observable.ObservableNormalData
import com.epai.oblender.R
import com.epai.oblender.game.keycodes.ControlEventKeyName
import com.epai.oblender.game.keycodes.ControlEventKeycode
import com.epai.oblender.ui.components.MarqueeText
import com.epai.oblender.ui.control.Keyboard
import com.epai.oblender.ui.control.event.LAUNCHER_EVENT_SCROLL_DOWN
import com.epai.oblender.ui.control.event.LAUNCHER_EVENT_SCROLL_UP
import com.epai.oblender.ui.control.event.LAUNCHER_EVENT_SCROLL_DOWN
import com.epai.oblender.ui.screens.main.control_editor.InfoLayoutItem
import com.epai.oblender.ui.screens.main.control_editor.InfoLayoutSwitchItem
import com.epai.oblender.ui.screens.main.control_editor.InfoLayoutTextItem

private data class TabItem(val title: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWidgetClickEvent(
    data: ObservableNormalData,
    switchControlLayers: (ObservableNormalData, ClickEvent.Type) -> Unit,
    sendText: (ObservableNormalData) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(start = 4.dp, end = 8.dp)
            .fillMaxSize()
    ) {
        val tabs = remember {
            listOf(
                TabItem(R.string.control_editor_edit_event_basic),
                TabItem(R.string.control_editor_edit_event_launcher),
                TabItem(R.string.control_editor_edit_event_key)
            )
        }

        val pagerState = rememberPagerState(pageCount = { tabs.size })
        var selectedTabIndex by remember { mutableIntStateOf(0) }

        LaunchedEffect(selectedTabIndex) {
            pagerState.animateScrollToPage(selectedTabIndex)
        }

        SecondaryTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            tabs.forEachIndexed { index, item ->
                Tab(
                    selected = index == selectedTabIndex,
                    onClick = { selectedTabIndex = index },
                    text = {
                        MarqueeText(text = stringResource(item.title))
                    }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
        ) { page ->
            when (page) {
                0 -> EditBasicEvent(
                    modifier = Modifier.fillMaxSize(),
                    data = data,
                    switchControlLayers = switchControlLayers
                )
                1 -> EditLauncherEvent(
                    modifier = Modifier.fillMaxSize(),
                    data = data,
                    sendText = sendText
                )
                2 -> EditKeyEvent(
                    modifier = Modifier.fillMaxSize(),
                    data = data
                )
            }
        }
    }
}

@Composable
private fun EditBasicEvent(
    modifier: Modifier = Modifier,
    data: ObservableNormalData,
    switchControlLayers: (ObservableNormalData, ClickEvent.Type) -> Unit
) {
    Column(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier)

        InfoLayoutSwitchItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_event_swipple),
            value = data.isSwipple,
            onValueChange = { value -> data.isSwipple = value }
        )

        InfoLayoutSwitchItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_event_penetrable),
            value = data.isPenetrable,
            onValueChange = { value -> data.isPenetrable = value }
        )

        InfoLayoutSwitchItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_event_toggleable),
            value = data.isToggleable,
            onValueChange = { value -> data.isToggleable = value }
        )

        Spacer(Modifier)
    }
}

private data class LauncherEventData(
    val mouseLeft: Boolean = false,
    val mouseMiddle: Boolean = false,
    val mouseRight: Boolean = false,
    val mouseScrollUp: Boolean = false,
    val mouseScrollDown: Boolean = false
)

@Composable
private fun EditLauncherEvent(
    modifier: Modifier = Modifier,
    data: ObservableNormalData,
    sendText: (ObservableNormalData) -> Unit
) {
    var eventData by remember { mutableStateOf(LauncherEventData()) }
    LaunchedEffect(data.clickEvents) {
        var mouseLeft = false
        var mouseMiddle = false
        var mouseRight = false
        var mouseScrollUp = false
        var mouseScrollDown = false
        data.clickEvents.forEach { event ->
            if (event.type == ClickEvent.Type.LauncherEvent) {
                if (!mouseLeft) mouseLeft = event.key == ControlEventKeycode.GLFW_MOUSE_BUTTON_LEFT
                if (!mouseMiddle) mouseMiddle = event.key == ControlEventKeycode.GLFW_MOUSE_BUTTON_MIDDLE
                if (!mouseRight) mouseRight = event.key == ControlEventKeycode.GLFW_MOUSE_BUTTON_RIGHT
                if (!mouseScrollUp) mouseScrollUp = event.key == LAUNCHER_EVENT_SCROLL_UP
                if (!mouseScrollDown) mouseScrollDown = event.key == LAUNCHER_EVENT_SCROLL_DOWN
            }
        }
        eventData = LauncherEventData(
            mouseLeft = mouseLeft, mouseMiddle = mouseMiddle, mouseRight = mouseRight,
            mouseScrollUp = mouseScrollUp, mouseScrollDown = mouseScrollDown
        )
    }

    Column(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        InfoLayoutSwitchItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_event_launcher_mouse_left),
            value = eventData.mouseLeft,
            onValueChange = { value ->
                val event = ClickEvent(ClickEvent.Type.LauncherEvent, ControlEventKeycode.GLFW_MOUSE_BUTTON_LEFT)
                if (value) data.addEvent(event) else data.removeEvent(event)
            }
        )

        InfoLayoutSwitchItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_event_launcher_mouse_middle),
            value = eventData.mouseMiddle,
            onValueChange = { value ->
                val event = ClickEvent(ClickEvent.Type.LauncherEvent, ControlEventKeycode.GLFW_MOUSE_BUTTON_MIDDLE)
                if (value) data.addEvent(event) else data.removeEvent(event)
            }
        )

        InfoLayoutSwitchItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_event_launcher_mouse_right),
            value = eventData.mouseRight,
            onValueChange = { value ->
                val event = ClickEvent(ClickEvent.Type.LauncherEvent, ControlEventKeycode.GLFW_MOUSE_BUTTON_RIGHT)
                if (value) data.addEvent(event) else data.removeEvent(event)
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        InfoLayoutSwitchItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_event_launcher_mouse_scroll_up),
            value = eventData.mouseScrollUp,
            onValueChange = { value ->
                val event = ClickEvent(ClickEvent.Type.LauncherEvent, LAUNCHER_EVENT_SCROLL_UP)
                if (value) data.addEvent(event) else data.removeEvent(event)
            }
        )

        InfoLayoutSwitchItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_event_launcher_mouse_scroll_down),
            value = eventData.mouseScrollDown,
            onValueChange = { value ->
                val event = ClickEvent(ClickEvent.Type.LauncherEvent, LAUNCHER_EVENT_SCROLL_DOWN)
                if (value) data.addEvent(event) else data.removeEvent(event)
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        InfoLayoutTextItem(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.control_editor_edit_event_launcher_send_text),
            onClick = { sendText(data) }
        )

        Spacer(Modifier)
    }
}

@Composable
private fun EditKeyEvent(
    modifier: Modifier = Modifier,
    data: ObservableNormalData
) {
    var showKeyboard by remember { mutableStateOf(false) }

    val scrollState = rememberLazyListState()
    LazyColumn(
        modifier = modifier
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        state = scrollState,
    ) {
        item {
            InfoLayoutTextItem(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.control_editor_edit_event_key_new),
                onClick = { showKeyboard = true },
                showArrow = false
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(data.clickEvents.filter { it.type == ClickEvent.Type.Key }) { event ->
            EditKeyItem(
                modifier = Modifier.fillMaxWidth(),
                keyEvent = event,
                onDelete = { data.removeEvent(event) }
            )
        }
    }

    if (showKeyboard) {
        Keyboard(
            onDismissRequest = { showKeyboard = false },
            isTapMode = true,
            onTap = { selectedKey ->
                val event = ClickEvent(type = ClickEvent.Type.Key, key = selectedKey)
                data.addEvent(event)
                showKeyboard = false
            }
        )
    }
}

@Composable
private fun EditKeyItem(
    modifier: Modifier = Modifier,
    keyEvent: ClickEvent,
    onDelete: () -> Unit
) {
    val name = remember(keyEvent.key) { ControlEventKeyName.getNameByKey(keyEvent.key) }

    InfoLayoutItem(
        modifier = modifier,
        onClick = {}
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MarqueeText(
                text = stringResource(R.string.control_editor_edit_event_key_value, name ?: keyEvent.key),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                painter = painterResource(R.drawable.ic_delete_outlined),
                contentDescription = stringResource(R.string.generic_delete)
            )
        }
    }
}
