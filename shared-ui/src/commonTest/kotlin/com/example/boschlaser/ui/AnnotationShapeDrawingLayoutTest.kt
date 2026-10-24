package com.example.boschlaser.ui

import com.example.boschlaser.shared.annotation.*
import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.Vec2
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.*

class AnnotationShapeDrawingLayoutTest {
    private val viewport = PhotoDrawingViewport(17.5, 29.25, 800.0, 600.0)
    @Test fun dimensionEndLinesAndTicksKeepTheirOrientationAndSizes() {
        for (angle in 0 until 360 step 15) for (scale in listOf(1.0, 2.0, 4.0)) {
            val radians = angle * PI / 180
            val mark = DimensionAnnotation("fixture", PhotoPoint(.5, .5),
                PhotoPoint(.5 + cos(radians) * 200 / 800, .5 + sin(radians) * 200 / 600), "", -65536)
            val layout = dimensionDrawingLayout(mark, viewport, scale)
            assertEquals(5, layout.segments.size)
            val base = layout.end - layout.start
            assertEquals(200.0, Geometry.length(layout.segments[0]), 1e-9)
            val endLine = layout.segments[1].end - layout.segments[1].start
            val tick = layout.segments[3].end - layout.segments[3].start
            assertEquals(0.0, base.x * endLine.x + base.y * endLine.y, 1e-8)
            assertEquals(kotlin.math.max(42.0, 32.0 * scale), Geometry.length(layout.segments[1]), 1e-9)
            assertEquals(kotlin.math.max(24.0, 18.0 * scale), Geometry.length(layout.segments[3]), 1e-9)
            val dot = (base.x * tick.x + base.y * tick.y) / (200 * Geometry.distance(Vec2(0.0, 0.0), tick))
            assertEquals(cos(PI / 4), dot, 1e-9)
        }
    }

    @Test fun thicknessPresetsAndCoincidentPointsRetainTheOriginalDisplayRules() {
        val mark = DimensionAnnotation("fixture", PhotoPoint(.123456789, .987654321), PhotoPoint(.123456789, .987654321), "", -65536)
        for ((preset, minimum, scaled) in listOf(Triple(0, 2.0, 5.6), Triple(1, 3.0, 9.6), Triple(2, 5.0, 16.0), Triple(99, 3.0, 9.6))) {
            assertEquals(minimum, dimensionDrawingLayout(mark.copy(thicknessPreset = preset), viewport, 1.0).lineWidthPx, 1e-9)
            assertEquals(scaled, dimensionDrawingLayout(mark.copy(thicknessPreset = preset), viewport, 4.0).lineWidthPx, 1e-9)
        }
        val layout = dimensionDrawingLayout(mark, viewport, 1.0)
        assertEquals(17.5 + .123456789 * 800, layout.start.x, 1e-12)
        assertEquals(29.25 + .987654321 * 600, layout.start.y, 1e-12)
        assertEquals(layout.start, layout.end)
        assertEquals(0.0, layout.angleRadians, 0.0)
        assertTrue(layout.segments.all { it.start.x.isFinite() && it.end.y.isFinite() })
    }

    @Test fun areaLabelUsesTheVertexMeanAndLegacyOutsidePhotoPointsRemainUnclamped() {
        val mark = AreaAnnotation("fixture", listOf(PhotoPoint(-.123456789, .1), PhotoPoint(.8, .2), PhotoPoint(.4, 1.2)), "", -65536)
        val layout = areaDrawingLayout(mark, viewport)
        assertEquals(17.5 - .123456789 * 800, layout.points[0].x, 1e-12)
        assertEquals(29.25 + 1.2 * 600, layout.points[2].y, 1e-12)
        assertEquals(layout.points.sumOf { it.x } / 3, layout.labelBaselineCenter.x, 1e-12)
        assertEquals(layout.points.sumOf { it.y } / 3, layout.labelBaselineCenter.y, 1e-12)
        assertEquals(3, mark.points.size)
    }

    @Test fun invalidViewportsAndDrawingScalesAreRejected() {
        assertFailsWith<IllegalArgumentException> { PhotoDrawingViewport(0.0, 0.0, -1.0, 100.0) }
        assertFailsWith<IllegalArgumentException> { PhotoDrawingViewport(Double.NaN, 0.0, 100.0, 100.0) }
        val mark = DimensionAnnotation("fixture", PhotoPoint(0.0, 0.0), PhotoPoint(1.0, 1.0), "", -65536)
        for (scale in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { dimensionDrawingLayout(mark, viewport, scale) }
        }
    }
}
