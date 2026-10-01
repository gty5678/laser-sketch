package com.example.boschlaser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class WallJointGeometryTest {
    @Test
    fun `endpoint angles from 45 through 180 degrees use extended join`() {
        assertTrue(usesExtendedWallJoin(dotForAngle(45.0)))
        assertTrue(usesExtendedWallJoin(dotForAngle(60.0)))
        assertTrue(usesExtendedWallJoin(dotForAngle(90.0)))
        assertTrue(usesExtendedWallJoin(dotForAngle(120.0)))
        assertTrue(usesExtendedWallJoin(dotForAngle(179.0)))
    }

    @Test
    fun `endpoint angles below 45 degrees use bevel join`() {
        assertFalse(usesExtendedWallJoin(dotForAngle(30.0)))
        assertFalse(usesExtendedWallJoin(dotForAngle(44.0)))
    }

    @Test
    fun `full rotation extends every joint except the acute sector around zero`() {
        for (degrees in 0 until 360) {
            val expected = degrees in 45..315
            if (expected) {
                assertTrue("Expected extension at $degrees degrees", usesExtendedWallJoin(dotForAngle(degrees.toDouble())))
            } else {
                assertFalse("Expected bevel at $degrees degrees", usesExtendedWallJoin(dotForAngle(degrees.toDouble())))
            }
        }
    }

    @Test
    fun `infinite wall lines intersect beyond and inside their drawn segments`() {
        val horizontal = SketchWall(start = SketchPoint(0f, 0f), end = SketchPoint(100f, 0f))
        val vertical = SketchWall(start = SketchPoint(150f, -100f), end = SketchPoint(150f, 50f))

        val intersection = infiniteWallLineIntersection(horizontal, vertical)!!

        assertEquals(150f, intersection.x, .001f)
        assertEquals(0f, intersection.y, .001f)
    }

    @Test
    fun `parallel wall lines have no intersection`() {
        val first = SketchWall(start = SketchPoint(0f, 0f), end = SketchPoint(100f, 0f))
        val second = SketchWall(start = SketchPoint(0f, 50f), end = SketchPoint(100f, 50f))

        assertNull(infiniteWallLineIntersection(first, second))
    }

    @Test
    fun `wall intersection uses each selected control line`() {
        val horizontal = SketchWall(
            start = SketchPoint(0f, 0f),
            end = SketchPoint(1000f, 0f),
            thickness = 200f,
            controlLine = SketchWallControlLine.OUTER,
        )
        val vertical = SketchWall(
            start = SketchPoint(500f, -500f),
            end = SketchPoint(500f, 500f),
            thickness = 300f,
            controlLine = SketchWallControlLine.INNER,
        )

        val intersection = infiniteWallLineIntersection(horizontal, vertical)!!

        assertEquals(650f, intersection.x, .001f)
        assertEquals(100f, intersection.y, .001f)
    }

    @Test
    fun `editing a side control endpoint keeps the opposite control endpoint fixed`() {
        val wall = SketchWall(
            start = SketchPoint(0f, 0f),
            end = SketchPoint(1000f, 0f),
            thickness = 200f,
            controlLine = SketchWallControlLine.OUTER,
        )
        val moved = wallFromControlLine(
            wall,
            controlStart = SketchPoint(100f, 200f),
            controlEnd = SketchPoint(1000f, 100f),
        )
        val (movedControlStart, movedControlEnd) = wallControlLinePoints(moved)

        assertEquals(100f, movedControlStart.x, .001f)
        assertEquals(200f, movedControlStart.y, .001f)
        assertEquals(1000f, movedControlEnd.x, .001f)
        assertEquals(100f, movedControlEnd.y, .001f)
    }

    @Test
    fun `oblique T wall extends far enough to cross host wall`() {
        val host = SketchWall(
            start = SketchPoint(-1000f, 0f),
            end = SketchPoint(1000f, 0f),
            thickness = 200f,
        )
        val diagonal = SketchWall(
            start = SketchPoint(-1000f, -1000f),
            end = SketchPoint(0f, 0f),
            thickness = 100f,
        )

        val outward = SketchPoint(1f, 1f)
        val upperCorner = SketchPoint(-35.35534f, 35.35534f)
        val lowerCorner = SketchPoint(35.35534f, -35.35534f)
        val upperExtension = wallCornerExtensionThroughTarget(upperCorner, outward, host)!!
        val lowerExtension = wallCornerExtensionThroughTarget(lowerCorner, outward, host)!!

        assertEquals(91.421f, upperExtension, .01f)
        assertEquals(191.421f, lowerExtension, .01f)
    }

    @Test
    fun `perpendicular T wall extension accounts for host and stem thickness`() {
        val host = SketchWall(
            start = SketchPoint(-1000f, 0f),
            end = SketchPoint(1000f, 0f),
            thickness = 200f,
        )
        val stem = SketchWall(
            start = SketchPoint(0f, -1000f),
            end = SketchPoint(0f, 0f),
            thickness = 80f,
        )

        val leftExtension = wallCornerExtensionThroughTarget(
            SketchPoint(-40f, 0f),
            SketchPoint(0f, 1f),
            host,
        )!!
        val rightExtension = wallCornerExtensionThroughTarget(
            SketchPoint(40f, 0f),
            SketchPoint(0f, 1f),
            host,
        )!!

        assertEquals(100f, leftExtension, .001f)
        assertEquals(100f, rightExtension, .001f)
    }

    private fun dotForAngle(degrees: Double): Float =
        cos(Math.toRadians(degrees)).toFloat()
}
