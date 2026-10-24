package com.example.boschlaser.shared.storage

import com.example.boschlaser.shared.annotation.*
import com.example.boschlaser.shared.bosch.MeasurementOperation
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.geometry.WallGeometry
import com.example.boschlaser.shared.geometry.OpeningGeometry
import com.example.boschlaser.shared.measurement.Measurement
import com.example.boschlaser.shared.measurement.MeasurementRecord
import com.example.boschlaser.shared.model.*
import kotlin.test.*

class ProjectSerializerTest {
    private fun emptyFiles() = linkedMapOf(
        "manifest.json" to """{"format":"LaserSketch","formatVersion":1,"projectId":"p","name":"Fixture","updatedAt":9007199254740993,"unit":"mm","assets":[]}""",
        "sketch.json" to """{"formatVersion":1,"walls":[],"openings":[],"columns":[],"bindings":[]}""",
        "measurements.json" to "[]", "annotations.json" to "[]",
    )
    private fun fixture(): Project {
        val wall = Wall("wall", Vec2(0.123456789, -40.5), Vec2(5000.123456789, -40.5), 237.75, WallAlignment.LEFT, 5000.25)
        val other = Wall("other", Vec2(40.5, 0.0), Vec2(40.5, 1000.0), alignment = WallAlignment.RIGHT)
        val door = Opening("door", wall.id, 1500.125, 810.5, OpeningType.DOOR, true, true)
        val window = Opening("window", wall.id, 3500.5, 1000.0, OpeningType.WINDOW)
        val rectangle = RectColumn("rectangle", Vec2(300.123, -200.5), 440.5, 380.0, 37.25)
        val circle = CircularColumn("circle", Vec2(-450.25, 420.0), 390.125)
        val asset = ProjectAsset("photo", "assets/photo.png", "image/png", 2400, 1600)
        val point = PhotoPoint(0.123456789, 0.3)
        val annotations = listOf(
            DimensionAnnotation("dimension", point, PhotoPoint(0.9, 0.7), "5000.25 mm", -16711936, 2),
            TextAnnotation("text", point, "Unicode: \u6d4b\u91cf\n\"quoted\" \\ path {[]}", -1, 3, false),
            AngleAnnotation("angle", point, PhotoPoint(0.5, 0.3), PhotoPoint(0.1, 0.7), "90", -65536),
            AreaAnnotation("area", listOf(point, PhotoPoint(0.9, 0.2), PhotoPoint(0.8, 0.9)), "12.5", -16776961),
        )
        val record = MeasurementRecord(Measurement("record", 5000.25, 9007199254740993L, "test-device", MeasurementOperation.ADD, 6200.75), byteArrayOf(0, 127, -128, -1), "legacy\noperation")
        return Project(ProjectManifest("project", "Portable fixture", 9007199254740993L),
            SketchDocument(walls = linkedMapOf(wall.id to wall, other.id to other), openings = linkedMapOf(door.id to door, window.id to window), columns = linkedMapOf(rectangle.id to rectangle, circle.id to circle)),
            listOf(record), mapOf(asset.id to AnnotationDocument(annotations.associateBy { it.id }, asset.id)), mapOf(asset.id to asset),
            listOf(MeasurementBinding(record.measurement.id, wall.id, MeasurementProperty.WALL_LENGTH)))
    }
    private fun rejected(files: Map<String, String>, reason: ProjectFormatFailure = ProjectFormatFailure.INVALID_DATA) {
        assertEquals(reason, assertFailsWith<ProjectFormatException> { ProjectSerializer.decode(files) }.reason)
    }
    @Test fun completeProjectRoundTripPreservesPrecisionOrderStylesAndRawBytes() {
        val project = fixture()
        val files = ProjectSerializer.encode(project)
        val restored = ProjectSerializer.decode(files)
        assertEquals(project, restored)
        assertEquals(files, ProjectSerializer.encode(restored))
        assertEquals(listOf("dimension", "text", "angle", "area"), restored.annotations.getValue("photo").annotations.keys.toList())
        assertEquals(9007199254740993L, restored.measurements.single().measurement.timestamp)
        assertFalse(files.getValue("sketch.json").contains("polygon", ignoreCase = true))
        assertFalse(files.getValue("sketch.json").contains("position"))
    }
    @Test fun independentEmptyWireFixtureDecodesWithoutPlatformOrAssets() {
        val project = ProjectSerializer.decode(emptyFiles())
        assertEquals("p", project.manifest.projectId)
        assertEquals(9007199254740993L, project.manifest.updatedAt)
        assertEquals(project, ProjectSerializer.decode(ProjectSerializer.encode(project)))
    }
    @Test fun independentSemanticWireFixtureUsesLocatorCoordinatesAndMillimeterOpeningOffsets() {
        val files = emptyFiles().apply {
            this["sketch.json"] = """{
                "formatVersion":1,
                "walls":[{"id":"wall","start":[10.123456789,20.0],"end":[5010.123456789,20.0],
                    "thickness":200.0,"alignment":"LEFT"}],
                "openings":[{"id":"door","hostWallId":"wall","offset":1800.0,"width":900.0,
                    "type":"DOOR","flipped":true,"hingeFlipped":false}],
                "columns":[{"id":"rect","type":"RECTANGLE","center":[300.123456789,600.0],
                    "width":400.0,"depth":300.0,"rotationDegrees":15.0},
                    {"id":"circle","type":"CIRCLE","center":[900.0,600.0],"diameter":350.0}],
                "bindings":[{"measurementId":"record","targetEntityId":"wall","property":"WALL_LENGTH"}]
            }"""
            this["measurements.json"] = """[{"id":"record","valueMm":4278.123456789,
                "timestamp":9007199254740993,"rawHex":"007f80ff"}]"""
        }
        val project = ProjectSerializer.decode(files)
        val wall = project.sketch.walls.getValue("wall")
        assertEquals(Vec2(10.123456789, 20.0), wall.start)
        assertEquals(WallAlignment.LEFT, wall.alignment)
        assertEquals(Vec2(10.123456789, 120.0), WallGeometry.build(wall).centerLine.start)
        val door = project.sketch.openings.getValue("door")
        assertEquals(1800.0, door.offset, 0.0)
        assertEquals(900.0, door.width, 0.0)
        assertTrue(door.flipped)
        assertFalse(door.hingeFlipped)
        val cutout = OpeningGeometry.cutoutPolygon(door, wall)
        assertEquals(1360.123456789, cutout.first().x, 1e-9)
        assertEquals(20.0, cutout.first().y, 1e-9)
        assertEquals(2260.123456789, cutout[2].x, 1e-9)
        assertEquals(220.0, cutout[2].y, 1e-9)
        assertEquals(RectColumn("rect", Vec2(300.123456789, 600.0), 400.0, 300.0, 15.0),
            project.sketch.columns.getValue("rect"))
        assertEquals(CircularColumn("circle", Vec2(900.0, 600.0), 350.0), project.sketch.columns.getValue("circle"))
        val measurement = project.measurements.single()
        assertEquals(4278.123456789, measurement.measurement.valueMm, 0.0)
        assertEquals(9007199254740993L, measurement.measurement.timestamp)
        assertContentEquals(byteArrayOf(0, 127, -128, -1), measurement.raw)
        assertEquals(listOf(MeasurementBinding("record", "wall", MeasurementProperty.WALL_LENGTH)), project.bindings)
        assertEquals(project, ProjectSerializer.decode(ProjectSerializer.encode(project)))
    }
    @Test fun versionsFormatAndUnitsHaveDistinctFailures() {
        for ((old, replacement, reason) in listOf(
            Triple("LaserSketch", "Unknown", ProjectFormatFailure.UNSUPPORTED_FORMAT),
            Triple("\"formatVersion\":1", "\"formatVersion\":2", ProjectFormatFailure.UNSUPPORTED_VERSION),
            Triple("\"mm\"", "\"m\"", ProjectFormatFailure.UNSUPPORTED_UNIT),
        )) rejected(emptyFiles().apply { this["manifest.json"] = getValue("manifest.json").replace(old, replacement) }, reason)
        rejected(emptyFiles().apply { this["sketch.json"] = getValue("sketch.json").replace(":1", ":2") }, ProjectFormatFailure.UNSUPPORTED_VERSION)
    }
    @Test fun missingUnexpectedAndMalformedMetadataAreRejected() {
        rejected(emptyFiles().apply { remove("annotations.json") }, ProjectFormatFailure.MISSING_ENTRY)
        rejected(emptyFiles().apply { put("extra.json", "{}") }, ProjectFormatFailure.UNKNOWN_ENTRY)
        rejected(emptyFiles().apply { put("annotations.json", "[") }, ProjectFormatFailure.INVALID_JSON)
        rejected(emptyFiles().apply { put("annotations.json", "{}") })
        rejected(emptyFiles().apply { this["manifest.json"] = getValue("manifest.json").replace("\"assets\":[]", "\"assets\":[],\"unknown\":1") })
    }
    @Test fun duplicateObjectKeysIncludingEscapedSpellingAreRejected() {
        rejected(emptyFiles().apply { this["manifest.json"] = getValue("manifest.json").replace("\"formatVersion\":1", "\"formatVersion\":2,\"formatVersion\":1") })
        rejected(emptyFiles().apply { this["manifest.json"] = getValue("manifest.json").replace("\"name\":\"Fixture\"", "\"name\":\"Fixture\",\"na\\u006de\":\"Other\"") })
    }
    @Test fun duplicateEntityIdsAndOrphanReferencesFailAtomically() {
        val files = ProjectSerializer.encode(fixture())
        val wallJson = """{"id":"w","start":[0,0],"end":[1000,0],"thickness":240,"alignment":"CENTER"}"""
        rejected(emptyFiles().apply { this["sketch.json"] = getValue("sketch.json").replace("\"walls\":[]", "\"walls\":[$wallJson,$wallJson]") })
        rejected(files + ("sketch.json" to files.getValue("sketch.json").replace("\"hostWallId\":\"wall\"", "\"hostWallId\":\"missing\"")))
        rejected(files + ("annotations.json" to files.getValue("annotations.json").replace("\"id\":\"text\"", "\"id\":\"dimension\"")))
        rejected(files + ("measurements.json" to files.getValue("measurements.json").let { it.dropLast(1) + "," + it.drop(1) }))
        rejected(files + ("sketch.json" to files.getValue("sketch.json").replace("\"measurementId\":\"record\"", "\"measurementId\":\"missing\"")))
    }
    @Test fun invalidGeometryNumericTypesAndHexAreRejected() {
        val files = ProjectSerializer.encode(fixture())
        for ((old, replacement) in listOf("237.75" to "0", "237.75" to "\"237.75\"", "237.75" to "NaN", "\"offset\":1500.125" to "\"offset\":9999", "\"flipped\":true" to "\"flipped\":\"true\""))
            rejected(files + ("sketch.json" to files.getValue("sketch.json").replace(old, replacement)))
        for (hex in listOf("f", "gg")) rejected(files + ("measurements.json" to files.getValue("measurements.json").replace("007f80ff", hex)))
        rejected(files + ("manifest.json" to files.getValue("manifest.json").replace("assets/photo.png", "assets/../photo.png")))
    }
    @Test fun oversizedAndDeepInputAreBoundedBeforeParsing() {
        rejected(emptyFiles().apply { put("annotations.json", "[".repeat(65) + "]".repeat(65)) }, ProjectFormatFailure.SIZE_LIMIT)
        rejected(emptyFiles().apply { put("annotations.json", " ".repeat(8 * 1024 * 1024 + 1)) }, ProjectFormatFailure.SIZE_LIMIT)
    }
}
