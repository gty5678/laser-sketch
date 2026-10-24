package com.example.boschlaser.shared.command

import com.example.boschlaser.shared.editor.*
import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.storage.*
import com.example.boschlaser.shared.tool.PointerContext
import com.example.boschlaser.shared.tool.ToolFailure
import kotlin.math.*
import kotlin.test.*

class WallDimensionTest {
    private fun room(): SketchDocument {
        val walls = listOf(
            Wall("left", Vec2(0.0, 0.0), Vec2(0.0, 3000.0), 200.0),
            Wall("right", Vec2(4000.0, 0.0), Vec2(4000.0, 3000.0), 200.0),
            Wall("bottom", Vec2(0.0, 0.0), Vec2(4000.0, 0.0), 200.0),
            Wall("top", Vec2(0.0, 3000.0), Vec2(4000.0, 3000.0), 200.0),
        )
        val dimension = WallDimension("dim", WallFaceReference("left", WallFaceSide.RIGHT),
            WallFaceReference("right", WallFaceSide.LEFT), Vec2(2000.0, 1500.0))
        return SketchDocument(walls = walls.associateBy { it.id }, dimensions = mapOf(dimension.id to dimension))
    }
    private fun value(document: SketchDocument) = WallDimensionGeometry.layout(document, document.dimensions.getValue("dim"))!!.valueMm

    @Test fun netDistanceMovesWholeWallAndPreservesRectangularConnectionsAndHistory() {
        val initial = room()
        assertEquals(3800.0, value(initial), 1e-8)
        val editor = SketchEditor(initial)
        assertTrue(editor.execute(DriveWallDimensionCommand("dim", 4200.125)))
        val result = editor.document
        assertEquals(4200.125, value(result), 1e-8)
        assertEquals(initial.walls.getValue("left"), result.walls.getValue("left"))
        val right = result.walls.getValue("right")
        assertEquals(4400.125, right.start.x, 1e-8)
        assertEquals(right.start.x, right.end.x, 1e-8)
        assertEquals(right.start, result.walls.getValue("bottom").end)
        assertEquals(right.end, result.walls.getValue("top").end)
        assertEquals(3000.0, right.end.y, 1e-8)
        assertTrue(editor.undo())
        assertEquals(initial, editor.document)
        assertFalse(editor.canUndo)
        assertTrue(editor.redo())
        assertEquals(result, editor.document)
    }

    @Test fun rotatedReversedAndAllLocatorAlignmentsUsePhysicalFaces() {
        for (alignment in WallAlignment.entries) for (angle in listOf(0.0, .45, PI / 2, PI)) for (reverse in listOf(false, true)) {
            fun rotate(point: Vec2) = Vec2(point.x * cos(angle) - point.y * sin(angle), point.x * sin(angle) + point.y * cos(angle))
            val source = room()
            val walls = source.walls.mapValues { (_, wall) -> wall.copy(
                start = rotate(if (reverse) wall.end else wall.start), end = rotate(if (reverse) wall.start else wall.end), alignment = alignment) }
            val original = source.dimensions.getValue("dim")
            fun reference(ref: WallFaceReference) = if (reverse) ref.copy(side = if (ref.side == WallFaceSide.LEFT) WallFaceSide.RIGHT else WallFaceSide.LEFT) else ref
            val dimension = original.copy(first = reference(assertIs<WallFaceReference>(original.first)),
                second = reference(assertIs<WallFaceReference>(original.second)), position = rotate(original.position))
            val document = source.copy(walls = walls, dimensions = mapOf("dim" to dimension))
            assertEquals(3800.0, value(document), 1e-7)
            val result = assertNotNull(DriveWallDimensionCommand("dim", 4100.75).apply(document))
            assertEquals(4100.75, value(result), 1e-7)
            result.walls.forEach { (id, wall) ->
                val before = Geometry.normalize(walls.getValue(id).end - walls.getValue(id).start)
                val after = Geometry.normalize(wall.end - wall.start)
                assertEquals(0.0, before.cross(after), 1e-7)
            }
        }
    }

