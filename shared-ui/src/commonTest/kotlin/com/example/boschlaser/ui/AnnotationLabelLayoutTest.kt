package com.example.boschlaser.ui

import com.example.boschlaser.shared.geometry.Vec2
import kotlin.math.PI
import kotlin.test.*

class AnnotationLabelLayoutTest {
    private val metrics = AnnotationTextMetrics(80.0, -20.0, 5.0)
    @Test fun dimensionLabelsKeepAnUprightBaselineAboveTheLineAtEveryAngle() {
        for (angle in -180..180 step 15) {
            val shape = DimensionDrawingLayout(Vec2(100.123456789, 200.987654321), Vec2(300.123456789, 200.987654321),
                angle * PI / 180, emptyList(), 3.0)
            val layout = dimensionLabelLayout(shape, metrics, 1.0)
            assertEquals(200.123456789, layout.anchor.x, 1e-9)
            assertEquals(200.987654321, layout.anchor.y, 1e-9)
            assertEquals(-18.0, layout.baselineY, 1e-9)
            assertEquals(-48.0, layout.left, 1e-9)
            assertEquals(48.0, layout.right, 1e-9)
            assertEquals(-42.0, layout.top, 1e-9)
            assertEquals(-9.0, layout.bottom, 1e-9)
            assertEquals(24.0, layout.fontSizePx, 1e-9)
            val normalized = (layout.rotationDegrees + 540) % 360 - 180
            assertTrue(normalized in -90.000000001..90.000000001)
            val expected = angle + if (angle > 90 || angle < -90) 180 else 0
            assertEquals(expected.toDouble(), layout.rotationDegrees, 1e-8)
        }
    }

    @Test fun textAreaAndAnglePaddingPreserveTheOriginalBaselineRules() {
        val anchor = Vec2(.123456789, .987654321)
        val text = annotationLabelLayout(anchor, metrics, 22.0, 8.0, whiteBackground = false)
        assertEquals(anchor, text.anchor)
        assertFalse(text.whiteBackground)
        assertEquals(0.0, text.baselineY, 0.0)
        assertEquals(-28.0, text.top, 0.0)
        assertEquals(13.0, text.bottom, 0.0)
        val area = annotationLabelLayout(anchor, metrics, 24.0, 8.0, 4.0)
        assertEquals(-24.0, area.top, 0.0)
        assertEquals(9.0, area.bottom, 0.0)
        val angle = annotationLabelLayout(anchor, metrics, 22.0, 7.0)
        assertEquals(-47.0, angle.left, 0.0)
        assertEquals(-27.0, angle.top, 0.0)
        assertEquals(12.0, angle.bottom, 0.0)
        assertEquals(7.0, angle.cornerRadiusPx, 0.0)
    }

    @Test fun sizePresetsAndScaledDimensionOffsetsKeepTheirMinimums() {
        for ((preset, minimum, scaled) in listOf(Triple(0,18.0,34.0),Triple(1,22.0,44.0),Triple(2,28.0,56.0),Triple(99,22.0,44.0))) {
            assertEquals(minimum, annotationTextSize(preset, 1.0), 0.0)
            assertEquals(scaled, annotationTextSize(preset, 2.0), 0.0)
        }
        val shape = DimensionDrawingLayout(Vec2(0.0,0.0), Vec2(100.0,0.0), 0.0, emptyList(), 3.0)
        val layout = dimensionLabelLayout(shape, metrics, 4.0)
        assertEquals(72.0, layout.fontSizePx, 0.0)
        assertEquals(-45.0, layout.baselineY, 0.0)
        assertEquals(24.0, layout.cornerRadiusPx, 0.0)
        assertEquals(-77.0, layout.top, 0.0)
    }

    @Test fun invalidFontMetricsAndLayoutInputsAreRejected() {
        assertFailsWith<IllegalArgumentException> { AnnotationTextMetrics(-1.0,-20.0,5.0) }
        assertFailsWith<IllegalArgumentException> { AnnotationTextMetrics(Double.NaN,-20.0,5.0) }
        assertFailsWith<IllegalArgumentException> { AnnotationTextMetrics(80.0,5.0,-20.0) }
        for (value in listOf(0.0,-1.0,Double.NaN,Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { annotationTextSize(1,value) }
            assertFailsWith<IllegalArgumentException> { annotationLabelLayout(Vec2(0.0,0.0),metrics,value,8.0) }
        }
        assertFailsWith<IllegalArgumentException> { annotationLabelLayout(Vec2(0.0,0.0),metrics,22.0,-1.0) }
    }
}
