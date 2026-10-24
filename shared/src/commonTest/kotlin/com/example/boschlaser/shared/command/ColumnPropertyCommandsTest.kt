package com.example.boschlaser.shared.command

import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.*
import kotlin.test.*

class ColumnPropertyCommandsTest {
    private val rectangle = RectColumn("rect", Vec2(10.123456789, 20.987654321), 300.123456789, 400.987654321, 15.123456789)
    private val circle = CircularColumn("circle", Vec2(-10.987654321, 30.123456789), 500.987654321)
    private val initial = SketchDocument(columns = mapOf(rectangle.id to rectangle, circle.id to circle))

    @Test fun sizesPreserveCentersRotationAndOtherEntitiesWithExactUndoRedo() {
        val manager = CommandManager(initial)
        assertTrue(manager.execute(ResizeColumnCommand(rectangle.id, 50.0, 600.123456789)))
        val expected = initial.copy(columns = initial.columns +
            (rectangle.id to rectangle.copy(width = 50.0, depth = 600.123456789)))
        assertEquals(expected, manager.document)
        assertTrue(manager.undo())
        assertEquals(initial, manager.document)
        assertTrue(manager.redo())
        assertEquals(expected, manager.document)
        assertTrue(manager.execute(ResizeColumnCommand(circle.id, 700.123456789, 700.123456789)))
        assertEquals(circle.copy(diameter = 700.123456789), manager.document.columns[circle.id])
    }

    @Test fun invalidSizesFailAtomicallyAndDoNotClearRedo() {
        val manager = CommandManager(initial)
        assertTrue(manager.execute(ResizeColumnCommand(rectangle.id, 600.0, 700.0)))
        assertTrue(manager.undo())
        for ((width, depth) in listOf(49.999 to 100.0, 100.0 to 49.999,
            Double.NaN to 100.0, 100.0 to Double.POSITIVE_INFINITY, -1.0 to 100.0)) {
            assertFalse(manager.execute(ResizeColumnCommand(rectangle.id, width, depth)))
            assertEquals(initial, manager.document)
            assertTrue(manager.canRedo)
        }
        assertNull(ResizeColumnCommand("missing", 100.0, 100.0).apply(initial))
        assertTrue(manager.redo())
    }

    @Test fun rotationNormalizesButPreservesExactSizeAndCenter() {
        val manager = CommandManager(initial)
        assertTrue(manager.execute(RotateColumnCommand(rectangle.id, -30.125)))
        assertEquals(rectangle.copy(rotationDegrees = 329.875), manager.document.columns[rectangle.id])
        assertTrue(manager.undo())
        assertEquals(initial, manager.document)
        assertTrue(manager.redo())
        assertFalse(manager.execute(RotateColumnCommand(rectangle.id, 689.875)))
        assertEquals(rectangle.copy(rotationDegrees = 329.875), manager.document.columns[rectangle.id])
        for (angle in listOf(Double.NaN, Double.NEGATIVE_INFINITY)) {
            assertNull(RotateColumnCommand(rectangle.id, angle).apply(initial))
        }
        assertNull(RotateColumnCommand(circle.id, 90.0).apply(initial))
        assertNull(RotateColumnCommand("missing", 90.0).apply(initial))
    }
}
