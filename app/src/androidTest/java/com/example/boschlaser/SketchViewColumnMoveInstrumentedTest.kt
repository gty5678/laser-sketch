package com.example.boschlaser

import com.example.boschlaser.shared.editor.Selection

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchViewColumnMoveInstrumentedTest {
    @Test
    fun movingColumnSnapsCenterToWallLineOrAlignsColumnAndWallEdges() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = SketchView(context).apply { setGridSnapEnabled(false) }
        val targetWall = SketchWall(start = SketchPoint(500f, 500f), end = SketchPoint(700f, 500f))
        val column = SketchColumn(
            id = "column",
            type = SketchColumnType.RECTANGLE,
            center = SketchPoint(300f, 300f),
            width = 100f,
            depth = 100f,
        )

        view.restoreState(SketchState(walls = listOf(targetWall), columns = listOf(column)))
        view.selectEntity(Selection.Column(column.id))
        view.previewWorldDrag(column.center, SketchPoint(550f, 498f))
        assertPointEquals(SketchPoint(550f, 500f), view.snapshotState().columns.single().center)
        assertNotNull(view.editorState().magnifierTarget)

        view.restoreState(SketchState(walls = listOf(targetWall), columns = listOf(column)))
        view.selectEntity(Selection.Column(column.id))
        view.previewWorldDrag(column.center, SketchPoint(500f, 332f))
        assertPointEquals(SketchPoint(500f, 330f), view.snapshotState().columns.single().center)
        assertPointEquals(SketchPoint(500f, 330f), view.editorState().magnifierTarget!!.toSketchPoint())

        val verticalWall = SketchWall(
            start = SketchPoint(500f, 400f),
            end = SketchPoint(500f, 700f),
            thickness = 100f,
        )
        view.restoreState(SketchState(walls = listOf(verticalWall), columns = listOf(column)))
        view.selectEntity(Selection.Column(column.id))
        view.previewWorldDrag(column.center, SketchPoint(602f, 550f))
        assertPointEquals(SketchPoint(600f, 550f), view.snapshotState().columns.single().center)

        val horizontalCornerWall = SketchWall(
            start = SketchPoint(500f, 500f),
            end = SketchPoint(800f, 500f),
            thickness = 100f,
        )
        val verticalCornerWall = SketchWall(
            start = SketchPoint(500f, 500f),
            end = SketchPoint(500f, 800f),
            thickness = 100f,
        )
        listOf(
            listOf(horizontalCornerWall, verticalCornerWall),
            listOf(verticalCornerWall, horizontalCornerWall),
        ).forEach { cornerWalls ->
            view.restoreState(SketchState(walls = cornerWalls, columns = listOf(column)))
            view.selectEntity(Selection.Column(column.id))
            view.previewWorldDrag(column.center, SketchPoint(602f, 602f))
            assertPointEquals(SketchPoint(600f, 600f), view.snapshotState().columns.single().center)
        }

        view.setGridSnapEnabled(true)
        view.restoreState(SketchState(columns = listOf(column)))
        view.selectEntity(Selection.Column(column.id))
        // Exact screen quarter-pixels avoid MotionEvent's subpixel quantization at this zoom.
        // The fractional world position must still remain unsnapped with the grid enabled.
        view.previewWorldDrag(column.center, SketchPoint(312.5f, 318.75f))
        assertPointEquals(SketchPoint(312.5f, 318.75f), view.snapshotState().columns.single().center)
    }

    @Test
    fun centerControlIsDrawnWhileDragging() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = SketchView(context)
        val column = SketchColumn(
            id = "column",
            type = SketchColumnType.RECTANGLE,
            center = SketchPoint(300f, 300f),
            width = 100f,
            depth = 100f,
        )
        view.restoreState(SketchState(columns = listOf(column)))
        view.selectEntity(Selection.Column(column.id))
        view.previewWorldDrag(column.center, SketchPoint(400f, 400f))
        assertEquals(true, view.editorState().dragging)
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
        view.drawColumnFixture(canvas, column.copy(center = SketchPoint(0f, 0f)), 1f, 100f, 100f, true)
        assertEquals(Color.rgb(0, 122, 92), bitmap.getPixel(100, 100))
    }

    private fun assertPointEquals(expected: SketchPoint, actual: SketchPoint) {
        assertEquals(expected.x, actual.x, .001f)
        assertEquals(expected.y, actual.y, .001f)
    }
}
