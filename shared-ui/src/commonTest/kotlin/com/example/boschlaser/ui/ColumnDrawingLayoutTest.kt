package com.example.boschlaser.ui

import com.example.boschlaser.shared.editor.Viewport
import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.CircularColumn
import com.example.boschlaser.shared.model.RectColumn
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.test.*

class ColumnDrawingLayoutTest {
    @Test fun rotatedHandleAndStemShareLayoutAtAllScales() {
        for (angle in 0 until 360 step 15) for (scale in listOf(.01, .1, 1.2)) {
            val column = RectColumn("fixture", Vec2(100.123456789, -200.987654321), 300.123456789, 400.987654321, angle.toDouble())
            val viewport = Viewport(scale, Vec2(17.5, 29.25))
            val layout = columnDrawingLayout(column, viewport, 32.0)
            assertEquals(column.center.x * scale + 17.5, layout.center.x, 1e-9)
            assertEquals(column.center.y * scale + 29.25, layout.center.y, 1e-9)
            assertEquals(max(5.0, column.width * scale / 2), layout.halfWidth, 1e-9)
            val stem = assertNotNull(layout.stemStart)
            val handle = assertNotNull(layout.rotationHandle)
            assertEquals(layout.halfDepth, Geometry.distance(layout.center, stem), 1e-9)
            assertEquals(layout.halfDepth + 32.0, Geometry.distance(layout.center, handle), 1e-9)
            assertEquals(32.0, Geometry.distance(stem, handle), 1e-9)
            val axis = Vec2(cos(angle * PI / 180), sin(angle * PI / 180))
            val offset = handle - layout.center
            assertEquals(0.0, axis.x * offset.x + axis.y * offset.y, 1e-9)
        }
    }

    @Test fun smallShapesKeepMinimumSizeAndCirclesNeverHaveRotationControls() {
        val viewport = Viewport(.01, Vec2(10.0, 20.0))
        val rectangle = columnDrawingLayout(RectColumn("rect", Vec2(0.0, 0.0), 50.0, 50.0, 90.0), viewport, 32.0)
        assertEquals(5.0, rectangle.halfWidth, 0.0)
        assertEquals(5.0, rectangle.halfDepth, 0.0)
        assertEquals(47.0, rectangle.rotationHandle!!.x, 1e-9)
        assertEquals(20.0, rectangle.rotationHandle.y, 1e-9)
        val circle = columnDrawingLayout(CircularColumn("circle", Vec2(0.0, 0.0), 50.0), viewport, 32.0)
        assertEquals(Vec2(10.0, 20.0), circle.center)
        assertEquals(5.0, circle.halfWidth, 0.0)
        assertEquals(circle.halfWidth, circle.halfDepth, 0.0)
        assertNull(circle.rotationHandle)
        assertNull(circle.stemStart)
    }

    @Test fun invalidHandleOffsetsAreRejected() {
        val column = RectColumn("fixture", Vec2(0.0, 0.0), 300.0, 400.0)
        for (offset in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { columnDrawingLayout(column, Viewport(1.0, Vec2(0.0, 0.0)), offset) }
        }
    }
}
