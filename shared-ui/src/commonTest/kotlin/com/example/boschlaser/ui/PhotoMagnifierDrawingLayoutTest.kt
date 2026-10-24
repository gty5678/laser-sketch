package com.example.boschlaser.ui

import com.example.boschlaser.shared.geometry.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PhotoMagnifierDrawingLayoutTest {
    @Test fun lensFitsTheCanvasAndKeepsTheOriginalDensityScaledMargin() {
        val large = photoMagnifierLayout(Vec2(600.0, 500.0), 2.0, Vec2(50.0, 150.0))!!
        assertEquals(128.0, large.radius, 1e-9)
        assertEquals(Vec2(448.0, 152.0), large.center)
        val small = photoMagnifierLayout(Vec2(100.0, 80.0), 1.0, Vec2(0.0, 0.0))!!
        assertEquals(28.0, small.radius, 1e-9)
        assertEquals(Vec2(60.0, 40.0), small.center)
        assertEquals(12.0, small.center.y - small.radius, 1e-9)
        assertEquals(12.0, 100 - small.center.x - small.radius, 1e-9)
    }

    @Test fun focusMapsToTheLensCenterAndNearbyPointsUseTheFixedZoom() {
        val focus = Vec2(51.123456789, 149.987654321)
        val lens = photoMagnifierLayout(Vec2(300.0, 250.0), 1.0, focus)!!
        assertEquals(lens.center, lens.magnify(focus))
        val point = lens.magnify(focus + Vec2(10.0, -20.0))
        assertEquals(lens.center.x + 23.5, point.x, 1e-9)
        assertEquals(lens.center.y - 47.0, point.y, 1e-9)
    }

    @Test fun photoFitStaysTopAlignedAndPreservesTheWholePhotoAspectRatio() {
        assertEquals(PhotoDrawingViewport(50.0, 0.0, 200.0, 200.0),
            fitPhotoViewport(Vec2(300.0, 200.0), Vec2(400.0, 400.0)))
        assertEquals(PhotoDrawingViewport(0.0, 0.0, 300.0, 150.0),
            fitPhotoViewport(Vec2(300.0, 250.0), Vec2(400.0, 200.0)))
        assertEquals(PhotoDrawingViewport(0.0, 0.0, 0.0, 0.0),
            fitPhotoViewport(Vec2(0.0, 250.0), Vec2(400.0, 200.0)))
        assertFailsWith<IllegalArgumentException> { fitPhotoViewport(Vec2(300.0, 250.0), Vec2(0.0, 200.0)) }
    }

    @Test fun tinyCanvasesHideTheLensAndInvalidInputsAreRejected() {
        for (size in listOf(Vec2(0.0, 100.0), Vec2(100.0, 24.0), Vec2(10.0, 10.0))) {
            assertNull(photoMagnifierLayout(size, 1.0, Vec2(0.0, 0.0)))
        }
        for (density in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> {
                photoMagnifierLayout(Vec2(300.0, 250.0), density, Vec2(0.0, 0.0))
            }
        }
        assertFailsWith<IllegalArgumentException> {
            photoMagnifierLayout(Vec2(-1.0, 250.0), 1.0, Vec2(0.0, 0.0))
        }
    }
}
