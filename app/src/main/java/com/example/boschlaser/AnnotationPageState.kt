package com.example.boschlaser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.boschlaser.shared.annotation.AnnotationQuantityLabel
import com.example.boschlaser.shared.measurement.MeasurementRecord
import com.example.boschlaser.shared.measurement.isNewestMeasurementAdded

/** Presentation projection only. The editor remains the owner of selection and document history. */
internal data class AnnotationProperties(
    val selection: AnnotationSelection? = null,
    val distance: TextFieldValue? = null,
    val distanceUnit: Int = 0,
    val text: TextFieldValue? = null,
    val angle: TextFieldValue? = null,
    val area: TextFieldValue? = null,
    val areaUnit: Int = 0,
    val visible: Boolean = false,
    val resetKey: Int = 0,
    val animateDismiss: Boolean = false,
    val dismissFocus: Int = 0,
) {
    val dimension get() = (selection as? AnnotationSelection.Dimension)?.mark
    val textMark get() = (selection as? AnnotationSelection.Text)?.mark
    val angleMark get() = (selection as? AnnotationSelection.Angle)?.mark
    val areaMark get() = (selection as? AnnotationSelection.Area)?.mark
    val hasAreaPoint get() = (selection as? AnnotationSelection.Area)?.selectedPointIndex?.let { it >= 0 } == true
    val color get() = dimension?.color ?: textMark?.color ?: angleMark?.color ?: areaMark?.color
}

internal class AnnotationPageState(private val distanceUnits: List<String>, private val areaUnits: List<String>) {
    var properties by mutableStateOf(AnnotationProperties())
        private set
    var mode by mutableStateOf(AnnotationMode.DIMENSION)
    var continuousPlacement by mutableStateOf(false)
    var latestMeasurements by mutableStateOf<List<Double>>(emptyList())
        private set
    private var observedMeasurementIds: List<String>? = null
    val recentMeasurementLabels get() = latestMeasurements.map { "${formatAnnotationMillimeters(it)} mm" }
    val defaultDimensionLabel get() = recentMeasurementLabels.firstOrNull().orEmpty()

    var waitingForMeasurement by mutableStateOf(false)
        private set
    fun toggleMeasurementWait() { waitingForMeasurement = properties.dimension != null && !waitingForMeasurement }
    fun cancelMeasurementWait() { waitingForMeasurement = false }
    fun consumeMeasurementWait(): Boolean {
        val apply = waitingForMeasurement && properties.dimension != null
        waitingForMeasurement = false
        return apply
    }
    fun convertUnit(area: Boolean, unit: Int, commit: (String) -> Boolean): Boolean {
        val value = (if (area) properties.area else properties.distance) ?: return false
        val oldUnit = if (area) properties.areaUnit else properties.distanceUnit
        val units = if (area) areaUnits else distanceUnits
        val converted = AnnotationQuantityLabel.convert(value.text, oldUnit, unit, units) ?: return false
        return updateQuantity(area, converted.asField(), unit, commit)
    }

    fun showSelection(selection: AnnotationSelection?) {
        cancelMeasurementWait()
        val distance = (selection as? AnnotationSelection.Dimension)?.mark?.label
            ?.let { AnnotationQuantityLabel.parse(it, distanceUnits) }
        val area = (selection as? AnnotationSelection.Area)?.mark?.label
            ?.let { AnnotationQuantityLabel.parse(it, areaUnits) }
        properties = AnnotationProperties(selection = selection,
            distance = distance?.text?.asField(), distanceUnit = distance?.unit ?: 0,
            text = (selection as? AnnotationSelection.Text)?.mark?.text?.asField(),
            angle = (selection as? AnnotationSelection.Angle)?.mark?.label?.asField(),
            area = area?.text?.asField(), areaUnit = area?.unit ?: 0,
            visible = selection != null, resetKey = properties.resetKey + 1,
            dismissFocus = properties.dismissFocus)
    }

    fun dismissPanel() {
        if (properties.visible) properties = properties.copy(visible = false, animateDismiss = true)
    }

    fun dismissFocus() { properties = properties.copy(dismissFocus = properties.dismissFocus + 1) }

