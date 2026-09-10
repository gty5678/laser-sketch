package com.example.boschlaser

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
        val selectedColumnId = privateField("selectedColumnId")
        val magnifierTarget = privateField("magnifierTarget")
        val moveSelection = SketchView::class.java.getDeclaredMethod(
            "moveSelection",
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            SketchPoint::class.java,
        ).apply { isAccessible = true }
        val targetWall = SketchWall(start = SketchPoint(500f, 500f), end = SketchPoint(700f, 500f))
        val column = SketchColumn(
            id = "column",
            type = SketchColumnType.RECTANGLE,
            center = SketchPoint(300f, 300f),
            width = 100f,
            depth = 100f,
        )

        view.restoreState(SketchState(walls = listOf(targetWall), columns = listOf(column)))
        selectedColumnId.set(view, column.id)
        moveSelection.invoke(view, 250f, 198f, SketchPoint(550f, 498f))
        assertPointEquals(SketchPoint(550f, 500f), view.snapshotState().columns.single().center)
        assertNotNull(magnifierTarget.get(view))

        view.restoreState(SketchState(walls = listOf(targetWall), columns = listOf(column)))
        selectedColumnId.set(view, column.id)
        moveSelection.invoke(view, 200f, 32f, SketchPoint(500f, 332f))
        assertPointEquals(SketchPoint(500f, 330f), view.snapshotState().columns.single().center)
        assertPointEquals(SketchPoint(500f, 330f), magnifierTarget.get(view) as SketchPoint)

        val verticalWall = SketchWall(
            start = SketchPoint(500f, 400f),
            end = SketchPoint(500f, 700f),
            thickness = 100f,
        )
        view.restoreState(SketchState(walls = listOf(verticalWall), columns = listOf(column)))
        selectedColumnId.set(view, column.id)
        moveSelection.invoke(view, 302f, 250f, SketchPoint(602f, 550f))
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
            selectedColumnId.set(view, column.id)
            moveSelection.invoke(view, 302f, 302f, SketchPoint(602f, 602f))
            assertPointEquals(SketchPoint(600f, 600f), view.snapshotState().columns.single().center)
        }

        view.setGridSnapEnabled(true)
        view.restoreState(SketchState(columns = listOf(column)))
        selectedColumnId.set(view, column.id)
        moveSelection.invoke(view, 13f, 17f, SketchPoint(313f, 317f))
        assertPointEquals(SketchPoint(313f, 317f), view.snapshotState().columns.single().center)
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
        privateField("dragging").setBoolean(view, true)
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
        val drawColumn = SketchView::class.java.getDeclaredMethod(
            "drawColumn",
            Canvas::class.java,
            SketchColumn::class.java,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        drawColumn.invoke(view, canvas, column.copy(center = SketchPoint(0f, 0f)), 1f, 100f, 100f, true)
        assertEquals(Color.rgb(0, 122, 92), bitmap.getPixel(100, 100))
    }

    private fun privateField(name: String) = SketchView::class.java.getDeclaredField(name).apply {
        isAccessible = true
    }

    private fun assertPointEquals(expected: SketchPoint, actual: SketchPoint) {
        assertEquals(expected.x, actual.x, .001f)
        assertEquals(expected.y, actual.y, .001f)
    }
}
