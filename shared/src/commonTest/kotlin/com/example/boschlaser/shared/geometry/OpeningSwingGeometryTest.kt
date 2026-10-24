package com.example.boschlaser.shared.geometry

import com.example.boschlaser.shared.model.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.*

class OpeningSwingGeometryTest {
    private fun assertPoint(expected: Vec2, actual: Vec2) {
        assertEquals(expected.x, actual.x, 1e-7)
        assertEquals(expected.y, actual.y, 1e-7)
    }

    @Test fun exactArcMatchesHitGeometryAcrossRotationsAlignmentsAndAllFlips() {
        for (angle in 0 until 360 step 15) {
            val radians = angle * PI / 180
            val origin = Vec2(123.123456789, -456.987654321)
            for (alignment in WallAlignment.entries) {
                val wall = Wall("host", origin, origin + Vec2(cos(radians), sin(radians)) * 3000.0, 240.5, alignment)
                for (flip in listOf(false, true)) for (hingeFlip in listOf(false, true)) {
                    val opening = Opening("door", wall.id, 1500.123456789, 900.987654321, OpeningType.DOOR, flip, hingeFlip)
                    val swing = assertNotNull(OpeningGeometry.doorSwing(opening, wall))
                    val hitSegments = OpeningGeometry.symbolSegments(opening, wall)
                    assertPoint(hitSegments[2].start, swing.hinge)
                    assertPoint(hitSegments[2].end, swing.leafEnd)
                    assertEquals(opening.width, swing.radius, 0.0)
                    assertEquals(90.0, abs(swing.sweepDegrees), 0.0)
                    val start = swing.startDegrees * PI / 180
                    val end = (swing.startDegrees + swing.sweepDegrees) * PI / 180
                    assertPoint(hitSegments[3].start, swing.hinge + Vec2(cos(start), sin(start)) * swing.radius)
                    assertPoint(swing.leafEnd, swing.hinge + Vec2(cos(end), sin(end)) * swing.radius)
                    for (segment in hitSegments.drop(3)) {
                        assertEquals(swing.radius, Geometry.distance(swing.hinge, segment.start), 1e-7)
                        assertEquals(swing.radius, Geometry.distance(swing.hinge, segment.end), 1e-7)
                    }
                }
            }
        }
    }

    @Test fun nearEndpointClampsAndUsesTheFullDoorWidth() {
        val wall = Wall("host", Vec2(0.0, 0.0), Vec2(3000.0, 0.0))
        val opening = Opening("door", wall.id, 5.0, 900.0, OpeningType.DOOR)
        val swing = assertNotNull(OpeningGeometry.doorSwing(opening, wall))
        assertPoint(Vec2(0.0, 0.0), swing.hinge)
        assertPoint(Vec2(0.0, 900.0), swing.leafEnd)
        assertEquals(900.0, swing.radius, 0.0)
        val mirrored = assertNotNull(OpeningGeometry.doorSwing(opening.copy(hingeFlipped = true), wall))
        assertPoint(Vec2(900.0, 0.0), mirrored.hinge)
        assertEquals(-90.0, mirrored.sweepDegrees, 0.0)
        assertNull(OpeningGeometry.doorSwing(opening.copy(type = OpeningType.WINDOW), wall))
        assertNull(OpeningGeometry.doorSwing(opening, wall.copy(end = wall.start)))
    }

    @Test fun minimumWindowThicknessOnlyAffectsDerivedFrame() {
        val wall = Wall("host", Vec2(0.0, 0.0), Vec2(3000.0, 0.0), 2.0)
        val opening = Opening("window", wall.id, 1500.0, 1000.0, OpeningType.WINDOW)
        val original = OpeningGeometry.symbolSegments(opening, wall)
        val visible = OpeningGeometry.symbolSegments(opening, wall, minimumHalfThickness = 25.0)
        assertEquals(-1.0, original[0].start.y, 0.0)
        assertEquals(-25.0, visible[0].start.y, 0.0)
        assertEquals(25.0 / 3, visible.last().start.y, 1e-10)
        assertEquals(original, OpeningGeometry.symbolSegments(opening, wall))
        assertFailsWith<IllegalArgumentException> { OpeningGeometry.symbolSegments(opening, wall, Double.NaN) }
        assertFailsWith<IllegalArgumentException> { OpeningGeometry.symbolSegments(opening, wall, -1.0) }
    }
}
