package com.example.boschlaser.shared.command

import com.example.boschlaser.shared.editor.*
import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.storage.*
import com.example.boschlaser.shared.tool.PointerContext
import kotlin.math.*
import kotlin.test.*

class ObjectDimensionTest {
    private fun fixture(): SketchDocument {
        val host = Wall("host", Vec2(0.0, 0.0), Vec2(6000.0, 0.0), 200.0)
        val side = Wall("side", Vec2(0.0, -2000.0), Vec2(0.0, 2000.0), 200.0)
        return SketchDocument(walls = mapOf(host.id to host, side.id to side),
            openings = mapOf("door" to Opening("door", host.id, 2000.0, 800.0, OpeningType.DOOR),
                "window" to Opening("window", host.id, 4400.0, 1000.0, OpeningType.WINDOW)),
            columns = mapOf("rect" to RectColumn("rect", Vec2(3000.0, 1500.0), 600.0, 400.0),
                "circle" to CircularColumn("circle", Vec2(5000.0, 1500.0), 500.0)))
    }
    private fun withDimension(first: DimensionReference, second: DimensionReference) =
        assertNotNull(AddWallDimensionCommand(WallDimension("dim", first, second, Vec2(1500.0, 1000.0))).apply(fixture()))
    private fun value(document: SketchDocument) =
        WallDimensionGeometry.layout(document, document.dimensions.getValue("dim"))!!.valueMm

    @Test fun everyReferenceResolvesAndMixedParallelPairsMeasurePhysicalLocations() {
        val document = fixture()
        val references = WallDimensionGeometry.references(document)
        assertEquals(30, references.size)
        references.forEach { assertNotNull(WallDimensionGeometry.face(document, it), "$it") }
        var pairs = 0
        for (first in references) for (second in references) {
            if (first.sameEntity(second)) continue
            val dimension = WallDimension("mixed", first, second, Vec2(700.0, 800.0))
            val a = WallDimensionGeometry.face(document, first)!!
            val b = WallDimensionGeometry.face(document, second)!!
            val parallel = abs(Geometry.normalize(a.end - a.start).cross(Geometry.normalize(b.end - b.start))) < 1e-6
            val layout = WallDimensionGeometry.layout(document, dimension)
            if (!parallel) assertNull(layout) else {
                assertNotNull(layout)
                val direction = Geometry.normalize(a.end - a.start)
                assertEquals(abs((b.start - a.start).cross(direction)), layout.valueMm, 1e-7)
                pairs++
            }
        }
        assertTrue(pairs > 100)
    }

    @Test fun wallCenterIsPhysicalCenterForAllControlLineAlignments() {
        for (alignment in WallAlignment.entries) {
            val wall = fixture().walls.getValue("side").copy(alignment = alignment)
            val document = fixture().copy(walls = fixture().walls + (wall.id to wall))
            assertEquals(WallGeometry.build(wall).centerLine,
                WallDimensionGeometry.face(document, WallFaceReference(wall.id, WallFaceSide.CENTER)))
        }
    }

