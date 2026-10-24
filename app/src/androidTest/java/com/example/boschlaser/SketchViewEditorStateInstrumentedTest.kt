package com.example.boschlaser

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.boschlaser.shared.editor.Selection
import com.example.boschlaser.shared.editor.SketchPreview
import com.example.boschlaser.shared.editor.ToolType
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchViewEditorStateInstrumentedTest {
    @get:org.junit.Rule val foregroundHost: org.junit.rules.TestRule = ForegroundTestHostRule()

    @Test fun wallDragOnlyRefreshesPropertySelectionAtGestureBoundaries() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(2000.0, 0.0))
            val view = SketchView(instrumentation.targetContext)
            view.restoreDocument(SketchDocument(walls = mapOf(wall.id to wall)))
            // A binary-exact camera scale keeps the MotionEvent fixture's world steps exact.
            SketchView::class.java.getDeclaredField("scale").apply { isAccessible = true }.setFloat(view, .125f)
            var notifications = 0
            var selection: SketchSelection? = null
            view.onSelectionChanged = { notifications++; selection = it }
            view.sendWorldPointer(android.view.MotionEvent.ACTION_DOWN, SketchPoint(1000f, 0f))
            assertEquals(1, notifications)
            for (step in 1..10) {
                view.sendWorldPointer(android.view.MotionEvent.ACTION_MOVE, SketchPoint(1000f, step * 25f))
            }
            assertEquals(1, notifications)
            assertEquals(wall, view.snapshotDocument().walls.getValue(wall.id))
            view.sendWorldPointer(android.view.MotionEvent.ACTION_UP, SketchPoint(1000f, 250f))
            assertEquals(2, notifications)
            assertEquals(250.0, view.snapshotDocument().walls.getValue(wall.id).start.y, 1e-6)
            assertEquals(250f, (selection as SketchSelection.Wall).wall.start.y, .001f)
            assertTrue(view.undo())
            assertEquals(wall, view.snapshotDocument().walls.getValue(wall.id))
        }
    }

    @Test fun hostSelectionProjectionAndCallbacksReadTheSharedEditor() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(2000.0, 0.0))
            val column = RectColumn("column", Vec2(500.0, 500.0), 400.0, 400.0)
            val opening = Opening("opening", wall.id, 500.0, 300.0, OpeningType.WINDOW)
            val document = SketchDocument(walls = mapOf(wall.id to wall), columns = mapOf(column.id to column),
                openings = mapOf(opening.id to opening))
            val view = SketchView(instrumentation.targetContext)
            var callback: SketchSelection? = null
            view.onSelectionChanged = { callback = it }
            view.restoreDocument(document)
            view.selectEntity(Selection.Wall(wall.id))
            assertEquals(Selection.Wall(wall.id), view.editorState().selection)
            assertEquals(wall.id, view.selectedWall()?.id)
            assertEquals(wall.id, (callback as SketchSelection.Wall).wall.id)
            assertNull(view.selectedColumn())
            view.selectEntity(Selection.Column(column.id))
            assertEquals(Selection.Column(column.id), view.editorState().selection)
            assertEquals(column.id, view.selectedColumn()?.id)
            assertEquals(column.id, (callback as SketchSelection.Column).column.id)
            assertNull(view.selectedWall())
            view.selectEntity(Selection.Opening(opening.id))
            assertEquals(opening.id, view.selectedOpening()?.id)
            assertEquals(opening.id, (callback as SketchSelection.Opening).opening.id)
            assertTrue(view.deleteSelected())
            assertNull(view.editorState().selection)
            assertNull(callback)
            assertTrue(view.undo())
            assertEquals(document, view.snapshotDocument())
            view.selectEntity(Selection.Wall(column.id))
            assertNull(view.editorState().selection)
            assertNull(callback)
            view.selectEntity(Selection.Wall(wall.id))
            view.setMode(SketchMode.RECT_COLUMN)
            assertEquals(ToolType.RECT_COLUMN, view.editorState().tool)
            assertNull(view.editorState().selection)
            assertNull(callback)
            assertEquals(document, view.snapshotDocument())
        }
    }

    @Test fun wallPropertiesResumeTheSharedChainWithoutFloatRounding() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val wall = Wall("wall", Vec2(100.123456789, -200.987654321), Vec2(2100.123456789, -200.987654321),
                240.123456789, WallAlignment.LEFT)
            val view = SketchView(instrumentation.targetContext)
            view.restoreDocument(SketchDocument(walls = mapOf(wall.id to wall)))
            view.setMode(SketchMode.WALL)
            view.selectEntity(Selection.Wall(wall.id))
            assertTrue(view.applySelectedWallLength(3123.123456789, moveStart = false))
            val end = view.snapshotDocument().walls.getValue(wall.id).end
            assertEquals(end, view.editorState().wallAnchor)
            assertEquals(end, (view.editorState().preview as SketchPreview.Wall).line.start)
            assertEquals(wall.start, view.snapshotDocument().walls.getValue(wall.id).start)
            assertTrue(view.updateSelectedWallControlLine(SketchWallControlLine.OUTER))
            assertEquals(end, view.editorState().wallAnchor)
            view.cancelGesture()
            assertEquals(end, view.editorState().wallAnchor)
            assertEquals(end, (view.editorState().preview as SketchPreview.Wall).line.end)
            view.setMode(SketchMode.SELECT)
            assertNull(view.editorState().wallAnchor)
            assertNull(view.editorState().preview)
        }
    }
    @Test fun screenPointerMappingKeepsFractionalWorldCoordinatesBeyondFloatPrecision() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = SketchView(instrumentation.targetContext)
            view.setGridSnapEnabled(false)
            view.setMode(SketchMode.MEASURE)
            // Platform camera fixture brings a large world coordinate into a small screen range.
            SketchView::class.java.getDeclaredField("scale").apply { isAccessible = true }.setFloat(view, .125f)
            SketchView::class.java.getDeclaredField("offsetX").apply { isAccessible = true }.setFloat(view, -2097152f)
            val event = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_UP, .015625f, 10f, 0)
            try { view.onTouchEvent(event) } finally { event.recycle() }
            val start = (view.editorState().preview as SketchPreview.Measurement).line.start
            assertEquals(16777216.125, start.x, 1e-9)
            assertEquals(80.0, start.y, 1e-9)
            assertEquals(SketchDocument(), view.snapshotDocument())
        }
    }

}
