package com.example.boschlaser.shared.annotation

import com.example.boschlaser.shared.geometry.Vec2
import kotlin.test.*

class AnnotationHitTestTest {
    private val size = Vec2(100.0, 100.0)
    private val narrow = AnnotationHitTolerances(2.0, 2.0, 2.0, 2.0, 2.0)
    private fun picker(vararg annotations: Annotation, size: Vec2 = this.size,
        tolerance: AnnotationHitTolerances = narrow) = AnnotationHitTest(
        AnnotationDocument(annotations.associateBy { it.id }), size, tolerance)
    private fun square(id: String) = AreaAnnotation(id, listOf(PhotoPoint(0.0, 0.0), PhotoPoint(1.0, 0.0),
        PhotoPoint(1.0, 1.0), PhotoPoint(0.0, 1.0)), "", -1)

    @Test fun wholeEntityPickingRetainsTextDimensionAngleAreaPriorityIndependentOfMapOrder() {
        val query = PhotoPoint(.5, .5)
        val text = TextAnnotation("text", query, "label", -1)
        val dimension = DimensionAnnotation("dimension", PhotoPoint(.1, .5), PhotoPoint(.9, .5), "", -1)
        val angle = AngleAnnotation("angle", PhotoPoint(.1, .1), PhotoPoint(.9, .9), PhotoPoint(.9, .1), "", -1)
        val area = square("area")
        assertEquals(AnnotationSelectionTarget(text.id), picker(area, angle, dimension, text).pick(query))
        assertEquals(AnnotationSelectionTarget(dimension.id), picker(area, angle, dimension).pick(query))
        assertEquals(AnnotationSelectionTarget(angle.id), picker(area, angle).pick(query))
        assertEquals(AnnotationSelectionTarget(area.id), picker(area).pick(query))
    }

    @Test fun selectedDimensionAndAngleControlsWinBeforeAreaVerticesAndText() {
        val query = PhotoPoint(.2, .2)
        val dimension = DimensionAnnotation("dimension", query, PhotoPoint(.8, .8), "", -1)
        val angle = AngleAnnotation("angle", query, PhotoPoint(.8, .2), PhotoPoint(.2, .8), "", -1)
        val text = TextAnnotation("text", query, "", -1)
        val area = AreaAnnotation("area", listOf(query, PhotoPoint(.9, .1), PhotoPoint(.9, .9)), "", -1)
        val picker = picker(dimension, angle, text, area)
        assertEquals(AnnotationSelectionTarget(dimension.id, 0), picker.pick(query, AnnotationSelectionTarget(dimension.id)))
        assertEquals(AnnotationSelectionTarget(angle.id, 0), picker.pick(query, AnnotationSelectionTarget(angle.id)))
        assertEquals(AnnotationSelectionTarget(area.id, 0), picker.pick(query))
    }

    @Test fun selectedAreaVertexWinsBeforeANewerCloserAreaAndUnselectedAreasUseReverseOrder() {
        val older = AreaAnnotation("older", listOf(PhotoPoint(.1, .1), PhotoPoint(.9, .1), PhotoPoint(.5, .9)), "", -1)
        val newer = older.copy(id = "newer", points = older.points.map { PhotoPoint(it.x + .0005, it.y + .0005) })
        val query = newer.points.first()
        val picker = picker(older, newer)
        assertEquals(AnnotationSelectionTarget(older.id, 0), picker.pick(query, AnnotationSelectionTarget(older.id)))
        assertEquals(AnnotationSelectionTarget(newer.id, 0), picker.pick(query))
        assertEquals(AnnotationSelectionTarget(newer.id, 0), picker.pick(query, AnnotationSelectionTarget("missing")))
    }

    @Test fun nearestSameKindWinsAndExactTiesKeepTheFirstDocumentEntry() {
        val first = TextAnnotation("first", PhotoPoint(.25, .5), "", -1)
        val second = first.copy(id = "second", position = PhotoPoint(.75, .5))
        val tolerance = narrow.copy(textPx = 30.0, linePx = 30.0)
        val query = PhotoPoint(.5, .5)
        assertEquals(AnnotationSelectionTarget(first.id), picker(first, second, tolerance = tolerance).pick(query))
        assertEquals(AnnotationSelectionTarget(second.id), picker(second, first, tolerance = tolerance).pick(query))
        assertEquals(AnnotationSelectionTarget(second.id), picker(first, second, tolerance = tolerance).pick(PhotoPoint(.7, .5)))
        val lower = DimensionAnnotation("lower", PhotoPoint(0.0, .25), PhotoPoint(1.0, .25), "", -1)
        val upper = lower.copy(id = "upper", start = PhotoPoint(0.0, .75), end = PhotoPoint(1.0, .75))
        assertEquals(AnnotationSelectionTarget(lower.id), picker(lower, upper, tolerance = tolerance).pick(query))
    }

    @Test fun screenToleranceUsesBothPhotoAxesAndIncludesItsExactBoundary() {
        val text = TextAnnotation("text", PhotoPoint(0.0, 0.0), "", -1)
        val picker = picker(text, size = Vec2(1000.0, 100.0), tolerance = narrow.copy(textPx = 10.0))
        val selected = AnnotationSelectionTarget(text.id)
        assertEquals(selected, picker.pick(PhotoPoint(.009, 0.0)))
        assertEquals(selected, picker.pick(PhotoPoint(.01, 0.0)))
        assertNull(picker.pick(PhotoPoint(.011, 0.0)))
        assertEquals(selected, picker.pick(PhotoPoint(0.0, .1)))
        assertNull(picker.pick(PhotoPoint(0.0, .11)))
    }

