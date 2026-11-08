package com.example.boschlaser.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

/** A position marker makes tools beyond the viewport discoverable. */
@Composable
internal fun ScrollableTools(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    val scroll = rememberScrollState()
    val color = MaterialTheme.colorScheme.primary
    Row(modifier.drawWithContent {
        drawContent()
        if (scroll.maxValue > 0) {
            val total = size.width + scroll.maxValue
            val start = size.width * scroll.value / total
            val end = start + size.width * size.width / total
            drawLine(color, Offset(start, size.height), Offset(end, size.height), 2.dp.toPx())
        }
    }.horizontalScroll(scroll), content = content)
}
