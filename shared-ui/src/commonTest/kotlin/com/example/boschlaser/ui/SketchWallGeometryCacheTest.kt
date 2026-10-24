package com.example.boschlaser.ui

import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.geometry.WallNetworkGeometry
import com.example.boschlaser.shared.model.Wall
import kotlin.test.*

class SketchWallGeometryCacheTest {
    @Test fun screenAndMagnifierReuseGeometryWithoutChangingWallJoints() {
        val first = Wall("first", Vec2(0.0, 0.0), Vec2(2000.0, 0.0))
        val second = Wall("second", first.end, Vec2(2600.0, 1000.0))
        val walls = mapOf(first.id to first, second.id to second)
        val cache = SketchWallGeometryCache()
        val screen = cache.polygons(walls, 25.0)
        assertSame(screen, cache.polygons(walls, 10.0))
        val expected = walls.values.map { WallNetworkGeometry.bodyPolygon(it, walls.values, 25.0) } +
            WallNetworkGeometry.jointPolygons(walls.values, 25.0)
        assertEquals(expected, screen)
        val moved = walls + (first.id to first.copy(start = Vec2(0.0, 100.0)))
        assertEquals(moved.values.map { WallNetworkGeometry.bodyPolygon(it, moved.values, 25.0) } +
            WallNetworkGeometry.jointPolygons(moved.values, 25.0), cache.polygons(moved, 25.0))
        assertNotSame(screen, cache.polygons(moved, 25.0))
    }

    @Test fun zoomedOutMinimumWidthIsPreservedAndInvalidatesOnThicknessChange() {
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(2000.0, 0.0), thickness = 50.0)
        val walls = mapOf(wall.id to wall)
        val cache = SketchWallGeometryCache()
        val wide = cache.polygons(walls, 100.0)
        assertEquals(listOf(WallNetworkGeometry.bodyPolygon(wall, walls.values, 100.0)), wide)
        val narrow = cache.polygons(walls, 10.0)
        assertNotEquals(wide, narrow)
        assertSame(wide, cache.polygons(walls, 100.0))
        val thick = walls + (wall.id to wall.copy(thickness = 400.0))
        assertEquals(listOf(WallNetworkGeometry.bodyPolygon(thick.getValue(wall.id), thick.values, 100.0)),
            cache.polygons(thick, 100.0))
    }
}
