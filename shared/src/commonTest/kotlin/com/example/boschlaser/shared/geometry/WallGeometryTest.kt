package com.example.boschlaser.shared.geometry

import com.example.boschlaser.shared.model.Opening
import com.example.boschlaser.shared.model.OpeningType
import com.example.boschlaser.shared.model.Wall
import com.example.boschlaser.shared.model.WallAlignment
import com.example.boschlaser.shared.model.WallAnchor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WallGeometryTest {
    private val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(2000.0, 0.0), 200.0)
    private val opening = Opening("door", wall.id, 700.0, 900.0, OpeningType.DOOR)

    @Test
    fun allAlignmentsKeepReferenceLineAndDeriveBody() {
        for ((alignment, centerY) in listOf(WallAlignment.CENTER to 0.0,
            WallAlignment.LEFT to 100.0, WallAlignment.RIGHT to -100.0)) {
            val shape = WallGeometry.build(wall.copy(alignment = alignment))
            assertEquals(Vec2(0.0, 0.0), shape.referenceLine.start)
            assertEquals(centerY, shape.centerLine.start.y, 1e-6)
            assertEquals(200.0, Geometry.distance(shape.leftEdge.start, shape.rightEdge.start), 1e-6)
            assertEquals(4, shape.polygon.size)
        }
    }
    @Test
    fun diagonalWallShapeAndFixedReferenceEndpointSurviveLengthEdit() {
        val diagonal = wall.copy(end = Vec2(1200.0, 1600.0), alignment = WallAlignment.LEFT)
        val updated = WallGeometry.setLength(diagonal, 3000.0, WallAnchor.START)
        assertEquals(diagonal.start, updated.start)
        assertEquals(Vec2(1800.0, 2400.0), updated.end)
        val shape = WallGeometry.build(updated)
        assertEquals(200.0, Geometry.distance(shape.leftEdge.start, shape.rightEdge.start), 1e-6)
    }
    @Test
    fun obtuseAnglesAreNeverFoldedToAcuteDuringFullRotation() {
        for (degrees in 0 until 360) {
            assertEquals(degrees in 45..315, WallGeometry.usesExtendedJoin(cos(degrees * PI / 180.0)), "$degrees degrees")
        }
        assertEquals(105.0, WallGeometry.endpointAngle(cos(105.0 * PI / 180.0)) * 180.0 / PI, 1e-6)
    }
    @Test
    fun obliqueTCornersReachFarFaceIndependently() {
        val host = wall.copy(start = Vec2(-1000.0, 0.0), end = Vec2(1000.0, 0.0))
        assertEquals(91.42136, WallGeometry.cornerExtensionThroughTarget(
            Vec2(-35.35534, 35.35534), Vec2(1.0, 1.0), host)!!, 0.001)
        assertEquals(191.42136, WallGeometry.cornerExtensionThroughTarget(
            Vec2(35.35534, -35.35534), Vec2(1.0, 1.0), host)!!, 0.001)
        assertEquals(191.42136, WallGeometry.cornerExtensionThroughTarget(
            Vec2(35.35534, -35.35534), Geometry.normalize(Vec2(1.0, 1.0)), host)!!, 0.001)
        assertNull(WallGeometry.cornerExtensionThroughTarget(Vec2(0.0, 0.0), Vec2(1.0, 0.0), host))
    }
    @Test
    fun openingKeepsStartDistanceWhenEndMovesOrRotates() {
        val updated = wall.copy(end = Vec2(0.0, 3000.0))
        val hosted = OpeningGeometry.rehost(opening, wall, updated, WallAnchor.START)!!
        assertEquals(700.0, hosted.offset)
        assertEquals(Vec2(0.0, 700.0), OpeningGeometry.center(hosted, updated))
        assertEquals(900.0, hosted.width)
    }
    @Test
    fun openingKeepsEndDistanceWhenStartMoves() {
        val updated = WallGeometry.setLength(wall, 3000.0, WallAnchor.END)
        val hosted = OpeningGeometry.rehost(opening, wall, updated, WallAnchor.END)!!
        assertEquals(1700.0, hosted.offset)
        assertEquals(1300.0, Geometry.length(WallGeometry.referenceLine(updated)) - hosted.offset, 1e-6)
    }
    @Test
    fun shorteningCannotMoveDeleteOrSqueezeOpening() {
        assertNull(OpeningGeometry.rehost(opening, wall, wall.copy(end = Vec2(1000.0, 0.0)), WallAnchor.START))
        assertFalse(OpeningGeometry.fits(opening.copy(hostWallId = "other"), wall))
        assertTrue(OpeningGeometry.fits(opening, wall))
        assertEquals(700.0, opening.offset)
    }
}
