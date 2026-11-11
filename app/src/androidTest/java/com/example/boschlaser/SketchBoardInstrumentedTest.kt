package com.example.boschlaser

import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.boschlaser.shared.editor.Selection
import com.example.boschlaser.shared.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchBoardInstrumentedTest {
    @get:org.junit.Rule val foregroundHost: org.junit.rules.TestRule = ForegroundTestHostRule()

    @Test fun bothShapesCanBePlacedSelectedRefilledAndUndone() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = SketchView(instrumentation.targetContext)
            SketchView::class.java.getDeclaredField("scale").apply { isAccessible = true }.setFloat(view, .125f)
            fun tap(x: Float, y: Float) {
                view.sendWorldPointer(MotionEvent.ACTION_DOWN, SketchPoint(x,y))
                view.sendWorldPointer(MotionEvent.ACTION_UP, SketchPoint(x,y))
            }
            view.setMode(SketchMode.BOARD)
            view.boardFill = BoardFill.WOOD
            tap(0f,0f); tap(2000f,2000f)
            val rectangle = view.snapshotDocument().boards.values.single()
            assertEquals(BoardFill.WOOD,rectangle.fill)
            view.boardShape = BoardShape.POLYGON
            tap(3000f,0f); tap(5000f,0f); tap(4000f,2000f)
            assertEquals(1,view.snapshotDocument().boards.size)
            tap(3000f,0f)
            assertEquals(2,view.snapshotDocument().boards.size)
            view.setMode(SketchMode.SELECT)
            tap(1000f,1000f)
            assertEquals(Selection.Board(rectangle.id),view.editorState().selection)
            assertTrue(view.setBoardFill(rectangle.id,BoardFill.GRID))
            assertTrue(view.deleteSelected())
            assertEquals(1,view.snapshotDocument().boards.size)
            assertTrue(view.undo())
            assertEquals(BoardFill.GRID,view.snapshotDocument().boards.getValue(rectangle.id).fill)
            assertTrue(view.undo())
            assertEquals(rectangle,view.snapshotDocument().boards.getValue(rectangle.id))
        }
    }
    @Test fun placedPolygonCanAddDragDeleteVerticesAndUndo() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = SketchView(instrumentation.targetContext)
            SketchView::class.java.getDeclaredField("scale").apply { isAccessible = true }.setFloat(view, .125f)
            val board = Board("polygon", listOf(
                com.example.boschlaser.shared.geometry.Vec2(0.0,0.0), com.example.boschlaser.shared.geometry.Vec2(2000.0,0.0),
                com.example.boschlaser.shared.geometry.Vec2(2000.0,2000.0), com.example.boschlaser.shared.geometry.Vec2(0.0,2000.0)),
                BoardFill.GRID, BoardShape.POLYGON)
            view.restoreDocument(SketchDocument(boards = mapOf(board.id to board)))
            view.selectEntity(Selection.Board(board.id))
            view.sendWorldPointer(MotionEvent.ACTION_DOWN, SketchPoint(1000f,0f))
            assertEquals(4,view.snapshotDocument().boards.getValue(board.id).vertices.size)
            view.sendWorldPointer(MotionEvent.ACTION_MOVE, SketchPoint(1000f,-500f))
            view.sendWorldPointer(MotionEvent.ACTION_UP, SketchPoint(1000f,-500f))
            assertEquals(5,view.snapshotDocument().boards.getValue(board.id).vertices.size)
            assertEquals(-500.0,view.snapshotDocument().boards.getValue(board.id).vertices[1].y,.001)
            assertEquals(1,view.editorState().selectedBoardVertex)
            assertTrue(view.deleteSelectedBoardVertex())
            assertEquals(board,view.snapshotDocument().boards[board.id])
            assertTrue(view.undo())
            assertEquals(5,view.snapshotDocument().boards.getValue(board.id).vertices.size)
            assertTrue(view.undo())
            assertEquals(board,view.snapshotDocument().boards[board.id])
        }
    }

    @Test fun okCommitsOpenPolygonThroughHostAndUndoRestores() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = SketchView(instrumentation.targetContext)
            SketchView::class.java.getDeclaredField("scale").apply { isAccessible = true }.setFloat(view, .125f)
            view.setMode(SketchMode.BOARD)
            view.boardShape = BoardShape.POLYGON
            listOf(SketchPoint(0f,0f),SketchPoint(2000f,0f),SketchPoint(2000f,2000f)).forEach {
                view.sendWorldPointer(MotionEvent.ACTION_DOWN,it)
                view.sendWorldPointer(MotionEvent.ACTION_UP,it)
            }
            assertTrue(view.snapshotDocument().boards.isEmpty())
            assertTrue(view.finishBoardPlacement())
            view.setMode(SketchMode.SELECT)
            assertEquals(3,view.snapshotDocument().boards.values.single().vertices.size)
            assertTrue(view.undo())
            assertTrue(view.snapshotDocument().boards.isEmpty())
        }
    }

}
