package com.example.boschlaser.ui

import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.*
import kotlin.test.*

class SketchWallPathCacheTest {
    private val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(2000.0, 0.0))
    private val opening = Opening("door", wall.id, 500.0, 600.0, OpeningType.DOOR)
    private val document = SketchDocument(walls = mapOf(wall.id to wall), openings = mapOf(opening.id to opening))

    @Test fun navigationReusesScreenAndMagnifierPaths() {
        val cache = SketchWallPathCache<Any>()
        val screen = cache.get(document, .5) { Any() }
        val lens = cache.get(document, 1.8) { Any() }
        repeat(100) {
            assertSame(screen, cache.get(document, .5) { error("Rebuilt while panning") })
            assertSame(lens, cache.get(document, 1.8) { error("Rebuilt magnifier") })
        }
        assertSame(screen, cache.get(document.copy(columns = mapOf("column" to CircularColumn("column", Vec2(0.0, 0.0), 100.0))), .5) { error("Unrelated edit rebuilt walls") })
        assertNotSame(screen, cache.get(document, .6) { Any() })
    }

    @Test fun geometryMaterialAndOpeningChangesInvalidatePaths() {
        val changes = listOf(
            document.copy(walls = mapOf(wall.id to wall.copy(end = Vec2(2500.0, 100.0)))),
            document.copy(walls = mapOf(wall.id to wall.copy(thickness = 300.0))),
            document.copy(walls = mapOf(wall.id to wall.copy(fill = WallFill.BLACK))),
            document.copy(openings = mapOf(opening.id to opening.copy(offset = 900.0))),
            document.copy(openings = emptyMap()),
        )
        changes.forEach { changed ->
            val cache = SketchWallPathCache<Any>()
            val original = cache.get(document, .5) { Any() }
            assertNotSame(original, cache.get(changed, .5) { Any() })
            assertNotSame(original, cache.get(document, .5) { Any() })
        }
    }
}
