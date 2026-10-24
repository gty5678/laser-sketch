package com.example.boschlaser

import org.junit.Assert.*
import org.junit.Test

class AnnotationCoreAdapterTest {
    @Test fun legacyFloatCoordinatesStylesAndTextRoundTripWithoutDrift() {
        val state = AnnotationState(
            dimensions = listOf(DimensionMark(.1234567f, .25f, .75f, .8765432f, "1234.5 mm", -123, 2)),
            texts = listOf(TextMark(.4f, .5f, "Test text", -456, 0, false)),
            angles = listOf(AngleMark(.5f, .5f, .2f, .2f, .8f, .2f, "Test angle", -789)),
            areas = listOf(AreaMark(listOf(AreaPoint(.1f, .2f), AreaPoint(.8f, .2f), AreaPoint(.4f, .9f)), "Test area", -321)),
        )
        val document = state.toAnnotationDocument("photo")
        assertEquals("photo", document.photoAssetId)
        assertEquals(listOf("dimension-0", "text-0", "angle-0", "area-0"), document.annotations.keys.toList())
        assertEquals(state, document.toAnnotationState())
        assertEquals(document, state.toAnnotationDocument("photo"))
    }
    @Test fun invalidLegacyCoordinatesAndIncompleteAreasAreSkipped() {
        val valid = DimensionMark(.1f, .2f, .3f, .4f, "", -1, 1)
        val state = AnnotationState(
            dimensions = listOf(valid, valid.copy(startX = Float.POSITIVE_INFINITY)),
            areas = listOf(AreaMark(listOf(AreaPoint(.1f, .1f)), "", -1)),
        )
        val restored = state.toAnnotationDocument().toAnnotationState()
        assertEquals(listOf(valid), restored.dimensions)
        assertTrue(restored.areas.isEmpty())
    }
}
