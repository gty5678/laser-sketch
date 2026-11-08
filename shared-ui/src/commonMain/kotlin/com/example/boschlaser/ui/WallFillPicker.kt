package com.example.boschlaser.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.boschlaser.shared.model.WallFill

/** Same visual swatches for creation and selected-wall properties; labels remain accessible. */
@Composable
fun WallFillPicker(fill: WallFill, labels: List<String>, onSelect: (WallFill) -> Unit) {
    FillPatternPicker(fill, labels, onSelect, WallFill.entries, "wall-fill-switch")
}

@Composable
private fun FillPatternPicker(fill: WallFill, labels: List<String>, onSelect: (WallFill) -> Unit,
    choices: List<WallFill>, tag: String) {
    require(labels.size == choices.size)
    val selectionColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    Surface(shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, outlineColor)) {
        Row(Modifier.selectableGroup().testTag(tag)) {
            choices.forEachIndexed { index, choice ->
                Box(Modifier.size(40.dp, 30.dp)
                    .selectable(selected = fill == choice, role = Role.RadioButton, onClick = { onSelect(choice) })
                    .semantics { contentDescription = labels[index] }) {
                    Canvas(Modifier.matchParentSize()) {
                        drawRect(if (choice == WallFill.BLACK) Color.Black else Color.White)
                        if (choice == WallFill.HATCH) {
                            val spacing = 6.dp.toPx()
                            var x = -size.height
                            clipRect {
                                while (x < size.width) {
                                    drawLine(Color.Black, Offset(x, size.height), Offset(x + size.height, 0f), 1.dp.toPx())
                                    x += spacing
                                }
                            }
                        }
                        if (index > 0) drawLine(outlineColor, Offset.Zero, Offset(0f, size.height), 1.dp.toPx())
                        if (fill == choice) {
                            val inset = 2.dp.toPx()
                            drawRect(selectionColor, Offset(inset, inset),
                                androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                                style = Stroke(2.dp.toPx()))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ColumnFillPicker(fill: com.example.boschlaser.shared.model.ColumnFill, labels: List<String>,
    onSelect: (com.example.boschlaser.shared.model.ColumnFill) -> Unit) {
    FillPatternPicker(if (fill == com.example.boschlaser.shared.model.ColumnFill.WHITE) WallFill.BLANK else WallFill.BLACK,
        labels, { onSelect(if (it == WallFill.BLANK) com.example.boschlaser.shared.model.ColumnFill.WHITE
            else com.example.boschlaser.shared.model.ColumnFill.BLACK) }, listOf(WallFill.BLANK, WallFill.BLACK), "column-fill-switch")
}
