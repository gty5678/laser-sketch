package com.example.boschlaser

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.boschlaser.shared.measurement.Measurement
import com.example.boschlaser.shared.measurement.MeasurementRecord
import org.junit.Assert.*
import org.junit.Test

class AnnotationPageStateTest {
    private fun state() = AnnotationPageState(listOf("mm", "m", "cm"), listOf("m²", "cm²", "mm²"))
    private val dimension = AnnotationSelection.Dimension(DimensionMark(.1f, .2f, .8f, .2f, "1200 mm", -1, 1))
    private val text = AnnotationSelection.Text(TextMark(.4f, .4f, "Original", -1, 1, true))
    private val area = AnnotationSelection.Area(AreaMark(listOf(AreaPoint(0f, 0f), AreaPoint(1f, 0f), AreaPoint(0f, 1f)), "12 cm²", -1), 1)

    @Test fun selectingAnotherTypeReplacesAllDraftsAndClearingRemovesProjection() {
        val state = state()
        state.showSelection(dimension)
        assertEquals("1200", state.properties.distance?.text)
        assertEquals(TextRange(4), state.properties.distance?.selection)
        state.showSelection(area)
        assertNull(state.properties.dimension)
        assertNull(state.properties.distance)
        assertEquals("12", state.properties.area?.text)
        assertEquals(1, state.properties.areaUnit)
        assertTrue(state.properties.hasAreaPoint)
        state.showSelection(text)
        assertNull(state.properties.area)
        assertFalse(state.properties.hasAreaPoint)
        assertEquals("Original", state.properties.text?.text)
        state.showSelection(null)
        assertFalse(state.properties.visible)
        assertNull(state.properties.selection)
        assertNull(state.properties.text)
        assertEquals(4, state.properties.resetKey)
    }

    @Test fun dismissingPanelPreservesSelectionAndDraftAndNewSelectionResetsAnimation() {
        val state = state()
        state.showSelection(dimension)
        state.dismissFocus()
        val before = state.properties
        state.dismissPanel()
        assertFalse(state.properties.visible)
        assertTrue(state.properties.animateDismiss)
        assertEquals(before.selection, state.properties.selection)
        assertEquals(before.distance, state.properties.distance)
        state.showSelection(dimension)
        assertTrue(state.properties.visible)
        assertFalse(state.properties.animateDismiss)
        assertEquals(before.resetKey + 1, state.properties.resetKey)
        assertEquals(before.dismissFocus, state.properties.dismissFocus)
    }

    @Test fun rejectedDocumentChangesLeaveDraftAndStyleUnchanged() {
        val state = state()
        state.showSelection(text)
        val before = state.properties
        state.updateText(TextFieldValue("Rejected")) { false }
        state.updateTextSize(2) { false }
        state.updateTextBackground(false) { false }
        assertFalse(state.updateColor(-2) { false })
        assertEquals(before, state.properties)
        state.showSelection(dimension)
        val dimensionBefore = state.properties
        assertFalse(state.updateQuantity(false, TextFieldValue("50"), 1) { false })
        assertFalse(state.applyMeasurement(50.0) { false })
        state.updateThickness(2) { false }
        assertEquals(dimensionBefore, state.properties)
    }

    @Test fun cursorOnlyChangeDoesNotCommitAndAcceptedTextPreservesCursorAndComposition() {
        val state = state()
        state.showSelection(text)
        var commits = 0
        state.updateText(TextFieldValue("Original", TextRange(2))) { commits++; true }
        assertEquals(0, commits)
        val value = TextFieldValue("Changed", TextRange(3), TextRange(1, 4))
        state.updateText(value) { commits++; it == "Changed" }
        assertEquals(1, commits)
        assertEquals(value, state.properties.text)
        assertEquals("Changed", state.properties.textMark?.text)
    }

    @Test fun areaQuantityAndColorChangesPreserveSelectedVertex() {
        val state = state()
        state.showSelection(area)
        assertTrue(state.updateQuantity(true, TextFieldValue("25"), 0) { it == "25 m²" })
        assertTrue(state.updateColor(-2) { true })
        assertEquals(1, (state.properties.selection as AnnotationSelection.Area).selectedPointIndex)
        assertEquals("25 m²", state.properties.areaMark?.label)
        assertEquals(-2, state.properties.color)
    }

    @Test fun initialRefreshAndDeletionDoNotApplyButNewIdWithSameValueDoes() {
        val state = state()
        fun record(id: String) = MeasurementRecord(Measurement(id, 16777217.1, 1))
        val first = record("first")
        val second = record("second")
        assertNull(state.receiveMeasurements(listOf(first)))
        assertNull(state.receiveMeasurements(listOf(first)))
        assertEquals(16777217.1, state.receiveMeasurements(listOf(second, first))!!, 0.0)
        assertEquals(listOf("16777217.1 mm", "16777217.1 mm"), state.recentMeasurementLabels)
        assertNull(state.receiveMeasurements(listOf(second)))
        assertNull(state.receiveMeasurements(emptyList()))
        assertEquals("", state.defaultDimensionLabel)
    }

    @Test fun recentValuesAreBoundedAndManualReapplyStillCommitsSameValue() {
        val state = state()
        state.updateRecentMeasurements(listOf(16777217.1, 2.0, 3.0, 4.0))
        assertEquals(listOf(16777217.1, 2.0, 3.0), state.latestMeasurements)
        state.showSelection(dimension)
        var commits = 0
        repeat(2) { assertTrue(state.applyMeasurement(16777217.1) { commits++; it == "16777217.1 mm" }) }
        assertEquals(2, commits)
        assertEquals("16777217.1", state.properties.distance?.text)
        assertEquals(0, state.properties.distanceUnit)
    }

    @Test fun placementCompletionOnlySwitchesMatchingSingleModeAndPreservesSelection() {
        val state = state()
        state.showSelection(dimension)
        state.continuousPlacement = true
        assertFalse(state.completePlacement(AnnotationMode.DIMENSION))
        state.continuousPlacement = false
        assertFalse(state.completePlacement(AnnotationMode.TEXT))
        assertTrue(state.completePlacement(AnnotationMode.DIMENSION))
        assertEquals(AnnotationMode.MOVE, state.mode)
        assertEquals(dimension, state.properties.selection)
    }
}
