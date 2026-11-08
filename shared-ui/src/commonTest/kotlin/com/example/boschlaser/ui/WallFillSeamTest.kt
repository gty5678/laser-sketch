package com.example.boschlaser.ui

import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.Wall
import kotlin.test.*

class WallFillSeamTest {
    @Test fun cornerBisectorSeparatesBothBodiesAndIsIndependentOfEndpointDirection() {
        val a = Wall("a", Vec2(0.0, 0.0), Vec2(2000.0, 0.0))
        val b = Wall("b", Vec2(0.0, 0.0), Vec2(0.0, 1000.0))
        val seam = assertNotNull(wallFillSeam(a, b))
        assertTrue((Vec2(80.0, 20.0) - seam.origin).dot(seam.normal) > 0)
        assertTrue((Vec2(20.0, 80.0) - seam.origin).dot(seam.normal) < 0)
        assertEquals(seam, wallFillSeam(a.copy(start = a.end, end = a.start), b))
        val reverse = assertNotNull(wallFillSeam(b, a))
        assertEquals(seam.origin, reverse.origin)
        assertTrue((seam.normal + reverse.normal).dot(seam.normal + reverse.normal) < 1e-10)
    }

    @Test fun straightContinuationUsesCrossSectionButTAndCrossingsHaveNoCornerSeam() {
        val a = Wall("a", Vec2(0.0, 0.0), Vec2(2000.0, 0.0))
        val continuation = Wall("b", a.start, Vec2(-1000.0, 0.0))
        assertEquals(Vec2(1.0, 0.0), assertNotNull(wallFillSeam(a, continuation)).normal)
        assertNull(wallFillSeam(a, Wall("t", Vec2(1000.0, 0.0), Vec2(1000.0, 1000.0))))
        assertNull(wallFillSeam(a, Wall("x", Vec2(1000.0, -1000.0), Vec2(1000.0, 1000.0))))
    }
}
