package com.example.boschlaser.shared.geometry

import com.example.boschlaser.shared.model.Opening
import com.example.boschlaser.shared.model.OpeningType
import com.example.boschlaser.shared.model.Wall
import com.example.boschlaser.shared.model.WallAlignment
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WallNetworkGeometryTest {
    @Test
    fun obliqueTBodyCornersEndExactlyOnHostFarFace() {
        val host = Wall("host", Vec2(-1000.0, 0.0), Vec2(1000.0, 0.0), 200.0)
        val stem = Wall("stem", Vec2(-1000.0, -1000.0), Vec2(0.0, 0.0), 100.0)
        val polygon = WallNetworkGeometry.bodyPolygon(stem, listOf(host, stem))
        assertEquals(100.0, polygon[1].y, .001)
        assertEquals(100.0, polygon[2].y, .001)
        assertTrue(polygon[1].x < polygon[2].x)
    }
    @Test
    fun obtuseJoinExtendsAndAcuteJoinRemainsBounded() {
        val first = Wall("first", Vec2(0.0, 0.0), Vec2(2000.0, 0.0), 200.0)
        fun second(degrees: Double) = Wall("second", Vec2(0.0, 0.0),
            Vec2(cos(degrees * PI / 180.0), sin(degrees * PI / 180.0)) * 2000.0, 200.0)
        val acute = WallNetworkGeometry.jointPolygons(listOf(first, second(30.0))).single()
        assertTrue(acute.all { Geometry.distance(it, first.start) <= 100.001 })
        val obtuse = WallNetworkGeometry.jointPolygons(listOf(first, second(105.0))).single()
        assertTrue(obtuse.any { Geometry.distance(it, first.start) > 100.1 })
    }
    @Test
    fun canonicalBodyAndJointHullAreIndependentOfStorageDirectionAndWallOrder() {
        val first = Wall("first", Vec2(0.0, 0.0), Vec2(180.0, 0.0), 80.0)
        val second = Wall("second", Vec2(0.0, 0.0), Vec2(-46.58743, 173.86665), 120.0)
        val reversed = first.copy(start = first.end, end = first.start)
        assertEquals(WallNetworkGeometry.bodyPolygon(first, listOf(first, second)),
            WallNetworkGeometry.bodyPolygon(reversed, listOf(reversed, second)))
        val expected = WallNetworkGeometry.jointPolygons(listOf(first, second)).single()
        val swapped = WallNetworkGeometry.jointPolygons(listOf(second, reversed)).single()
        assertEquals(expected.size, swapped.size)
        expected.forEach { point -> assertTrue(swapped.any { Geometry.distance(point, it) < .0001 }) }
    }

    @Test
    fun teeUsesTwoStemCornersAndKeepsTheOpposedRunContinuous() {
        val east = Wall("east", Vec2(0.0, 0.0), Vec2(1000.0, 0.0), 100.0)
        val west = Wall("west", Vec2(0.0, 0.0), Vec2(-1000.0, 0.0), 100.0)
        val stem = Wall("stem", Vec2(0.0, 0.0), Vec2(0.0, 1000.0), 100.0)
        val joint = WallNetworkGeometry.jointPolygons(listOf(east, west, stem))
        assertEquals(2, joint.size)
        assertEquals(joint.map(::canonicalPolygon).toSet(),
            WallNetworkGeometry.jointPolygons(listOf(stem.copy(start = stem.end, end = stem.start),
                west, east)).map(::canonicalPolygon).toSet())
    }

    @Test
    fun yAndFourWayNodesOnlyJoinAdjacentRaysRegardlessOfWallOrder() {
        fun ray(id: String, degrees: Double) = Wall(id, Vec2(0.0, 0.0),
            Vec2(cos(degrees * PI / 180.0), sin(degrees * PI / 180.0)) * 1000.0, 100.0)
        val y = listOf(ray("a", 0.0), ray("b", 120.0), ray("c", 240.0))
        val cross = listOf(ray("e", 0.0), ray("n", 90.0), ray("w", 180.0), ray("s", 270.0))
        for ((walls, expectedCount) in listOf(y to 3, cross to 4)) {
            val expected = WallNetworkGeometry.jointPolygons(walls).map(::canonicalPolygon).toSet()
            assertEquals(expectedCount, expected.size)
            val reordered = walls.reversed().mapIndexed { index, wall ->
                if (index % 2 == 0) wall.copy(start = wall.end, end = wall.start) else wall
            }
            assertEquals(expected, WallNetworkGeometry.jointPolygons(reordered).map(::canonicalPolygon).toSet())
        }
    }

    @Test
    fun nearlyStraightRunRetainsItsBackCorner() {
        fun ray(id: String, degrees: Double) = Wall(id, Vec2(0.0, 0.0),
            Vec2(cos(degrees * PI / 180.0), sin(degrees * PI / 180.0)) * 1000.0, 100.0)
        val walls = listOf(ray("east", 0.0), ray("west", 175.0), ray("stem", 265.0))
        val expected = WallNetworkGeometry.jointPolygons(walls).map(::canonicalPolygon).toSet()
        assertEquals(3, expected.size)
        assertEquals(expected, WallNetworkGeometry.jointPolygons(walls.reversed()).map(::canonicalPolygon).toSet())
    }

    @Test
    fun reflexSectorClosesTheSlitBehindANearlyStraightRun() {
        val angle = 175.0 * PI / 180.0
        val origin = Vec2(0.0, 0.0)
        val walls = listOf(
            Wall("east", origin, Vec2(1000.0, 0.0), 240.0),
            Wall("west", origin, Vec2(cos(angle), sin(angle)) * 1000.0, 240.0),
            Wall("stem", origin, Vec2(0.0, 1000.0), 60.0),
        )
        val sample = Vec2(-4.0, -70.0)
        val joints = WallNetworkGeometry.jointPolygons(walls)
        assertTrue(joints.any { polygon -> polygon.indices.all { index ->
            val next = polygon[(index + 1) % polygon.size]
            (next - polygon[index]).cross(sample - polygon[index]) >= -.0001
        } })
        assertEquals(joints.map(::canonicalPolygon).toSet(),
            WallNetworkGeometry.jointPolygons(walls.reversed().map { it.copy(start = it.end, end = it.start) })
                .map(::canonicalPolygon).toSet())
    }

    @Test
    fun sameSideStraightRunWithObliqueBranchKeepsEachCornerInItsOwnSector() {
        for (degrees in listOf(45.0, 90.0, 135.0)) {
            val direction = Vec2(cos(degrees * PI / 180.0), sin(degrees * PI / 180.0))
            val walls = listOf(
                Wall("east", Vec2(0.0, 0.0), Vec2(1000.0, 0.0), 200.0, WallAlignment.LEFT),
                Wall("west", Vec2(0.0, 0.0), Vec2(-1000.0, 0.0), 100.0, WallAlignment.RIGHT),
                Wall("stem", Vec2(0.0, 0.0), direction * 1000.0, 80.0),
            )
            val joints = WallNetworkGeometry.jointPolygons(walls)
            assertEquals(2, joints.size)
            joints.forEach { polygon ->
                assertTrue(polygon.all { it.y >= -.0001 }, "Corner crossed the straight run at $degrees degrees")
                assertTrue(polygon.all { direction.cross(it) <= .0001 } ||
                    polygon.all { direction.cross(it) >= -.0001 },
                    "Corner crossed the branch at $degrees degrees")
            }
            if (degrees == 45.0) {
                val outside = Vec2(-20.0, -10.0)
                val polygons = walls.map { WallNetworkGeometry.bodyPolygon(it, walls) } + joints
                assertTrue(polygons.none { polygon ->
                    polygon.indices.all { index ->
                        val next = polygon[(index + 1) % polygon.size]
                        (next - polygon[index]).cross(outside - polygon[index]) >= -.0001
                    }
                }, "The network filled a point behind the branch and below the straight run")
            }
            val reversed = walls.reversed().map { wall ->
                wall.copy(start = wall.end, end = wall.start, alignment = when (wall.alignment) {
                    WallAlignment.LEFT -> WallAlignment.RIGHT
                    WallAlignment.RIGHT -> WallAlignment.LEFT
                    WallAlignment.CENTER -> WallAlignment.CENTER
                })
            }
            assertEquals(joints.map(::canonicalPolygon).toSet(),
                WallNetworkGeometry.jointPolygons(reversed).map(::canonicalPolygon).toSet())
        }
    }

    private fun canonicalPolygon(polygon: List<Vec2>): Set<Pair<Int, Int>> = polygon.map {
        (it.x * 1000.0).roundToInt() to (it.y * 1000.0).roundToInt()
    }.toSet()

    @Test
    fun branchBodyTerminatesOnSameSideStraightRunFaceInsteadOfKeepingItsSquareCap() {
        for (degrees in listOf(30.0, 45.0, 90.0, 135.0, 150.0)) {
            for (alignment in WallAlignment.entries) {
                val origin = Vec2(0.0, 0.0)
                val direction = Vec2(cos(degrees * PI / 180.0), sin(degrees * PI / 180.0))
                val east = Wall("east", origin, Vec2(1000.0, 0.0), 200.0, WallAlignment.LEFT)
                val west = Wall("west", origin, Vec2(-1000.0, 0.0), 200.0, WallAlignment.RIGHT)
                val stem = Wall("stem", origin, direction * 1000.0, 80.0, alignment)
                val walls = listOf(east, west, stem)
                val body = WallNetworkGeometry.bodyPolygon(stem, walls)
                val cap = body.sortedBy { it.dot(direction) }.take(2)
                cap.forEach { assertEquals(0.0, it.y, .0001, "$degrees $alignment") }
                val reversed = stem.copy(start = stem.end, end = stem.start, alignment = when (alignment) {
                    WallAlignment.LEFT -> WallAlignment.RIGHT
                    WallAlignment.RIGHT -> WallAlignment.LEFT
                    WallAlignment.CENTER -> WallAlignment.CENTER
                })
                assertEquals(canonicalPolygon(body), canonicalPolygon(
                    WallNetworkGeometry.bodyPolygon(reversed, listOf(west, reversed, east))))
            }
        }
    }

    @Test
    fun branchCornerCanTerminateOnTheThicknessStepBetweenUnequalCenteredHosts() {
        val origin = Vec2(0.0, 0.0)
        val east = Wall("east", origin, Vec2(1000.0, 0.0), 200.0)
        val west = Wall("west", origin, Vec2(-1000.0, 0.0), 100.0)
        val stem = Wall("stem", origin, Vec2(1000.0, 1000.0), 80.0)
        val polygon = WallNetworkGeometry.bodyPolygon(stem, listOf(east, west, stem))
        assertTrue(polygon.any { abs(it.x) < .0001 && abs(it.y + 80.0 / sqrt(2.0)) < .0001 })
        // The other edge first leaves the east host at its upper thickness step.
        // Extending it through to the west far face would fill the gap above that host.
        assertTrue(polygon.any { abs(it.x) < .0001 && abs(it.y - 80.0 / sqrt(2.0)) < .0001 })
    }

    @Test
    fun openingCutoutFollowsHostBodyAndDoesNotPersistGeometry() {
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(3000.0, 0.0), 200.0)
        val door = Opening("door", wall.id, 1500.0, 900.0, OpeningType.DOOR)
        assertEquals(listOf(Vec2(1050.0, -100.0), Vec2(1950.0, -100.0), Vec2(1950.0, 100.0), Vec2(1050.0, 100.0)),
            OpeningGeometry.cutoutPolygon(door, wall))
    }
}
