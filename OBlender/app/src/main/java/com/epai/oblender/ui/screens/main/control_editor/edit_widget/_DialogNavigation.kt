package com.epai.oblender.ui.screens.main.control_editor.edit_widget

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.epai.oblender.R
import com.epai.oblender.ui.screens.TitledNavKey
import com.epai.oblender.ui.screens.content.elements.CategoryItem
import kotlinx.serialization.Serializable

sealed interface EditWidgetCategory : TitledNavKey {
    @Serializable data object Info : EditWidgetCategory
    @Serializable data object TextStyle : EditWidgetCategory
    @Serializable data object ClickEvent : EditWidgetCategory
    @Serializable data object Style : EditWidgetCategory
}

val editWidgetCategories = listOf(
    CategoryItem(EditWidgetCategory.Info, { Icon(painter = painterResource(R.drawable.ic_info_outlined), contentDescription = "Info", modifier = Modifier.size(24.dp)) }, R.string.control_editor_edit_category_info),
    CategoryItem(EditWidgetCategory.TextStyle, { Icon(painter = painterResource(R.drawable.ic_text_format), contentDescription = "Text", modifier = Modifier.size(24.dp)) }, R.string.control_editor_edit_text),
    CategoryItem(EditWidgetCategory.ClickEvent, { Icon(painter = painterResource(R.drawable.ic_touch_app_outlined), contentDescription = "Event", modifier = Modifier.size(24.dp)) }, R.string.control_editor_edit_category_event),
    CategoryItem(EditWidgetCategory.Style, { Icon(painter = painterResource(R.drawable.ic_style_outlined), contentDescription = "Style", modifier = Modifier.size(24.dp)) }, R.string.control_editor_edit_category_style)
)
