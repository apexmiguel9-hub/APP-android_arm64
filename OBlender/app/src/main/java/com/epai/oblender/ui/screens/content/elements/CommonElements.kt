package com.epai.oblender.ui.screens.content.elements

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.epai.oblender.ui.screens.TitledNavKey

data class CategoryItem(
    val key: TitledNavKey,
    val icon: @Composable () -> Unit,
    val textRes: Int,
    val division: Boolean = false
)

@Composable
fun CategoryIcon(
    painter: Painter,
    contentDescription: String
) {
    Icon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = Modifier.size(24.dp)
    )
}
