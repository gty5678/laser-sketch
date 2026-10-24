package com.example.boschlaser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.boschlaser.shared.model.WallAlignment

@Composable
fun WallControlLinePicker(alignment: WallAlignment, label: String, labels: List<String>, onSelect: (WallAlignment) -> Unit) {
    require(labels.size == 3)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label)
        listOf(WallAlignment.CENTER, WallAlignment.LEFT, WallAlignment.RIGHT).forEachIndexed { index, choice ->
            FilterChip(selected = alignment == choice, onClick = { onSelect(choice) }, label = { Text(labels[index]) },
                modifier = Modifier.weight(1f).semantics { contentDescription = "$label ${labels[index]}" })
        }
    }
}
