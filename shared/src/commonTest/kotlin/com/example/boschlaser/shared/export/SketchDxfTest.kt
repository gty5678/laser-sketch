package com.example.boschlaser.shared.export

import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.*
import kotlin.test.*

class SketchDxfTest {
    @Test fun emptyDrawingHasValidEnvelopeAndMillimetres() {
        val text = sketchDxf(SketchDocument())
        assertTrue(text.contains("9\n\$INSUNITS\n70\n4\n"))
        assertTrue(text.endsWith("0\nENDSEC\n0\nEOF\n"))
        assertFalse(text.contains("0\nLINE\n"))
    }

    @Test fun columnExportsExactRadiusAndReflectedCoordinates() {
        val column = CircularColumn("column", Vec2(-125.5, 230.0), 400.0)
        val text = sketchDxf(SketchDocument(columns = mapOf(column.id to column)))
        assertTrue(text.contains("10\n-125.5\n20\n-230.0\n40\n200.0\n"))
    }

    @Test fun openingCutsWallOutlinesAndExportsJambs() {
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(3000.0, 0.0))
        val opening = Opening("door", wall.id, 1500.0, 1000.0, OpeningType.DOOR, doorStyle = DoorStyle.OPENING)
        val document = SketchDocument(walls = mapOf(wall.id to wall), openings = mapOf(opening.id to opening))
        val text = sketchDxf(document)
        assertTrue(text.contains("11\n1000.0\n"))
        assertTrue(text.contains("10\n2000.0\n"))
        assertTrue(text.contains("8\nOPENINGS\n"))
    }
}
