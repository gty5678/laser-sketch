package com.example.boschlaser.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.boschlaser.shared.model.WallAlignment

/** Compact inner / center / outer control shared by creation and properties. */
@Composable
fun WallControlLinePicker(alignment: WallAlignment, label: String, labels: List<String>, showLabel: Boolean = true, onSelect: (WallAlignment) -> Unit) {
    require(labels.size == 3)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showLabel) Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1)
        Surface(shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Row(Modifier.selectableGroup()) {
                listOf(WallAlignment.LEFT to labels[1], WallAlignment.CENTER to labels[0], WallAlignment.RIGHT to labels[2])
                    .forEachIndexed { index, (choice, text) ->
                        if (index > 0) Box(Modifier.width(1.dp).height(30.dp).background(MaterialTheme.colorScheme.outline))
                        val selected = alignment == choice
                        Box(Modifier.widthIn(min = 56.dp).height(30.dp)
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(choice) })
                            .semantics { contentDescription = "$label $text" }
                            .padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                            Text(text, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1)
                        }
                    }
            }
        }
    }
}