    @Test fun tStemAndHostedOpeningFollowWithFixedOppositeEndpoint() {
        val stem = Wall("stem", Vec2(4000.0, 1500.0), Vec2(2000.0, 1500.0), 200.0)
        val opening = Opening("door", "right", 1200.125, 800.0, OpeningType.DOOR)
        val document = room().let { it.copy(walls = it.walls + (stem.id to stem), openings = mapOf(opening.id to opening)) }
        val result = assertNotNull(DriveWallDimensionCommand("dim", 4200.0).apply(document))
        assertEquals(Vec2(4400.0, 1500.0), result.walls.getValue("stem").start)
        assertEquals(stem.end, result.walls.getValue("stem").end)
        assertEquals(opening, result.openings.getValue("door"))
    }

    @Test fun doorConflictAndInvalidValuesLeaveDocumentAndRedoUntouched() {
        val opening = Opening("door", "bottom", 3000.0, 900.0, OpeningType.DOOR)
        val initial = room().copy(openings = mapOf(opening.id to opening))
        val editor = SketchEditor(initial)
        assertTrue(editor.execute(DriveWallDimensionCommand("dim", 4300.0)))
        assertTrue(editor.undo())
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0, 1_000_001.0, 1000.0)) {
            assertFalse(editor.execute(DriveWallDimensionCommand("dim", invalid)))
            assertEquals(initial, editor.document)
            assertTrue(editor.canRedo)
            assertFalse(editor.canUndo)
        }
        assertFalse(editor.execute(DriveWallDimensionCommand("dim", 3800.0)))
        assertTrue(editor.canRedo)
    }

    @Test fun obliqueCornerUsesIntersectionWithoutRotatingItsNeighbour() {
        val source = room()
        val bottom = source.walls.getValue("bottom").copy(start = Vec2(0.0, -1000.0))
        val left = source.walls.getValue("left").copy(start = bottom.start)
        val document = source.copy(walls = source.walls + (bottom.id to bottom) + (left.id to left))
        val result = assertNotNull(DriveWallDimensionCommand("dim", 4200.0).apply(document))
        assertEquals(Vec2(4400.0, 100.0), result.walls.getValue("right").start)
        assertEquals(result.walls.getValue("right").start, result.walls.getValue("bottom").end)
        assertEquals(0.0, Geometry.normalize(bottom.end - bottom.start).cross(
            Geometry.normalize(result.walls.getValue("bottom").end - bottom.start)), 1e-8)
    }

    @Test fun incompatibleMultiwayCornerRejectsRatherThanRotatesWalls() {
        val diagonal = Wall("diagonal", Vec2(2000.0, -1000.0), Vec2(4000.0, 0.0), 200.0)
        val document = room().let { it.copy(walls = it.walls + (diagonal.id to diagonal)) }
        assertNull(DriveWallDimensionCommand("dim", 4200.0).apply(document))
    }

    @Test fun deleteWallRemovesDependentDimensionAndUndoRestoresBoth() {
        val initial = room()
        val editor = SketchEditor(initial)
        assertTrue(editor.execute(DeleteWallCommand("right")))
        assertTrue(editor.document.dimensions.isEmpty())
        assertTrue(editor.undo())
        assertEquals(initial, editor.document)
        assertTrue(editor.execute(DeleteWallDimensionCommand("dim")))
        assertEquals(initial.walls, editor.document.walls)
    }

    @Test fun dimensionsRoundTripAndLegacyFilesRemainReadable() {
        val project = Project(ProjectManifest("fixture", "Dimensions", 1L), room())
        val encoded = ProjectSerializer.encode(project)
        assertEquals(project, ProjectSerializer.decode(encoded))
        val legacy = project.copy(sketch = room().copy(dimensions = emptyMap()))
        val files = ProjectSerializer.encode(legacy)
        assertFalse(files.getValue("sketch.json").contains("dimensions"))
        assertEquals(legacy, ProjectSerializer.decode(files))
        val invalid = encoded.toMutableMap()
        invalid["sketch.json"] = encoded.getValue("sketch.json").replace("\"wallId\":\"right\"", "\"wallId\":\"missing\"")
        assertFailsWith<ProjectFormatException> { ProjectSerializer.decode(invalid) }
    }

    @Test fun threeTapCreationPreviewCancelAndSelection() {
        val initial = room().copy(dimensions = emptyMap())
        val editor = SketchEditor(initial, idFactory = { "dim" })
        fun tap(point: Vec2): com.example.boschlaser.shared.tool.ToolUpdate {
            val context = PointerContext(point, 20.0, 20.0, 10.0, 10.0, 20.0, 10.0)
            editor.pointerDown(point, context)
            return editor.pointerUp(point, context)
        }
        editor.setTool(ToolType.DIMENSION)
        assertFalse(tap(Vec2(100.0, 1500.0)).documentChanged)
        assertEquals("left", editor.state.dimensionFirst?.entityId)
        assertEquals(ToolFailure.INVALID_DIMENSION, tap(Vec2(2000.0, 100.0)).failure)
        assertFalse(tap(Vec2(3900.0, 1500.0)).documentChanged)
        assertIs<SketchPreview.Dimension>(editor.state.preview)
        assertEquals(initial, editor.document)
        editor.cancelPointer()
        assertNull(editor.state.preview)
        assertNull(editor.state.dimensionFirst)
        assertFalse(editor.canUndo)
        tap(Vec2(100.0, 1500.0)); tap(Vec2(3900.0, 1500.0))
        assertTrue(tap(Vec2(2000.0, 1400.0)).documentChanged)
        assertEquals(3800.0, value(editor.document), 1e-8)
        editor.setTool(ToolType.SELECT)
        tap(Vec2(2000.0, 1400.0))
        assertEquals(Selection.Dimension("dim"), editor.state.selection)
        assertTrue(editor.undo())
        assertEquals(initial, editor.document)
    }

    @Test fun dragChangesOnlyAnnotationPositionAndCancelOrUndoRestoresIt() {
        val initial = room()
        val editor = SketchEditor(initial)
        val start = Vec2(2000.0, 1500.0)
        val end = Vec2(2000.0, 1800.0)
        fun context(point: Vec2) = PointerContext(point, 20.0, 20.0, 10.0, 10.0, 20.0, 10.0)
        editor.pointerDown(start, context(start))
        editor.pointerMove(end, context(end))
        assertEquals(initial, editor.document)
        assertEquals(end, editor.renderDocument.dimensions.getValue("dim").position)
        editor.cancelPointer()
        assertEquals(initial, editor.renderDocument)
        assertFalse(editor.canUndo)
        editor.pointerDown(start, context(start))
        editor.pointerMove(end, context(end))
        assertTrue(editor.pointerUp(end, context(end)).documentChanged)
        assertEquals(initial.walls, editor.document.walls)
        assertEquals(end, editor.document.dimensions.getValue("dim").position)
        assertTrue(editor.undo())
        assertEquals(initial, editor.document)
    }

    @Test fun invalidatedDimensionRemainsSelectableAndBoundsIncludeOutsideAnnotation() {
        val source = room()
        val dimension = source.dimensions.getValue("dim").copy(position = Vec2(2000.0, -5000.0))
        val document = source.copy(dimensions = mapOf(dimension.id to dimension))
        assertEquals(-5800.0, sketchContentBounds(document).min.y, 1e-8)
        val wall = document.walls.getValue("right").copy(end = Vec2(4500.0, 3000.0))
        val invalid = document.copy(walls = document.walls + (wall.id to wall))
        assertNull(WallDimensionGeometry.layout(invalid, dimension))
        assertEquals(dimension, WallDimensionGeometry.hit(invalid, dimension.position, 20.0))
        assertNull(DriveWallDimensionCommand("dim", 4200.0).apply(invalid))
        assertEquals(-5800.0, sketchContentBounds(invalid).min.y, 1e-8)
    }

    @Test fun selectingWallFindsFacingNetDistanceWithoutCreatingAnnotation() {
        val document = room().copy(dimensions = emptyMap())
        for (id in listOf("left", "right", "bottom", "top")) {
            val dimensions = WallDimensionGeometry.forSelectedWall(document, id)
            assertEquals(1, dimensions.size)
            val dimension = dimensions.single()
            assertEquals(id, dimension.second.entityId)
            val expected = if (id in listOf("left", "right")) 3800.0 else 2800.0
            assertEquals(expected, WallDimensionGeometry.layout(document, dimension)!!.valueMm, 1e-8)
            val result = assertNotNull(DriveWallFaceDistanceCommand(dimension, expected + 200.125).apply(document))
            assertTrue(result.dimensions.isEmpty())
            assertEquals(document.walls.getValue(dimension.first.entityId), result.walls.getValue(dimension.first.entityId))
            assertEquals(expected + 200.125, WallDimensionGeometry.layout(result, dimension)!!.valueMm, 1e-8)
        }
    }

    @Test fun temporaryDimensionFindsNearestOnBothSidesAndRejectsUnrelatedWalls() {
        val source = room().copy(dimensions = emptyMap())
        val inside = Wall("inside", Vec2(2000.0, 500.0), Vec2(2000.0, 2500.0), 200.0)
        val outside = Wall("outside", Vec2(-1000.0, 0.0), Vec2(-1000.0, 3000.0), 200.0)
        val remote = Wall("remote", Vec2(500.0, 4000.0), Vec2(500.0, 6000.0), 200.0)
        val coincident = Wall("coincident", Vec2(0.0, 0.0), Vec2(0.0, 3000.0), 200.0)
        val document = source.copy(walls = source.walls + listOf(inside, outside, remote, coincident).associateBy { it.id })
        val dimensions = WallDimensionGeometry.forSelectedWall(document, "left")
        assertEquals(setOf("inside", "outside"), dimensions.map { it.first.entityId }.toSet())
        assertEquals(setOf(800.0, 1800.0), dimensions.map { WallDimensionGeometry.layout(document, it)!!.valueMm }.toSet())
        assertTrue(WallDimensionGeometry.forSelectedWall(document, "missing").isEmpty())
    }

    @Test fun temporaryDriveKeepsExistingAnnotationsAndIsAtomicWithUndoAndInvalidInput() {
        val initial = room()
        val automatic = WallDimensionGeometry.forSelectedWall(initial, "right").single().copy(id = "dim")
        val editor = SketchEditor(initial)
        assertTrue(editor.execute(DriveWallFaceDistanceCommand(automatic, 4200.0)))
        assertEquals(initial.dimensions, editor.document.dimensions)
        assertEquals(4200.0, value(editor.document), 1e-8)
        assertTrue(editor.undo())
        assertEquals(initial, editor.document)
        assertFalse(editor.execute(DriveWallFaceDistanceCommand(automatic, -1.0)))
        assertEquals(initial, editor.document)
        assertTrue(editor.canRedo)
        assertFalse(editor.execute(DriveWallFaceDistanceCommand(automatic, 3800.0)))
        assertTrue(editor.canRedo)
    }

    @Test fun temporaryDimensionTapRequestsInputOnReleaseAndDragOrCancelDoesNotEdit() {
        val initial = room().copy(dimensions = emptyMap())
        val editor = SketchEditor(initial)
        editor.select(Selection.Wall("right"))
        val point = Vec2(2000.0, 1500.0)
        val labelId = WallDimensionGeometry.forSelectedWall(initial, "right").single().id
        fun context(point: Vec2) = PointerContext(point, 20.0, 20.0, 10.0, 10.0, 20.0, 10.0, faceDimensionLabelId = labelId)
        assertNull(editor.pointerDown(point, context(point)).editFaceDimension)
        val requested = assertNotNull(editor.pointerUp(point, context(point)).editFaceDimension)
        assertEquals("right", requested.second.entityId)
        assertEquals(Selection.Wall("right"), editor.state.selection)
        assertEquals(initial, editor.document)
        assertFalse(editor.canUndo)
        editor.pointerDown(point, context(point))
        val dragged = point + Vec2(0.0, 100.0)
        editor.pointerMove(dragged, context(dragged))
        assertNull(editor.pointerUp(dragged, context(dragged)).editFaceDimension)
        assertEquals(initial, editor.document)
        assertFalse(editor.canUndo)
        editor.pointerDown(point, context(point))
        editor.cancelPointer()
        assertNull(editor.pointerUp(point, context(point)).editFaceDimension)
    }

    @Test fun placedDimensionTapRequestsItsOwnInputBeforeTemporaryWallDimensions() {
        val initial = room()
        val editor = SketchEditor(initial)
        editor.select(Selection.Wall("right"))
        val point = Vec2(2000.0, 1500.0)
        val context = PointerContext(point, 20.0, 20.0, 10.0, 10.0, 20.0, 10.0, dimensionLabelId = "dim")
        assertNull(editor.pointerDown(point, context).editFaceDimension)
        val update = editor.pointerUp(point, context)
        assertEquals("dim", assertNotNull(update.editFaceDimension).id)
        assertEquals("right", update.editFaceDimension!!.second.entityId)
        assertEquals(Selection.Wall("right"), editor.state.selection)
        assertEquals(initial, editor.document)
        assertFalse(editor.canUndo)
        editor.pointerDown(point, context)
        editor.cancelPointer()
        assertNull(editor.pointerUp(point, context).editFaceDimension)
        editor.pointerDown(point, context)
        val end = point + Vec2(0.0, 200.0)
        editor.pointerMove(end, context.copy(screenPoint = end))
        assertNull(editor.pointerUp(end, context.copy(screenPoint = end)).editFaceDimension)
        assertEquals(initial, editor.document)
        editor.select(null)
        val lineContext = context.copy(dimensionLabelId = null)
        editor.pointerDown(point, lineContext)
        editor.pointerMove(end, lineContext.copy(screenPoint = end))
        editor.pointerUp(end, lineContext.copy(screenPoint = end))
        assertEquals(initial.walls, editor.document.walls)
        assertEquals(end, editor.document.dimensions.getValue("dim").position)
    }

    @Test fun measuredLabelHitTakesPrecedenceOverNearbyWallEndpoint() {
        val initial = room()
        val editor = SketchEditor(initial)
        editor.select(Selection.Wall("right"))
        val endpoint = initial.walls.getValue("right").start
        val context = PointerContext(endpoint, 20.0, 20.0, 10.0, 10.0, 20.0, 10.0, dimensionLabelId = "dim")
        editor.pointerDown(endpoint, context)
        assertEquals("dim", assertNotNull(editor.pointerUp(endpoint, context).editFaceDimension).id)
        assertEquals(Selection.Wall("right"), editor.state.selection)
        assertEquals(initial, editor.document)
        assertFalse(editor.canUndo)
    }

    @Test fun tappingDimensionLineWithoutNumericLabelDoesNotRequestInput() {
        val initial = room()
        val editor = SketchEditor(initial)
        val point = Vec2(1000.0, 1500.0)
        val context = PointerContext(point, 20.0, 20.0, 10.0, 10.0, 20.0, 10.0)
        editor.pointerDown(point, context)
        val update = editor.pointerUp(point, context)
        assertEquals(Selection.Dimension("dim"), editor.state.selection)
        assertNull(update.editFaceDimension)
        assertEquals(initial, editor.document)
        assertFalse(editor.canUndo)
        editor.restore(initial.copy(dimensions = emptyMap()))
        editor.select(Selection.Wall("right"))
        editor.pointerDown(point, context)
        assertNull(editor.pointerUp(point, context).editFaceDimension)
        assertEquals(initial.walls, editor.document.walls)
        assertFalse(editor.canUndo)
    }

    @Test fun permanentDimensionRequiresPreselectedReferencedWallAndOrientsEitherSide() {
        val original = room()
        val column = RectColumn("column", Vec2(500.0, 500.0), 300.0, 300.0)
        val initial = original.copy(columns = mapOf(column.id to column))
        val dimension = initial.dimensions.getValue("dim")
        val point = Vec2(2000.0, 1500.0)
        val context = PointerContext(point, 20.0, 20.0, 10.0, 10.0, 20.0, 10.0, dimensionLabelId = dimension.id)
        for (selection in listOf(null, Selection.Dimension("dim"), Selection.Column("column"), Selection.Wall("top"))) {
            val editor = SketchEditor(initial)
            editor.select(selection)
            editor.pointerDown(point, context)
            assertNull(editor.pointerUp(point, context).editFaceDimension)
            assertEquals(initial, editor.document)
            assertFalse(editor.canUndo)
        }
        for (moving in listOf("left", "right")) {
            val editor = SketchEditor(initial)
            editor.select(Selection.Wall(moving))
            editor.pointerDown(point, context)
            val request = assertNotNull(editor.pointerUp(point, context).editFaceDimension)
            assertEquals(moving, request.second.entityId)
            assertEquals(Selection.Wall(moving), editor.state.selection)
            assertTrue(editor.execute(DriveWallFaceDistanceCommand(request, 4200.0)))
            assertEquals(initial.dimensions, editor.document.dimensions)
            assertEquals(initial.walls.getValue(request.first.entityId), editor.document.walls.getValue(request.first.entityId))
            assertEquals(4200.0, value(editor.document), 1e-8)
            assertTrue(editor.undo())
            assertEquals(initial, editor.document)
        }
    }
}
