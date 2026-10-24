package com.example.boschlaser.ui

import com.example.boschlaser.shared.annotation.PhotoPoint
import com.example.boschlaser.shared.geometry.Vec2
import kotlin.test.*

class PhotoViewportMappingTest {
    @Test fun mappingRoundTripsFractionalCoordinatesOnNonSquarePhotos() {
        val viewport = PhotoDrawingViewport(17.123456789, -29.987654321, 800.125, 600.875)
        for (point in listOf(PhotoPoint(0.0, 0.0), PhotoPoint(1.0, 1.0),
            PhotoPoint(.123456789, .987654321), PhotoPoint(-.125, 1.25))) {
            val restored = viewport.photo(viewport.screen(point))
            assertEquals(point.x, restored.x, 1e-12)
            assertEquals(point.y, restored.y, 1e-12)
        }
    }
    @Test fun creationClampsButDraggingAndHitTestingKeepRawOutsidePositions() {
        val viewport = PhotoDrawingViewport(100.0, 200.0, 800.0, 400.0)
        val screen = Vec2(-100.0, 800.0)
        assertEquals(PhotoPoint(-.25, 1.5), viewport.photo(screen))
        assertEquals(PhotoPoint(0.0, 1.0), viewport.photo(screen, clampToPhoto = true))
        assertEquals(PhotoPoint(1.0, 0.0), viewport.photo(Vec2(1000.0, 100.0), clampToPhoto = true))
    }
    @Test fun subtractionAndDivisionDoNotNarrowToFloat() {
        val viewport = PhotoDrawingViewport(16777216.0, -16777216.0, 1000.0, 500.0)
        val normalized = viewport.photo(Vec2(16777216.125, -16777215.75))
        assertEquals(.000125, normalized.x, 1e-15)
        assertEquals(.0005, normalized.y, 1e-15)
    }
    @Test fun emptyViewportsRemainDrawableButCannotMapInput() {
        for (viewport in listOf(PhotoDrawingViewport(0.0, 0.0, 0.0, 100.0),
            PhotoDrawingViewport(0.0, 0.0, 100.0, 0.0))) {
            assertFailsWith<IllegalArgumentException> { viewport.photo(Vec2(0.0, 0.0)) }
            assertFailsWith<IllegalArgumentException> { viewport.photo(Vec2(0.0, 0.0), clampToPhoto = true) }
        }
    }
}
