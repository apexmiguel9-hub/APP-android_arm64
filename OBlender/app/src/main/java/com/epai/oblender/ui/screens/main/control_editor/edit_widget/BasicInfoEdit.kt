package com.epai.oblender.ui.screens.main.control_editor.edit_widget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.layer_controller.data.*
import com.movtery.layer_controller.observable.*
import com.epai.oblender.R
import com.epai.oblender.ui.screens.main.control_editor.*

@Composable
fun EditWidgetInfo(
    screenKey: com.epai.oblender.ui.screens.TitledNavKey,
    currentKey: com.epai.oblender.ui.screens.TitledNavKey?,
    data: ObservableWidget,
    onPreviewRequested: () -> Unit,
    onDismissRequested: () -> Unit
) {
    // Simplified - just show basic info
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Widget Info", style = MaterialTheme.typography.titleMedium)
        Text("ID: ${data.uuid}", style = MaterialTheme.typography.bodySmall)
    }
}
