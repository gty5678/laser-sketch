package com.example.boschlaser.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SketchPropertySheetTest {
    @Test fun startsOnlyAfterStrictlyVerticalDownwardSlop() {
        assertFalse(sketchPanelDragStarts(0f, 8f, 8f))
        assertFalse(sketchPanelDragStarts(20f, 20f, 8f))
        assertFalse(sketchPanelDragStarts(-30f, 20f, 8f))
        assertFalse(sketchPanelDragStarts(0f, -30f, 8f))
        assertTrue(sketchPanelDragStarts(-10f, 20f, 8f))
    }

    @Test fun opacityDependsOnHeightAndCannotDropBelowTheOriginalFloor() {
        assertEquals(1f, sketchPanelDragAlpha(-10f, 100f))
        assertEquals(.5f, sketchPanelDragAlpha(50f, 100f), .00001f)
        assertEquals(.5f, sketchPanelDragAlpha(100f, 200f), .00001f)
        assertEquals(.35f, sketchPanelDragAlpha(1000f, 100f), .00001f)
        assertEquals(1f, sketchPanelDragAlpha(0f, 0f))
        assertEquals(.35f, sketchPanelDragAlpha(10f, 0f), .00001f)
    }

    @Test fun minimumAndProportionalDismissalThresholdsArePreserved() {
        assertEquals(48f, sketchPanelDismissDistance(100f, 48f))
        assertEquals(90f, sketchPanelDismissDistance(500f, 48f), .00001f)
        assertEquals(48f, sketchPanelDismissDistance(0f, 48f))
    }
}
