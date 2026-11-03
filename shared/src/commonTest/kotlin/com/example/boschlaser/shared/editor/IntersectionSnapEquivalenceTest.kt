package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.geometry.WallGeometry
import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.model.Wall
import com.example.boschlaser.shared.model.WallAlignment
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class IntersectionSnapEquivalenceTest {
    private val engine = SnapEngine()

    /** Original all-pairs decision, including midpoint ordering and first-wins distance ties. */
    private fun exhaustive(document: SketchDocument, input: Vec2, tolerance: Double, midpoints: Boolean): SnapResult {
        val lines = document.walls.values.map { WallGeometry.referenceLine(it) }
        val candidates = mutableListOf<SnapResult>()
        if (midpoints) document.walls.values.zip(lines).forEach { (wall, line) ->
            candidates += SnapResult((line.start + line.end) * .5, SnapType.MIDPOINT, wall.id)
        }
        for (first in lines.indices) for (second in first + 1 until lines.size) {
            Geometry.segmentIntersection(lines[first], lines[second])?.let {
                candidates += SnapResult(it, SnapType.INTERSECTION)
            }
        }
        return candidates.filter { Geometry.distance(it.point, input) <= tolerance }
            .minByOrNull { Geometry.distance(it.point, input) } ?: SnapResult(input, SnapType.NONE)
    }

    @Test fun sparseAndDenseRotatedWallsMatchAllPairsAcrossTolerancesAndMidpoints() {
        val random = Random(8031)
        for (spread in listOf(100.0, 10000.0)) {
            val document = SketchDocument(walls = (0 until 80).associate { index ->
                val start = Vec2(random.nextDouble(-spread, spread), random.nextDouble(-spread, spread))
                val end = start + Vec2(random.nextDouble(-1000.0, 1000.0), random.nextDouble(-1000.0, 1000.0))
                val wall = Wall("wall-$index", start, end, alignment = WallAlignment.entries[index % 3])
                wall.id to wall
            })
            repeat(80) {
                val input = Vec2(random.nextDouble(-spread, spread), random.nextDouble(-spread, spread))
                for (tolerance in listOf(0.0, 20.0, 250.0)) for (midpoints in listOf(false, true)) {
                    assertEquals(exhaustive(document, input, tolerance, midpoints), engine.snap(document, input,
                        SnapContext(tolerance, endpoints = false, controlLines = false, intersections = true, midpoints = midpoints)))
                }
            }
        }
    }

    @Test fun endpointEpsilonBoundaryAndEqualDistanceTiesRetainOriginalResult() {
        fun wall(id: String, start: Vec2, end: Vec2) = Wall(id, start, end)
        val scenes = listOf(
            listOf(wall("a", Vec2(0.0, 0.0), Vec2(1e6, 0.0)),
                wall("b", Vec2(1e6 + .0005, -100.0), Vec2(1e6 + .0005, 100.0))) to Vec2(1e6 + .0005, 0.0),
            listOf(wall("a", Vec2(-100.0, 0.0), Vec2(100.0, 0.0)),
                wall("b", Vec2(20.0, -100.0), Vec2(20.0, 100.0)),
                wall("c", Vec2(-20.0, -100.0), Vec2(-20.0, 100.0))) to Vec2(0.0, 0.0),
            listOf(wall("a", Vec2(1e12, 1e12), Vec2(1e12 + 1000.0, 1e12)),
                wall("b", Vec2(1e12 + 500.0, 1e12 - 500.0), Vec2(1e12 + 500.0, 1e12 + 500.0))) to Vec2(1e12 + 500.0, 1e12 + 20.0),
        )
        for ((walls, input) in scenes) for (tolerance in listOf(0.0, 20.0)) {
            val document = SketchDocument(walls = walls.associateBy { it.id })
            assertEquals(exhaustive(document, input, tolerance, false), engine.snap(document, input,
                SnapContext(tolerance, endpoints = false, controlLines = false, intersections = true)))
        }
        val epsilonScene = SketchDocument(walls = scenes.first().first.associateBy { it.id })
        val epsilonLines = epsilonScene.walls.values.map { WallGeometry.referenceLine(it) }
        val epsilonPoint = requireNotNull(Geometry.segmentIntersection(epsilonLines[0], epsilonLines[1]))
        assertEquals(SnapType.INTERSECTION, engine.snap(epsilonScene, epsilonPoint,
            SnapContext(0.0, endpoints = false, controlLines = false, intersections = true)).type)
    }
}
