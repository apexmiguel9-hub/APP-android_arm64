package com.epai.oblender.ui.screens.main.control_editor.edit_widget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.layer_controller.data.TextAlignment
import com.movtery.layer_controller.observable.*
import com.epai.oblender.R
import com.epai.oblender.ui.screens.main.control_editor.*

@Composable
fun EditTextStyle(data: ObservableWidget, onEditWidgetText: (ObservableTranslatableString) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(start = 4.dp, end = 8.dp), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (data) {
            is ObservableTextData -> commonItems(onEditWidgetText = { onEditWidgetText(data.text) }, textAlignment = data.textAlignment, onTextAlignmentChanged = { data.textAlignment = it }, textBold = data.textBold, onTextBoldChanged = { data.textBold = it }, textItalic = data.textItalic, onTextItalicChanged = { data.textItalic = it }, textUnderline = data.textUnderline, onTextUnderlineChanged = { data.textUnderline = it })
            is ObservableNormalData -> commonItems(onEditWidgetText = { onEditWidgetText(data.text) }, textAlignment = data.textAlignment, onTextAlignmentChanged = { data.textAlignment = it }, textBold = data.textBold, onTextBoldChanged = { data.textBold = it }, textItalic = data.textItalic, onTextItalicChanged = { data.textItalic = it }, textUnderline = data.textUnderline, onTextUnderlineChanged = { data.textUnderline = it })
        }
    }
}

private fun LazyListScope.commonItems(onEditWidgetText: () -> Unit, textAlignment: TextAlignment, onTextAlignmentChanged: (TextAlignment) -> Unit, textBold: Boolean, onTextBoldChanged: (Boolean) -> Unit, textItalic: Boolean, onTextItalicChanged: (Boolean) -> Unit, textUnderline: Boolean, onTextUnderlineChanged: (Boolean) -> Unit) {
    item { InfoLayoutTextItem(title = stringResource(R.string.control_editor_edit_text), onClick = onEditWidgetText) }
    item { InfoLayoutSelectItem(title = stringResource(R.string.control_editor_edit_text_alignment), options = TextAlignment.entries, current = textAlignment, onClick = { if (textAlignment != it) onTextAlignmentChanged(it) }, label = { Text(it.name) }) }
    item { InfoLayoutSwitchItem(title = stringResource(R.string.control_editor_edit_text_bold), value = textBold, onValueChange = onTextBoldChanged) }
    item { InfoLayoutSwitchItem(title = stringResource(R.string.control_editor_edit_text_italic), value = textItalic, onValueChange = onTextItalicChanged) }
    item { InfoLayoutSwitchItem(title = stringResource(R.string.control_editor_edit_text_underline), value = textUnderline, onValueChange = onTextUnderlineChanged) }
}
