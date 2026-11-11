package com.example.boschlaser.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.boschlaser.shared.model.*

@Composable
fun BoardTools(shape: BoardShape?, fill: BoardFill, shapeLabels: List<String>, fillLabels: List<String>,
    onShape: (BoardShape) -> Unit, onFill: (BoardFill) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (shape != null) BoardSwitch(BoardShape.entries, shape, shapeLabels, "board-shape-switch", onShape) { value, color ->
            val inset = 9.dp.toPx()
            val width = size.width - 2 * inset
            val height = size.height - 12.dp.toPx()
            val top = 6.dp.toPx()
            when (value) {
                BoardShape.RECTANGLE -> drawRect(color, Offset(inset, top), Size(width, height), style = Stroke(1.5.dp.toPx()))
                BoardShape.CIRCLE -> drawCircle(color, height / 2, Offset(size.width / 2, size.height / 2), style = Stroke(1.5.dp.toPx()))
                BoardShape.POLYGON -> drawPath(Path().apply {
                    moveTo(inset, top + height); lineTo(inset, top + height * .25f)
                    lineTo(inset + width * .6f, top); lineTo(inset + width, top + height * .5f)
                    lineTo(inset + width * .75f, top + height); close()
                }, color, style = Stroke(1.5.dp.toPx()))
            }
        }
        BoardSwitch(BoardFill.entries, fill, fillLabels, "board-fill-switch", onFill) { value, color ->
            val inset = 5.dp.toPx()
            clipRect(inset, inset, size.width - inset, size.height - inset) {
                drawRect(Color.White)
                if (value != BoardFill.WHITE) drawBoardPattern(value, 6.dp.toPx().toDouble(), Offset.Zero, color)
            }
            drawRect(color, Offset(inset,inset), Size(size.width-2*inset,size.height-2*inset), style = Stroke(1.dp.toPx()))
        }
    }
}

@Composable
private fun <T> BoardSwitch(choices: List<T>, selected: T, labels: List<String>, tag: String,
    onSelect: (T) -> Unit, draw: androidx.compose.ui.graphics.drawscope.DrawScope.(T, Color) -> Unit) {
    require(labels.size == choices.size)
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    Surface(shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, outline)) {
        Row(Modifier.selectableGroup().testTag(tag)) {
            choices.forEachIndexed { i, value ->
                Canvas(Modifier.size(40.dp, 30.dp)
                    .selectable(selected == value, role = Role.RadioButton, onClick = { onSelect(value) })
                    .semantics { contentDescription = labels[i] }) {
                    draw(value, outline)
                    if (i > 0) drawLine(outline, Offset.Zero, Offset(0f,size.height),1.dp.toPx())
                    if (selected == value) {
                        val inset = 2.dp.toPx()
                        drawRect(primary, Offset(inset,inset),Size(size.width-2*inset,size.height-2*inset),style = Stroke(2.dp.toPx()))
                    }
                }
            }
        }
    }
}
