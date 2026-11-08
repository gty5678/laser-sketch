package com.example.boschlaser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.boschlaser.shared.annotation.*
import com.example.boschlaser.shared.annotation.Annotation as Mark

private fun Mark.labelText(): String = when (this) {
    is TextAnnotation -> text
    is DimensionAnnotation -> label
    is AngleAnnotation -> label
    is AreaAnnotation -> label
}
private fun Mark.withLabel(value: String): Mark = when (this) {
    is TextAnnotation -> copy(text = value)
    is DimensionAnnotation -> copy(label = value)
    is AngleAnnotation -> copy(label = value.trim())
    is AreaAnnotation -> copy(label = value)
}
@Composable
internal fun IosAnnotationProperties(mark: Mark, point: Int?, focusSignal: Int, update: (Mark) -> Unit,
    onDeletePoint: (Int) -> Unit, onInsertPoint: (Int) -> Unit, measurementContent: @Composable () -> Unit = {}) {
    val units = (if (mark is AreaAnnotation) listOf("unit_mm2", "unit_cm2", "unit_m2") else listOf("unit_mm", "unit_cm", "unit_m")).map(::label)
    val parsed = AnnotationQuantityLabel.parse(mark.labelText(), units)
    var text by remember(mark.id) { mutableStateOf(TextFieldValue(mark.labelText(), TextRange(mark.labelText().length))) }
    var quantity by remember(mark.id) { mutableStateOf(TextFieldValue(parsed.text, TextRange(parsed.text.length))) }
    var unit by remember(mark.id) { mutableIntStateOf(parsed.unit) }
    LaunchedEffect(mark.labelText()) {
        if (text.text != mark.labelText()) text = TextFieldValue(mark.labelText(), TextRange(mark.labelText().length))
        if (AnnotationQuantityLabel.format(quantity.text, unit, units) != mark.labelText()) {
            quantity = TextFieldValue(parsed.text, TextRange(parsed.text.length)); unit = parsed.unit
        }
    }
    @Composable fun LabelProperties() { AnnotationLabelProperties(text.takeIf { mark is TextAnnotation }, text.takeIf { mark is AngleAnnotation }, label("text_placeholder"), label("angle"),
        label("degree"), focusSignal, onText = { value ->
            if (value.text != text.text) update(mark.withLabel(value.text)); text = value
        }, onAngle = { value -> if (value.text != text.text) update(mark.withLabel(value.text)); text = value }) }
    @Composable fun Quantity() { AnnotationQuantityProperty(quantity, unit, units, label("quantity"), label("unit"),
        focusSignal, onValue = { value ->
            if (value.text != quantity.text) update(mark.withLabel(AnnotationQuantityLabel.format(value.text, unit, units)))
            quantity = value
        }, onUnit = { next ->
            AnnotationQuantityLabel.convert(quantity.text, unit, next, units)?.let { converted ->
                quantity = TextFieldValue(converted, TextRange(converted.length)); unit = next
                update(mark.withLabel(AnnotationQuantityLabel.format(converted, next, units)))
            }
        }) }
    @Composable fun StyleProperties() { AnnotationStyleProperties((mark as? TextAnnotation)?.sizePreset, (mark as? TextAnnotation)?.whiteBackground ?: false,
        (mark as? DimensionAnnotation)?.thicknessPreset,
        AnnotationStyleLabels(label("text_size"), listOf("small", "medium", "large").map(::label), label("white_background"),
            label("line_width"), listOf("thin", "medium", "thick").map(::label)),
        { if (mark is TextAnnotation) update(mark.copy(sizePreset = it)) },
        { if (mark is TextAnnotation) update(mark.copy(whiteBackground = it)) },
        { if (mark is DimensionAnnotation) update(mark.copy(thicknessPreset = it)) }) }
    val title = label(when (mark) {
        is DimensionAnnotation -> "dimension"
        is TextAnnotation -> "annotation_text"
        is AngleAnnotation -> "angle"
        is AreaAnnotation -> "annotation_area"
    })
    AnnotationDimensionMenu(title, mark.labelText(), label("finish"), mark.id to focusSignal,
        tag = if (mark is DimensionAnnotation) "annotation-dimension-menu" else "annotation-label-menu") {
        if (mark is DimensionAnnotation || mark is AreaAnnotation) Quantity() else LabelProperties()
        if (mark is DimensionAnnotation) measurementContent()
        if (mark is TextAnnotation) StyleProperties()
        if (mark is AreaAnnotation && point != null) Row {
            TextButton(onClick = { onDeletePoint(point) }) { Text(label("delete_point")) }
            TextButton(onClick = { onInsertPoint(point) }) { Text(label("insert_point")) }
        }
    }
    if (mark is DimensionAnnotation) Box(androidx.compose.ui.Modifier.width(260.dp)) { StyleProperties() }
    AnnotationDimensionMenu(label("color"), "", label("finish"), mark.id to focusSignal,
        tag = "annotation-colors-menu") {
        ColorPalette(mark.colorArgb, label("color"), remember { iosPalette() }, CustomColorLabels(label("custom_color"), label("color_hex"),
            label("color_wheel"), label("color_brightness"), label("apply_color"), label("cancel"))) { color ->
            update(when (mark) {
                is TextAnnotation -> mark.copy(colorArgb = color)
                is DimensionAnnotation -> mark.copy(colorArgb = color)
                is AngleAnnotation -> mark.copy(colorArgb = color)
                is AreaAnnotation -> mark.copy(colorArgb = color)
            })
        }
    }
}
