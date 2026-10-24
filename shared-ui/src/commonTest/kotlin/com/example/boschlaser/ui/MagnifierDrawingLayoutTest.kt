package com.example.boschlaser.ui

import com.example.boschlaser.shared.editor.Viewport
import com.example.boschlaser.shared.geometry.Vec2
import kotlin.test.*

class MagnifierDrawingLayoutTest {
    @Test fun targetAlwaysMapsToLensCenterAcrossZoomAndDensity() {
        val target = Vec2(1234.123456789, -987.987654321)
        for (density in listOf(1.0, 2.0, 3.5)) for (scale in listOf(.025, .12, 1.2)) for (zoom in listOf(1.0, 2.6, 8.0)) {
            val layout = magnifierDrawingLayout(Viewport(scale, Vec2(150.0, -300.0)), SketchMagnifier(target, zoom, 104.0), density)
            val mapped = layout.viewport.worldToScreen(target)
            assertEquals(layout.center.x, mapped.x, 1e-9)
            assertEquals(layout.center.y, mapped.y, 1e-9)
            assertEquals(minOf(1.8, scale * zoom), layout.viewport.scale, 1e-9)
            assertEquals(88.0 * density, layout.center.x, 1e-9)
            assertEquals(104.0 + 84.0 * density, layout.center.y, 1e-9)
            assertEquals(72.0 * density, layout.radius, 1e-9)
            assertEquals(layout.radius + 4 * density, layout.shadowRadius, 1e-9)
            assertEquals(layout.center.x + 2 * density, layout.shadowCenter.x, 1e-9)
            assertEquals(layout.center.y + 3 * density, layout.shadowCenter.y, 1e-9)
        }
    }

    @Test fun screenPanDoesNotMoveTheLensOrItsTarget() {
        val magnifier = SketchMagnifier(Vec2(1200.0, 900.0), 2.6, 0.0)
        assertEquals(magnifierDrawingLayout(Viewport(.12, Vec2(0.0, 0.0)), magnifier, 2.0),
            magnifierDrawingLayout(Viewport(.12, Vec2(-1234.0, 9999.0)), magnifier, 2.0))
    }

    @Test fun invalidDisplayParametersAreRejected() {
        val viewport = Viewport(.12, Vec2(0.0, 0.0))
        val magnifier = SketchMagnifier(Vec2(0.0, 0.0), 2.6, 0.0)
        for (invalid in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { magnifierDrawingLayout(viewport, magnifier, invalid) }
            assertFailsWith<IllegalArgumentException> { magnifierDrawingLayout(viewport, magnifier.copy(zoom = invalid), 1.0) }
        }
        assertFailsWith<IllegalArgumentException> { magnifierDrawingLayout(viewport, magnifier.copy(topOffsetPx = Double.NaN), 1.0) }
    }
}
