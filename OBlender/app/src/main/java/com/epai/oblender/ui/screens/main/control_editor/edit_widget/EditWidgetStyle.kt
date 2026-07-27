package com.epai.oblender.ui.screens.main.control_editor.edit_widget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.layer_controller.observable.*
import com.epai.oblender.R
import com.epai.oblender.ui.screens.main.control_editor.InfoLayoutTextItem

@Composable
fun EditWidgetStyle(data: ObservableWidget, styles: List<ObservableButtonStyle>, openStyleList: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (styles.isEmpty()) {
            InfoLayoutTextItem(title = stringResource(R.string.control_editor_edit_style_config_empty), onClick = openStyleList)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                items(styles) { style ->
                    Text(text = style.displayName(), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
