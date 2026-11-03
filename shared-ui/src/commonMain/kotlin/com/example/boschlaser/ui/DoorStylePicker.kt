package com.example.boschlaser.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.boschlaser.shared.model.DoorStyle

@Composable
fun DoorStylePicker(selected: DoorStyle, labels: Map<DoorStyle, String>, onSelect: (DoorStyle) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        labels.forEach { (style, label) ->
            FilterChip(selected = selected == style, onClick = { onSelect(style) }, label = { Text(label) })
        }
    }
}