    @Test fun anglesHitTheirTwoFiniteRaysWithoutTreatingTheArcOrInfiniteExtensionsAsLines() {
        val angle = AngleAnnotation("angle", PhotoPoint(.5, .5), PhotoPoint(.9, .5), PhotoPoint(.5, .9), "", -1)
        val picker = picker(angle)
        assertEquals(AnnotationSelectionTarget(angle.id), picker.pick(PhotoPoint(.7, .5)))
        assertEquals(AnnotationSelectionTarget(angle.id), picker.pick(PhotoPoint(.5, .7)))
        assertNull(picker.pick(PhotoPoint(.7, .7)))
        assertNull(picker.pick(PhotoPoint(.95, .5)))
    }

    @Test fun areaEdgesIncludeTheClosingEdgeAndKeepTheFirstEdgeOnATie() {
        val area = AreaAnnotation("area", listOf(PhotoPoint(.2, .2), PhotoPoint(.8, .2), PhotoPoint(.8, .8)), "", -1)
        val picker = picker(area)
        assertEquals(2, picker.areaEdgeAt(area.id, PhotoPoint(.5, .5)))
        assertEquals(0, picker.areaEdgeAt(area.id, area.points.first()))
        assertEquals(AnnotationSelectionTarget(area.id), picker.pick(PhotoPoint(.48, .5)))
        assertNull(picker.areaEdgeAt("missing", PhotoPoint(.5, .5)))
    }

    @Test fun concaveAreaUsesItsInteriorRatherThanItsBoundingBox() {
        val area = AreaAnnotation("concave", listOf(PhotoPoint(0.0, 0.0), PhotoPoint(1.0, 0.0), PhotoPoint(1.0, .3),
            PhotoPoint(.3, .3), PhotoPoint(.3, 1.0), PhotoPoint(0.0, 1.0)), "", -1)
        val picker = picker(area)
        assertEquals(AnnotationSelectionTarget(area.id), picker.pick(PhotoPoint(.2, .8)))
        assertNull(picker.pick(PhotoPoint(.7, .7)))
    }

    @Test fun overlappingAreaBodiesUseTheLatestDocumentEntryWithoutChangingTheDocument() {
        val first = square("first")
        val second = square("second")
        val document = AnnotationDocument(linkedMapOf(first.id to first, second.id to second))
        val picker = AnnotationHitTest(document, size, narrow)
        assertEquals(AnnotationSelectionTarget(second.id), picker.pick(PhotoPoint(.5, .5)))
        assertEquals(listOf(first.id, second.id), document.annotations.keys.toList())
        assertEquals(first, document.annotations[first.id])
        assertEquals(second, document.annotations[second.id])
    }

    @Test fun coincidentEndpointsAndDegenerateAreasDoNotNormalizeZeroVectors() {
        val point = PhotoPoint(.5, .5)
        val dimension = DimensionAnnotation("dimension", point, point, "", -1)
        val angle = AngleAnnotation("angle", point, point, point, "", -1)
        val area = AreaAnnotation("area", List(3) { point }, "", -1)
        assertEquals(AnnotationSelectionTarget(dimension.id), picker(dimension).pick(PhotoPoint(.51, .5)))
        assertEquals(AnnotationSelectionTarget(dimension.id, 0), picker(dimension).pick(point, AnnotationSelectionTarget(dimension.id)))
        assertEquals(AnnotationSelectionTarget(angle.id), picker(angle).pick(PhotoPoint(.51, .5)))
        assertEquals(AnnotationSelectionTarget(area.id, 0), picker(area).pick(point))
        assertEquals(0, picker(area).areaEdgeAt(area.id, point))
    }

    @Test fun pickingUsesOriginalDoubleCoordinatesAndDoesNotClampLegacyOutsidePhotoGeometry() {
        val point = PhotoPoint(.123456789123, .234567891234)
        val text = TextAnnotation("precise", point, "", -1)
        val picker = picker(text, size = Vec2(1e9, 1e9), tolerance = narrow.copy(textPx = .000001))
        assertEquals(AnnotationSelectionTarget(text.id), picker.pick(point))
        assertNull(picker.pick(PhotoPoint(point.x + 1e-12, point.y)))
        val outside = text.copy(id = "outside", position = PhotoPoint(-.2, 1.2))
        assertEquals(AnnotationSelectionTarget(outside.id), picker(outside).pick(outside.position))
        val dimension = DimensionAnnotation("legacy", PhotoPoint(-.5, .5), PhotoPoint(1.5, .5), "", -1)
        assertEquals(AnnotationSelectionTarget(dimension.id), picker(dimension).pick(PhotoPoint(1.2, .5)))
    }

    @Test fun invalidSizesAndTolerancesAreRejectedAndZeroToleranceIsAllowed() {
        for (value in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { narrow.copy(linePx = value) }
            assertFailsWith<IllegalArgumentException> { narrow.copy(textPx = value) }
            assertFailsWith<IllegalArgumentException> { narrow.copy(controlPx = value) }
            assertFailsWith<IllegalArgumentException> { narrow.copy(areaVertexPx = value) }
            assertFailsWith<IllegalArgumentException> { narrow.copy(areaEdgePx = value) }
        }
        for (size in listOf(Vec2(0.0, 100.0), Vec2(100.0, 0.0), Vec2(-1.0, 100.0))) {
            assertFailsWith<IllegalArgumentException> { AnnotationHitTest(AnnotationDocument(), size, narrow) }
        }
        val text = TextAnnotation("zero", PhotoPoint(.5, .5), "", -1)
        assertEquals(AnnotationSelectionTarget(text.id), picker(text,
            tolerance = AnnotationHitTolerances(0.0, 0.0, 0.0, 0.0, 0.0)).pick(text.position))
    }
}
