package com.example.boschlaser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.boschlaser.shared.model.WallAlignment

/** Compact creation settings share a row; values apply to subsequent walls. */
@Composable
fun DrawingWallTools(alignment: WallAlignment, lineLabel: String, lineLabels: List<String>,
    thicknessLabel: String, onAlignment: (WallAlignment) -> Unit, onThickness: () -> Unit,
    fill: com.example.boschlaser.shared.model.WallFill, fillLabels: List<String>, onFill: (com.example.boschlaser.shared.model.WallFill) -> Unit) {
    require(lineLabels.size == 3)
    require(fillLabels.size == 3)
    ScrollableTools(Modifier.fillMaxWidth()) {
        Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
            WallFillPicker(fill, fillLabels, onFill)
        }
        TextButton(onClick = onThickness, modifier = Modifier.semantics { contentDescription = thicknessLabel }) {
            Text(thicknessLabel, style = MaterialTheme.typography.labelMedium)
        }
        Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
            WallControlLinePicker(alignment, lineLabel, lineLabels, showLabel = false, onSelect = onAlignment)
        }
    }
}
