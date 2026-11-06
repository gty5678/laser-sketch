package com.example.boschlaser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.boschlaser.shared.model.DoorStyle

@Composable
fun DoorStylePicker(selected: DoorStyle, labels: Map<DoorStyle, String>, onSelect: (DoorStyle) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { (style, label) ->
            FilterChip(selected = selected == style, onClick = { onSelect(style) }, label = { Text(label) })
        }
    }
}
