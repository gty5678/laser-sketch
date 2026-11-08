package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.tool.PointerContext
import kotlin.test.*

class CreationSettingsTest {
    private fun place(editor: SketchEditor, point: Vec2) {
        val context = PointerContext(point, 10.0, 10.0, 10.0, 10.0, 10.0, 0.0)
        editor.pointerDown(point, context)
        editor.pointerUp(point, context)
    }

    @Test fun columnFillChangesAreAtomicAndUndoable() {
        for (column in listOf<Column>(RectColumn("column", Vec2(3.0, 4.0), 500.0, 700.0, 37.0),
            CircularColumn("column", Vec2(3.0, 4.0), 500.0))) {
            val original = SketchDocument(columns = mapOf(column.id to column))
            val editor = SketchEditor(original)
            editor.select(Selection.Column(column.id))
            assertTrue(editor.execute(com.example.boschlaser.shared.command.ChangeColumnFillCommand(column.id, ColumnFill.WHITE)))
            val expected = when (column) {
                is RectColumn -> column.copy(fill = ColumnFill.WHITE)
                is CircularColumn -> column.copy(fill = ColumnFill.WHITE)
            }
            assertEquals(original.copy(columns = mapOf(column.id to expected)), editor.document)
            assertEquals(Selection.Column(column.id), editor.state.selection)
            assertTrue(editor.undo())
            assertEquals(original, editor.document)
            assertTrue(editor.redo())
            assertEquals(expected, editor.document.columns.getValue(column.id))
            assertNull(com.example.boschlaser.shared.command.ChangeColumnFillCommand("missing", ColumnFill.WHITE).apply(original))
        }
    }

    @Test fun selectedWallFillPreservesGeometryAndHistory() {
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(5000.0, 0.0), fill = WallFill.BLACK)
        val opening = Opening("door", wall.id, 1200.0, 900.0, OpeningType.DOOR)
        val original = SketchDocument(walls = mapOf(wall.id to wall), openings = mapOf(opening.id to opening))
        val editor = SketchEditor(original)
        editor.select(Selection.Wall(wall.id))
        for (fill in WallFill.entries) {
            editor.select(Selection.Wall(wall.id))
            val before = editor.document
            val command = com.example.boschlaser.shared.command.ChangeWallFillCommand(wall.id, fill)
            assertTrue(editor.execute(command))
            assertEquals(before.copy(walls = mapOf(wall.id to wall.copy(fill = fill))), editor.document)
            assertEquals(Selection.Wall(wall.id), editor.state.selection)
            if (before != editor.document) {
                assertTrue(editor.undo())
                assertEquals(before, editor.document)
                assertTrue(editor.redo())
                assertEquals(fill, editor.document.walls.getValue(wall.id).fill)
            }
        }
        assertNull(com.example.boschlaser.shared.command.ChangeWallFillCommand("missing", WallFill.BLACK).apply(original))
    }

    @Test fun wallFillAppliesOnlyToNewWallsAndSurvivesUndoRedo() {
        val editor = SketchEditor()
        editor.setTool(ToolType.WALL)
        place(editor, Vec2(0.0, 0.0))
        for ((index, fill) in WallFill.entries.withIndex()) {
            val original = editor.document
            editor.placement = editor.placement.copy(wallFill = fill)
            assertEquals(original, editor.document)
            place(editor, Vec2((index + 1) * 2000.0, 0.0))
            assertEquals(fill, editor.document.walls.values.last().fill)
            val created = editor.document
            assertTrue(editor.undo())
            assertEquals(original, editor.document)
            assertTrue(editor.redo())
            assertEquals(created, editor.document)
        }
    }

    @Test fun columnSettingsApplyToPreviewAndNextColumnOnly() {
        for (tool in listOf(ToolType.RECT_COLUMN, ToolType.CIRCLE_COLUMN)) {
            val editor = SketchEditor()
            editor.setTool(tool)
            place(editor, Vec2(0.0, 0.0))
            val original = editor.document
            editor.placement = editor.placement.copy(columnWidth = 550.5, columnDepth = 800.25, columnRotation = 37.5, columnFill = ColumnFill.WHITE)
            assertEquals(original, editor.document)
            val point = Vec2(2000.0, 2000.0)
            val context = PointerContext(point, 10.0, 10.0, 10.0, 10.0, 10.0, 0.0)
            editor.pointerDown(point, context)
            val preview = (editor.state.preview as SketchPreview.Column).column
            editor.pointerUp(point, context)
            val next = editor.document.columns.values.last()
            assertEquals(ColumnFill.WHITE, next.fill)
            assertEquals(preview, when (next) {
                is RectColumn -> next.copy(id = preview.id)
                is CircularColumn -> next.copy(id = preview.id)
            })
            assertEquals(original.columns.values.single(), editor.document.columns.values.first())
            when (next) {
                is RectColumn -> { assertEquals(550.5, next.width); assertEquals(800.25, next.depth); assertEquals(37.5, next.rotationDegrees) }
                is CircularColumn -> assertEquals(550.5, next.diameter)
            }
            assertTrue(editor.undo())
            assertEquals(original, editor.document)
        }
    }

    @Test fun openingDefaultsOnlyAffectLaterOpenings() {
        for (tool in listOf(ToolType.DOOR, ToolType.WINDOW)) {
            val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(6000.0, 0.0))
            val editor = SketchEditor(SketchDocument(walls = mapOf(wall.id to wall)))
            editor.setTool(tool)
            place(editor, Vec2(1000.0, 0.0))
            val original = editor.document
            editor.placement = editor.placement.copy(doorWidth = 700.0, windowWidth = 800.0, doorStyle = DoorStyle.DOUBLE)
            assertEquals(original, editor.document)
            place(editor, Vec2(4000.0, 0.0))
            assertEquals(original.openings.values.single(), editor.document.openings.values.first())
            val next = editor.document.openings.values.last()
            assertEquals(if (tool == ToolType.DOOR) 700.0 else 800.0, next.width)
            if (tool == ToolType.DOOR) assertEquals(DoorStyle.DOUBLE, next.doorStyle)
            assertTrue(editor.undo())
            assertEquals(original, editor.document)
        }
    }

    @Test fun columnSettingsRejectInvalidDimensionsAndAngles() {
        assertEquals(50.0, PlacementSettings(columnWidth = 50.0, columnDepth = 50.0).columnWidth)
        for (value in listOf(49.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { PlacementSettings(columnWidth = value) }
            assertFailsWith<IllegalArgumentException> { PlacementSettings(columnDepth = value) }
        }
        assertFailsWith<IllegalArgumentException> { PlacementSettings(columnRotation = Double.NaN) }
    }
}
