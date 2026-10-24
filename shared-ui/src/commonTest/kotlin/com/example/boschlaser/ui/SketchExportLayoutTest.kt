package com.example.boschlaser.ui

import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.geometry.sketchContentBounds
import com.example.boschlaser.shared.model.*
import kotlin.test.*

class SketchExportLayoutTest {
    @Test fun emptyExportRetainsDefaultBoundsAndNinetyPixelMargin() {
        val document = SketchDocument()
        val bounds = sketchContentBounds(document)
        assertEquals(Vec2(-2500.0, -1800.0), bounds.min)
        assertEquals(Vec2(2500.0, 1800.0), bounds.max)
        val viewport = sketchExportViewport(document, Vec2(1600.0, 1200.0))
        assertEquals(1020.0 / 3600.0, viewport.scale, 1e-12)
        assertEquals(Vec2(800.0, 600.0), viewport.offset)
        assertEquals(90.0, viewport.worldToScreen(bounds.min).y, 1e-9)
    }

    @Test fun framingUsesWallCenterLineAndColumnCentersWithExistingPadding() {
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(2000.0, 0.0), 240.0, WallAlignment.LEFT)
        val column = RectColumn("column", Vec2(3000.0, 500.0), 200.0, 300.0)
        val document = SketchDocument(walls = mapOf(wall.id to wall), columns = mapOf(column.id to column))
        val bounds = sketchContentBounds(document)
        assertEquals(Vec2(-800.0, -680.0), bounds.min)
        assertEquals(Vec2(3800.0, 1300.0), bounds.max)
        val viewport = sketchExportViewport(document, Vec2(1600.0, 1200.0))
        assertEquals(Vec2(800.0, 600.0), viewport.worldToScreen(Vec2(1500.0, 310.0)))
    }

    @Test fun largePreciseCoordinatesRemainCenteredWithoutFloatNarrowing() {
        val center = Vec2(123456789.123456789, -123456789.987654321)
        val column = CircularColumn("column", center, 100.0)
        val document = SketchDocument(columns = mapOf(column.id to column))
        val viewport = sketchExportViewport(document, Vec2(1600.0, 1200.0))
        val actual = viewport.worldToScreen(center)
        assertEquals(800.0, actual.x, 1e-7)
        assertEquals(600.0, actual.y, 1e-7)
        assertEquals(.5, viewport.scale, 0.0)
        assertEquals(center, document.columns.getValue(column.id).center)
    }

    @Test fun smallExportKeepsMinimumScaleAndRejectsNonPositiveSize() {
        val document = SketchDocument()
        assertEquals(.03, sketchExportViewport(document, Vec2(100.0, 100.0)).scale, 0.0)
        assertFailsWith<IllegalArgumentException> { sketchExportViewport(document, Vec2(0.0, 100.0)) }
        assertFailsWith<IllegalArgumentException> { sketchExportViewport(document, Vec2(100.0, -1.0)) }
    }
}
