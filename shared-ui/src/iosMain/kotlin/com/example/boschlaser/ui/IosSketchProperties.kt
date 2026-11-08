package com.example.boschlaser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.boschlaser.shared.command.*
import com.example.boschlaser.shared.editor.*
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.model.Column as SketchColumn

internal fun field(value: Double) = decimal(value).let { TextFieldValue(it, TextRange(it.length)) }
internal val lineLabels get() = listOf("center_line", "inner_line", "outer_line").map(::label)
internal val doorLabels get() = DoorStyle.entries.associateWith { label("door_${it.name.lowercase()}") }

@Composable
internal fun IosNumberDialog(title: String, initial: List<Pair<String, Double>>, apply: (List<Double>) -> Boolean, dismiss: () -> Unit) {
    val values = remember { initial.map { field(it.second) }.toMutableStateList() }
    var invalid by remember { mutableStateOf(false) }
    fun submit() {
        val parsed = values.map { it.text.toDoubleOrNull() }
        invalid = parsed.any { it == null || !it.isFinite() } || !apply(parsed.filterNotNull())
        if (!invalid) dismiss()
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column {
            initial.forEachIndexed { index, item -> OutlinedTextField(values[index], { values[index] = it; invalid = false },
                label = { Text(label(item.first)) }, singleLine = true, isError = invalid)
                if (item.first == "rotation") ColumnAnglePresets(values[index].text) { selected ->
                    values[index] = TextFieldValue(selected, TextRange(0, selected.length))
                    submit()
                }
                if (item.first == "wall_thickness") WallThicknessPresets(values[index].text) { selected ->
                    values[index] = TextFieldValue(selected, TextRange(0, selected.length))
                    submit()
                }
            }
            val widthIndex = initial.indexOfFirst { it.first == "width" || it.first == "diameter" }
            val depthIndex = initial.indexOfFirst { it.first == "depth" }
            if (widthIndex >= 0) {
                val select: (String) -> Unit = { selected ->
                    val draft = TextFieldValue(selected, TextRange(0, selected.length))
                    values[widthIndex] = draft
                    if (depthIndex >= 0) values[depthIndex] = draft
                    submit()
                }
                if (depthIndex >= 0 || initial[widthIndex].first == "diameter") {
                    ColumnSizePresets(values[widthIndex].text, values.getOrNull(depthIndex)?.text, select)
                } else OpeningWidthPresets(values[widthIndex].text, select)
            }
            if (invalid) Text(label("invalid_value"), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = { submit() }) { Text(label("apply")) } }, dismissButton = { TextButton(onClick = dismiss) { Text(label("cancel")) } })
}

@Composable
internal fun IosSketchProperties(editor: SketchEditor, accept: (SketchCommand) -> Boolean, update: () -> Unit) {
    val selection = editor.state.selection
    val document = editor.document
    Column(Modifier.heightIn(max = 330.dp).verticalScroll(rememberScrollState())) {
        when (selection) {
            is Selection.Wall -> document.walls[selection.id]?.let { wall ->
                var thicknessVisible by remember(wall.id) { mutableStateOf(false) }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    TextButton(onClick = { thicknessVisible = true }) {
                        Text("${label("wall_thickness")} ${decimal(wall.thickness)}")
                    }
                    WallFillPicker(wall.fill, listOf(label("wall_fill_blank"), label("wall_fill_hatch"),
                        label("wall_fill_black"))) { accept(ChangeWallFillCommand(wall.id, it)) }
                }
                if (thicknessVisible) IosNumberDialog(label("wall_thickness"), listOf("wall_thickness" to wall.thickness),
                    { accept(ChangeWallThicknessCommand(wall.id, it[0])) }, { thicknessVisible = false })
            }
            is Selection.Column -> document.columns[selection.id]?.let { column ->
                val width = when (column) { is RectColumn -> column.width; is CircularColumn -> column.diameter }
                val rectangle = column as? RectColumn
                var property by remember(column.id) { mutableStateOf<Int?>(null) }
                ColumnPropertyTools(column.fill, listOf(label("column_fill_white"), label("column_fill_black")),
                    listOfNotNull("${label("column_size")} ${decimal(width)} × ${decimal(rectangle?.depth ?: width)}",
                        rectangle?.let { "${label("rotation")} ${decimal(it.rotationDegrees)}°" }),
                    { accept(ChangeColumnFillCommand(column.id, it)) }, { property = it })
                property?.let { editing ->
                    val initial = if (editing == 0) {
                        if (rectangle == null) listOf("diameter" to width)
                        else listOf("width" to width, "depth" to rectangle.depth)
                    } else listOf("rotation" to (rectangle?.rotationDegrees ?: 0.0))
                    IosNumberDialog(label(if (editing == 0) "column_size" else "rotation"), initial,
                        { values -> accept(if (editing == 0) ResizeColumnCommand(column.id, values[0], values.getOrElse(1) { values[0] })
                            else RotateColumnCommand(column.id, values[0])) }, { property = null })
                }
            }
            is Selection.Opening -> document.openings[selection.id]?.let { opening ->
                if (opening.type != OpeningType.DOOR) IosOpeningProperties(opening, accept)
            }
            else -> Unit
        }
    }
}

@Composable
internal fun IosOpeningProperties(opening: Opening, accept: (SketchCommand) -> Boolean) {
    var widthVisible by remember(opening.id) { mutableStateOf(false) }
    TextButton(onClick = { widthVisible = true }) { Text("${label("width")} ${decimal(opening.width)}") }
    if (widthVisible) IosNumberDialog(label("width"), listOf("width" to opening.width),
        { accept(ChangeOpeningPropertiesCommand(opening.id, it[0])) }, { widthVisible = false })
    if (opening.type == OpeningType.DOOR) {
        var styleVisible by remember(opening.id) { mutableStateOf(false) }
        TextButton(onClick = { styleVisible = true }) { Text(doorLabels.getValue(opening.doorStyle)) }
        if (styleVisible) AlertDialog(onDismissRequest = { styleVisible = false }, confirmButton = {},
            dismissButton = { TextButton(onClick = { styleVisible = false }) { Text(label("cancel")) } },
            text = { DoorStylePicker(opening.doorStyle, doorLabels) {
                if (accept(ChangeDoorStyleCommand(opening.id, it))) styleVisible = false
            } })
    }
}
