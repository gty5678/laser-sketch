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

    private fun dotForAngle(degrees: Double): Float =
        cos(Math.toRadians(degrees)).toFloat()
}