    @Test fun doorAndWindowJambsMoveOnlyAlongHostWithUndoAndAtomicFailure() {
        for (id in listOf("door", "window")) for (side in listOf(OpeningFrameSide.START, OpeningFrameSide.END)) {
            val original = withDimension(WallFaceReference("side", WallFaceSide.RIGHT), OpeningFrameReference(id, side))
            val editor = SketchEditor(original)
            assertTrue(editor.execute(DriveWallDimensionCommand("dim", value(original) + 125.125)))
            assertEquals(value(original) + 125.125, value(editor.document), .001)
            val before = original.openings.getValue(id)
            assertEquals(before.copy(offset = before.offset + 125.125), editor.document.openings.getValue(id))
            assertEquals(original.walls, editor.document.walls)
            assertEquals(original.columns, editor.document.columns)
            assertNull(DriveWallDimensionCommand("dim", 10000.0).apply(editor.document))
            assertTrue(editor.undo())
            assertEquals(original, editor.document)
            assertTrue(editor.canRedo)
            for (invalid in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 1000001.0)) {
                assertFalse(editor.execute(DriveWallDimensionCommand("dim", invalid)))
                assertEquals(original, editor.document)
                assertTrue(editor.canRedo)
            }
            assertTrue(editor.redo())
        }
    }

    @Test fun framesOnDifferentOpeningsDriveInBothDirectionsWithoutChangingWidths() {
        val document = withDimension(OpeningFrameReference("door", OpeningFrameSide.END),
            OpeningFrameReference("window", OpeningFrameSide.START))
        val oriented = WallDimensionGeometry.forMovingSelection(document.dimensions.getValue("dim"), Selection.Opening("door"))!!
        val result = assertNotNull(DriveWallFaceDistanceCommand(oriented, 1300.0).apply(document))
        assertEquals(1300.0, value(result), .001)
        assertEquals(document.openings.getValue("window"), result.openings.getValue("window"))
        assertEquals(2200.0, result.openings.getValue("door").offset, .001)
        assertEquals(800.0, result.openings.getValue("door").width, .001)
        assertEquals(document.dimensions, result.dimensions)
    }

    @Test fun columnsTranslateAgainstWallCentersFramesAndOtherColumns() {
        val pairs = listOf(
            WallFaceReference("side", WallFaceSide.CENTER) to ColumnLineReference("rect", ColumnReferenceLine.CENTER_Y),
            OpeningFrameReference("door", OpeningFrameSide.END) to ColumnLineReference("circle", ColumnReferenceLine.LEFT),
            ColumnLineReference("rect", ColumnReferenceLine.RIGHT) to ColumnLineReference("circle", ColumnReferenceLine.CENTER_Y),
            WallFaceReference("host", WallFaceSide.LEFT) to ColumnLineReference("rect", ColumnReferenceLine.TOP),
            OpeningFrameReference("window", OpeningFrameSide.LEFT) to ColumnLineReference("circle", ColumnReferenceLine.BOTTOM),
        )
        for ((first, second) in pairs) {
            val document = withDimension(first, second)
            val result = assertNotNull(DriveWallDimensionCommand("dim", value(document) + 125.25).apply(document))
            assertEquals(value(document) + 125.25, value(result), .001)
            assertEquals(document.walls, result.walls)
            assertEquals(document.openings, result.openings)
            val before = document.columns.getValue(second.columnId)
            val after = result.columns.getValue(second.columnId)
            assertEquals(125.25, Geometry.distance(before.center, after.center), .001)
            when (before) {
                is RectColumn -> assertEquals(before.copy(center = after.center), after)
                is CircularColumn -> assertEquals(before.copy(center = after.center), after)
            }
        }
    }

    @Test fun rotatedColumnAxesAndEdgesStayAssociativeWhileDriving() {
        for (angle in listOf(25.0, 90.0, 180.0, 315.0)) {
            val radians = angle * PI / 180.0
            fun rotate(p: Vec2) = Vec2(p.x * cos(radians) - p.y * sin(radians), p.x * sin(radians) + p.y * cos(radians))
            val wall = Wall("wall", rotate(Vec2(0.0, -2000.0)), rotate(Vec2(0.0, 2000.0)), 200.0)
            val column = RectColumn("column", rotate(Vec2(3000.0, 0.0)), 600.0, 400.0, angle)
            for (line in listOf(ColumnReferenceLine.LEFT, ColumnReferenceLine.CENTER_Y, ColumnReferenceLine.RIGHT)) {
                val dimension = WallDimension("dim", WallFaceReference(wall.id, WallFaceSide.CENTER),
                    ColumnLineReference(column.id, line), rotate(Vec2(1500.0, 0.0)))
                val document = SketchDocument(walls = mapOf(wall.id to wall), columns = mapOf(column.id to column),
                    dimensions = mapOf(dimension.id to dimension))
                val result = assertNotNull(DriveWallDimensionCommand("dim", 3500.125).apply(document))
                assertEquals(3500.125, value(result), .001)
                assertEquals(angle, (result.columns.getValue(column.id) as RectColumn).rotationDegrees)
                assertEquals(document.walls, result.walls)
            }
        }
    }

    @Test fun unsupportedFrameTranslationAndMovingTheFixedHostedFrameAreAtomicFailures() {
        val document = withDimension(WallFaceReference("host", WallFaceSide.CENTER),
            OpeningFrameReference("door", OpeningFrameSide.LEFT))
        assertNull(DriveWallDimensionCommand("dim", 200.0).apply(document))
        val reverse = WallDimensionGeometry.forMovingSelection(document.dimensions.getValue("dim"), Selection.Wall("host"))!!
        assertNull(DriveWallFaceDistanceCommand(reverse, 200.0).apply(document))
        assertEquals(100.0, value(document), .001)
    }

    @Test fun movingWallAgainstColumnPreservesTopologyAndKeepsColumnFixed() {
        val document = withDimension(ColumnLineReference("rect", ColumnReferenceLine.CENTER_Y),
            WallFaceReference("side", WallFaceSide.CENTER))
        val result = assertNotNull(DriveWallDimensionCommand("dim", 3200.0).apply(document))
        assertEquals(3200.0, value(result), .001)
        assertEquals(-200.0, result.walls.getValue("side").start.x, .001)
        assertEquals(document.columns, result.columns)
    }

    @Test fun pickingCentersFramesAndEdgesFiltersSecondReferenceByParallelDirection() {
        val document = fixture()
        assertEquals(WallFaceReference("side", WallFaceSide.CENTER),
            WallDimensionGeometry.pickFace(document, Vec2(0.0, 1000.0), 10.0))
        assertEquals(OpeningFrameReference("door", OpeningFrameSide.START),
            WallDimensionGeometry.pickFace(document, Vec2(1600.0, 0.0), 10.0))
        assertEquals(ColumnLineReference("rect", ColumnReferenceLine.LEFT),
            WallDimensionGeometry.pickFace(document, Vec2(2700.0, 1500.0), 10.0))
        assertEquals(ColumnLineReference("rect", ColumnReferenceLine.CENTER_Y),
            WallDimensionGeometry.pickFace(document, Vec2(3000.0, 1500.0), 10.0,
                WallFaceReference("side", WallFaceSide.CENTER)))
        assertNull(WallDimensionGeometry.pickFace(document, Vec2(2850.0, 1700.0), 10.0,
            WallFaceReference("side", WallFaceSide.CENTER)))
    }

    @Test fun selectedReferencedObjectActivatesNumericLabelAndKeepsSelection() {
        val document = withDimension(OpeningFrameReference("door", OpeningFrameSide.END),
            ColumnLineReference("rect", ColumnReferenceLine.LEFT))
        for (selection in listOf(Selection.Opening("door"), Selection.Column("rect"), Selection.Wall("side"), null)) {
            val editor = SketchEditor(document)
            editor.select(selection)
            val point = Vec2(2500.0, 1000.0)
            val context = PointerContext(point, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, dimensionLabelId = "dim")
            editor.pointerDown(point, context)
            val update = editor.pointerUp(point, context)
            if (selection is Selection.Opening || selection is Selection.Column) {
                assertNotNull(update.editFaceDimension)
                assertTrue(WallDimensionGeometry.matches(update.editFaceDimension!!.second, selection))
                assertEquals(selection, editor.state.selection)
            } else assertNull(update.editFaceDimension)
            assertEquals(document, editor.document)
            assertFalse(editor.canUndo)
        }
    }

    @Test fun deleteOpeningColumnOrHostRemovesOnlyDependentDimensionsAndUndoRestoresReferences() {
        val source = withDimension(OpeningFrameReference("door", OpeningFrameSide.END),
            ColumnLineReference("rect", ColumnReferenceLine.LEFT))
        for (command in listOf(DeleteOpeningCommand("door"), DeleteColumnCommand("rect"), DeleteWallCommand("host"))) {
            val editor = SketchEditor(source)
            assertTrue(editor.execute(command))
            assertTrue(editor.document.dimensions.isEmpty())
            assertTrue(editor.undo())
            assertEquals(source, editor.document)
            assertTrue(editor.redo())
        }
        assertEquals(source.dimensions, DeleteColumnCommand("circle").apply(source)!!.dimensions)
    }

    @Test fun mixedReferencesRoundTripAndMissingOrAmbiguousTargetsAreRejected() {
        val fixture = fixture()
        val dimensions = listOf(
            WallDimension("centers", WallFaceReference("side", WallFaceSide.CENTER),
                ColumnLineReference("rect", ColumnReferenceLine.CENTER_Y), Vec2(1500.0, 1000.0)),
            WallDimension("frames", OpeningFrameReference("door", OpeningFrameSide.END),
                OpeningFrameReference("window", OpeningFrameSide.START), Vec2(3000.0, 500.0)),
        )
        val project = Project(ProjectManifest("fixture", "Mixed references", 1L),
            fixture.copy(dimensions = dimensions.associateBy { it.id }))
        val files = ProjectSerializer.encode(project)
        assertEquals(project, ProjectSerializer.decode(files))
        for ((from, to) in listOf("\"columnId\":\"rect\"" to "\"columnId\":\"missing\"",
            "\"openingId\":\"door\"" to "\"openingId\":\"missing\"",
            "\"columnId\":\"rect\"" to "\"columnId\":\"rect\",\"wallId\":\"side\"")) {
            assertFailsWith<ProjectFormatException> {
                ProjectSerializer.decode(files + ("sketch.json" to files.getValue("sketch.json").replace(from, to)))
            }
        }
    }
}
