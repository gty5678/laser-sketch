package com.example.boschlaser.shared.storage

import com.example.boschlaser.shared.annotation.*
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.geometry.WallGeometry
import com.example.boschlaser.shared.model.*
import kotlin.test.*

class ProjectMigrationTest {
    private val header = """"version":1,"id":"old","name":"Legacy fixture","updatedAt":123"""
    private fun sketch(control: String = "INNER") = """{$header,"walls":[{"id":"w","start":{"x":0.125,"y":0},"end":{"x":4000.125,"y":0},"thickness":240,"controlLine":"$control","measuredLength":4000.5}],"columns":[{"id":"c","type":"CIRCLE","center":{"x":-20,"y":300},"width":400,"depth":999,"rotationDegrees":45}],"openings":[{"id":"d","type":"DOOR","wallId":"w","position":0.25,"width":800,"flipped":true,"hingeFlipped":true}]}"""
    @Test fun centerLineMigrationPreservesWallBodyAndAbsoluteOpeningPosition() {
        for ((control, alignment, referenceY) in listOf(Triple("CENTER", WallAlignment.CENTER, 0.0), Triple("INNER", WallAlignment.LEFT, -120.0), Triple("OUTER", WallAlignment.RIGHT, 120.0))) {
            val migrated = ProjectMigration.androidSketch(sketch(control))
            val wall = migrated.project.sketch.walls.getValue("w")
            assertEquals(Vec2(0.125, referenceY), wall.start)
            assertEquals(Vec2(0.125, 0.0), WallGeometry.build(wall).centerLine.start)
            assertEquals(alignment, wall.alignment)
            assertEquals(4000.5, wall.measuredLength)
            val door = migrated.project.sketch.openings.getValue("d")
            assertEquals(1000.0, door.offset)
            assertEquals(800.0, door.width)
            assertTrue(door.flipped && door.hingeFlipped)
            assertEquals(400.0, (migrated.project.sketch.columns.getValue("c") as CircularColumn).diameter)
            assertTrue(migrated.notices.isEmpty())
            assertEquals(migrated.project, ProjectSerializer.decode(ProjectSerializer.encode(migrated.project)))
        }
    }
    @Test fun orphanOpeningIsRemovedWithExplicitNotice() {
        val migrated = ProjectMigration.androidSketch(sketch().replace("\"wallId\":\"w\"", "\"wallId\":\"missing\""))
        assertTrue(migrated.project.sketch.openings.isEmpty())
        assertEquals(listOf(MigrationNotice.ORPHAN_OPENING), migrated.notices)
    }
    @Test fun photoStylesCoordinatesAndDeterministicIdsArePreserved() {
        val text = """{$header,"dimensions":[{"startX":0.1,"startY":0.2,"endX":0.8,"endY":0.9,"label":"1200 mm","color":-1,"thickness":3}],"texts":[{"x":0.25,"y":0.75,"text":"Test","color":-65536,"textSize":2,"whiteBackground":false}],"angles":[{"vertexX":0.1,"vertexY":0.2,"firstX":0.3,"firstY":0.4,"secondX":0.5,"secondY":0.6,"label":"45"}],"areas":[{"points":[{"x":0,"y":0},{"x":1,"y":0},{"x":0,"y":1}],"label":"2"},{"points":[]}]}"""
        val asset = ProjectAsset("photo", "assets/photo.jpg", "image/jpeg")
        val migrated = ProjectMigration.androidAnnotation(text, asset)
        assertEquals(migrated, ProjectMigration.androidAnnotation(text, asset))
        val annotations = migrated.project.annotations.getValue(asset.id).annotations
        assertEquals(listOf("dimension-0", "text-0", "angle-0", "area-0"), annotations.keys.toList())
        assertEquals(3, (annotations.getValue("dimension-0") as DimensionAnnotation).thicknessPreset)
        val mark = annotations.getValue("text-0") as TextAnnotation
        assertEquals(PhotoPoint(0.25, 0.75), mark.position)
        assertEquals(-65536, mark.colorArgb)
        assertFalse(mark.whiteBackground)
        assertEquals(listOf(MigrationNotice.INCOMPLETE_AREA), migrated.notices)
        assertEquals(migrated.project, ProjectSerializer.decode(ProjectSerializer.encode(migrated.project)))
    }
    @Test fun optionalOldFieldsUseDocumentedDefaults() {
        val migrated = ProjectMigration.androidSketch("""{$header,"walls":[{"start":{"x":0,"y":0},"end":{"x":2000,"y":0}}]}""")
        assertEquals("wall-0", migrated.project.sketch.walls.values.single().id)
        assertEquals(240.0, migrated.project.sketch.walls.values.single().thickness)
        assertEquals(WallAlignment.CENTER, migrated.project.sketch.walls.values.single().alignment)
    }
    @Test fun unsupportedCorruptAndInvalidGeometryNeverProduceATarget() {
        assertEquals(ProjectFormatFailure.UNSUPPORTED_VERSION, assertFailsWith<ProjectFormatException> {
            ProjectMigration.androidSketch(sketch().replace("\"version\":1", "\"version\":2"))
        }.reason)
        for (text in listOf(sketch().replace("4000.125", "0.125"), sketch().replace("\"width\":800", "\"width\":8000"), sketch().replace("\"thickness\":240", "\"thickness\":\"240\""), sketch().replace("INNER", "UNKNOWN")))
            assertFailsWith<ProjectFormatException> { ProjectMigration.androidSketch(text) }
        assertFailsWith<ProjectFormatException> { ProjectMigration.androidSketch("{") }
        assertFailsWith<ProjectFormatException> { ProjectMigration.androidSketch("""{"version":1,"name":"Missing ID"}""", "override") }
    }
}