    fun updateText(value: TextFieldValue, commit: (String) -> Boolean) {
        val mark = properties.textMark ?: return
        if (properties.text?.text != value.text && !commit(value.text)) return
        properties = properties.copy(text = value, selection = AnnotationSelection.Text(mark.copy(text = value.text)))
    }

    fun updateAngle(value: TextFieldValue, commit: (String) -> Boolean) {
        val mark = properties.angleMark ?: return
        if (properties.angle?.text != value.text && !commit(value.text)) return
        properties = properties.copy(angle = value, selection = AnnotationSelection.Angle(mark.copy(label = value.text.trim())))
    }

    fun updateQuantity(area: Boolean, value: TextFieldValue, unit: Int, commit: (String) -> Boolean): Boolean {
        val previous = if (area) properties.area else properties.distance
        val previousUnit = if (area) properties.areaUnit else properties.distanceUnit
        if (previous == null) return false
        val label = AnnotationQuantityLabel.format(value.text, unit, if (area) areaUnits else distanceUnits)
        if ((previous.text != value.text || previousUnit != unit) && !commit(label)) return false
        properties = if (area) {
            val selection = properties.selection as? AnnotationSelection.Area ?: return false
            properties.copy(area = value, areaUnit = unit, selection = selection.copy(mark = selection.mark.copy(label = label)))
        } else {
            val mark = properties.dimension ?: return false
            properties.copy(distance = value, distanceUnit = unit, selection = AnnotationSelection.Dimension(mark.copy(label = label)))
        }
        return true
    }

    fun updateColor(color: Int, commit: (Int) -> Boolean): Boolean {
        val selection = properties.selection ?: return false
        if (!commit(color)) return false
        properties = properties.copy(selection = when (selection) {
            is AnnotationSelection.Dimension -> selection.copy(mark = selection.mark.copy(color = color))
            is AnnotationSelection.Text -> selection.copy(mark = selection.mark.copy(color = color))
            is AnnotationSelection.Angle -> selection.copy(mark = selection.mark.copy(color = color))
            is AnnotationSelection.Area -> selection.copy(mark = selection.mark.copy(color = color))
        })
        return true
    }

    fun updateTextSize(size: Int, commit: (Int) -> Boolean) {
        val mark = properties.textMark ?: return
        if (commit(size)) properties = properties.copy(selection = AnnotationSelection.Text(mark.copy(textSize = size)))
    }

    fun updateTextBackground(enabled: Boolean, commit: (Boolean) -> Boolean) {
        val mark = properties.textMark ?: return
        if (commit(enabled)) properties = properties.copy(selection = AnnotationSelection.Text(mark.copy(whiteBackground = enabled)))
    }

    fun updateThickness(thickness: Int, commit: (Int) -> Boolean) {
        val mark = properties.dimension ?: return
        if (commit(thickness)) properties = properties.copy(selection = AnnotationSelection.Dimension(mark.copy(thickness = thickness)))
    }

    fun updateRecentMeasurements(values: List<Double>) { latestMeasurements = values.take(3) }

    /** Returns only a newly acquired value; refreshes/deletions must not overwrite a manual label. */
    fun receiveMeasurements(records: List<MeasurementRecord>): Double? {
        val ids = records.map { it.measurement.id }
        val added = isNewestMeasurementAdded(observedMeasurementIds, ids)
        observedMeasurementIds = ids
        updateRecentMeasurements(records.map { it.measurement.valueMm })
        return if (added) latestMeasurements.firstOrNull() else null
    }

    fun applyMeasurement(value: Double, commit: (String) -> Boolean): Boolean {
        val mark = properties.dimension ?: return false
        val text = formatAnnotationMillimeters(value)
        val label = "$text mm"
        if (!commit(label)) return false
        properties = properties.copy(distance = text.asField(), distanceUnit = 0,
            selection = AnnotationSelection.Dimension(mark.copy(label = label)))
        return true
    }

    fun completePlacement(mode: AnnotationMode): Boolean {
        if (continuousPlacement || this.mode != mode) return false
        this.mode = AnnotationMode.MOVE
        return true
    }
}

private fun String.asField() = TextFieldValue(this, TextRange(length))
