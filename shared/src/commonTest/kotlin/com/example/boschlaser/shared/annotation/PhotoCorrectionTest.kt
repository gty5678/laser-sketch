package com.example.boschlaser.shared.annotation

import com.example.boschlaser.shared.geometry.Vec2
import kotlin.test.*

class PhotoCorrectionTest {
    @Test fun identityKeepsEveryPixel() {
        val pixels = IntArray(24) { 0xff000000.toInt() or (it * 12345) }
        val result = correctPhoto(pixels, 6, 4, PhotoCorrection())
        assertEquals(6, result.width)
        assertEquals(4, result.height)
        assertContentEquals(pixels, result.pixels)
    }

    @Test fun everySourceEdgeRemainsInsideCanvasForCombinedControls() {
        for (h in listOf(-.3, 0.0, .3)) for (v in listOf(-.3, 0.0, .3)) for (lens in listOf(-.3, 0.0, .3)) for (rotation in listOf(-45.0, 0.0, 45.0)) for (aspect in listOf(.5, 2.0)) {
            val correction = PhotoCorrection(h, v, lens, rotation)
            val mapping = PhotoCorrectionMapping(correction, aspect)
            for (i in 0..20) for (point in listOf(Vec2(i / 20.0, 0.0), Vec2(i / 20.0, 1.0), Vec2(0.0, i / 20.0), Vec2(1.0, i / 20.0))) {
                val projected = correction.forward(point, aspect)
                val u = (projected.x - mapping.left) / mapping.width
                val v = (projected.y - mapping.top) / mapping.height
                assertTrue(u in -1e-9..1.000000001 && v in -1e-9..1.000000001, "Source edge must not be cropped")
                val restored = mapping.source(u, v)
                assertEquals(point.x, restored.x, 1e-6)
                assertEquals(point.y, restored.y, 1e-6)
            }
        }
    }

    @Test fun perspectiveChangesShapeAndLeavesWhiteMargins() {
        val c = PhotoCorrection(horizontal = .2, vertical = -.1)
        val a = c.forward(Vec2(0.0, 0.0))
        val b = c.forward(Vec2(0.0, 1.0))
        assertNotEquals(a.x, b.x)
        val result = correctPhoto(IntArray(600) { 0xff00ff00.toInt() }, 30, 20, c, 60)
        assertTrue(result.width > 30 || result.height > 20)
        assertTrue(result.pixels.any { it == -1 })
        assertTrue(result.pixels.any { it == 0xff00ff00.toInt() })
    }

    @Test fun rejectsInvalidControlsAndLimitsOutputSize() {
        for (c in listOf(PhotoCorrection(horizontal = Double.NaN), PhotoCorrection(vertical = .31), PhotoCorrection(rotation = 45.1), PhotoCorrection(rotation = Double.NaN), PhotoCorrection(lens = Double.POSITIVE_INFINITY))) {
            assertFalse(c.valid())
            assertFailsWith<IllegalArgumentException> { correctPhoto(IntArray(4), 2, 2, c) }
        }
        val result = correctPhoto(IntArray(100), 10, 10, PhotoCorrection(.3, .3, .3), 8)
        assertTrue(result.width <= 8 && result.height <= 8)
    }
    @Test fun rotationUsesPhysicalAspectRatioAndDirection() {
        val right = PhotoCorrection(rotation = 45.0).forward(Vec2(1.0, .5), 2.0)
        assertEquals(kotlin.math.sqrt(.5), right.x, 1e-9)
        assertEquals(kotlin.math.sqrt(2.0), right.y, 1e-9)
        val left = PhotoCorrection(rotation = -45.0).forward(Vec2(1.0, .5), 2.0)
        assertEquals(right.x, left.x, 1e-9)
        assertEquals(-right.y, left.y, 1e-9)
        val output = correctPhoto(IntArray(200) { 0xff00ff00.toInt() }, 20, 10, PhotoCorrection(rotation = 45.0), 100)
        assertEquals(output.width, output.height)
        assertTrue(output.width > 20)
        assertTrue(output.pixels.any { it == -1 })
    }

    @Test fun cancellationStopsProcessingBetweenRows() {
        var rows = 0
        assertFailsWith<IllegalStateException> {
            correctPhoto(IntArray(600), 30, 20, PhotoCorrection(lens = .2), checkCancelled = {
                rows++
                if (rows == 3) error("cancelled")
            })
        }
        assertEquals(3, rows)
    }

}
