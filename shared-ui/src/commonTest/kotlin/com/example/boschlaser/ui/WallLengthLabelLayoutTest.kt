package com.example.boschlaser.ui

import com.example.boschlaser.shared.editor.Viewport
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.Wall
import com.example.boschlaser.shared.model.WallAlignment
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.test.*

class WallLengthLabelLayoutTest {
    @Test fun labelFollowsPhysicalCenterAndLeftNormalAtEveryAngleAlignmentAndZoom() {
        for (angle in 0 until 360 step 15) for (alignment in WallAlignment.entries) for (scale in listOf(.01, .1, 1.2)) {
            val direction = Vec2(cos(angle * PI / 180), sin(angle * PI / 180))
            val normal = Vec2(-direction.y, direction.x)
            val start = Vec2(100.123456789, -200.987654321)
            val length = 2000.123456789
            val wall = Wall("fixture", start, start + direction * length, 240.123456789, alignment)
            val viewport = Viewport(scale, Vec2(17.5, 29.25))
            val layout = assertNotNull(wallLengthLabelLayout(wall, viewport))
            val alignmentOffset = when (alignment) {
                WallAlignment.CENTER -> 0.0
                WallAlignment.LEFT -> wall.thickness / 2
                WallAlignment.RIGHT -> -wall.thickness / 2
            }
            val expected = viewport.worldToScreen(start + direction * (length / 2) + normal * alignmentOffset) +
                normal * (max(2.5, wall.thickness * scale / 2) * 1.9)
            assertEquals(expected.x, layout.baselineCenter.x, 1e-8)
            assertEquals(expected.y, layout.baselineCenter.y, 1e-8)
            assertEquals(length, layout.valueMm, 1e-8)
        }
    }

    @Test fun measuredValueKeepsDoublePrecisionAndDoesNotMoveTheLabel() {
        val wall = Wall("fixture", Vec2(0.0, 0.0), Vec2(2000.0, 0.0), measuredLength = 3123.123456789)
        val viewport = Viewport(.1, Vec2(10.0, 20.0))
        val measured = assertNotNull(wallLengthLabelLayout(wall, viewport))
        val geometric = assertNotNull(wallLengthLabelLayout(wall.copy(measuredLength = null), viewport))
        assertEquals(3123.123456789, measured.valueMm, 0.0)
        assertEquals(2000.0, geometric.valueMm, 0.0)
        assertEquals(measured.baselineCenter, geometric.baselineCenter)
        val reversed = assertNotNull(wallLengthLabelLayout(wall.copy(start = wall.end, end = wall.start), viewport))
        assertEquals(measured.baselineCenter.x, reversed.baselineCenter.x, 1e-9)
        assertEquals(20.0 - 12.0 * 1.9, reversed.baselineCenter.y, 1e-9)
    }

    @Test fun subpixelWallsHideLabelsAndThinWallsKeepMinimumOffset() {
        val viewport = Viewport(.01, Vec2(10.0, 20.0))
        val wall = Wall("fixture", Vec2(0.0, 0.0), Vec2(99.0, 0.0), thickness = 50.0)
        assertNull(wallLengthLabelLayout(wall, viewport))
        val boundary = assertNotNull(wallLengthLabelLayout(wall.copy(end = Vec2(100.0, 0.0)), viewport))
        assertEquals(10.5, boundary.baselineCenter.x, 1e-9)
        assertEquals(24.75, boundary.baselineCenter.y, 1e-9)
        assertNull(wallLengthLabelLayout(wall.copy(end = wall.start), viewport))
    }
}
