package com.example.boschlaser.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class AnnotationPropertySheetTest {
    @Test fun draggingOnlyDownwardFadesProportionallyToThePanelHeight() {
        assertEquals(1f, propertySheetDragAlpha(-20f, 100f))
        assertEquals(1f, propertySheetDragAlpha(0f, 100f))
        assertEquals(0.725f, propertySheetDragAlpha(50f, 100f), 0.00001f)
        assertEquals(0.725f, propertySheetDragAlpha(100f, 200f), 0.00001f)
    }

    @Test fun opacityHasAFloorAndZeroHeightDoesNotProduceNonFiniteAlpha() {
        assertEquals(0.45f, propertySheetDragAlpha(100f, 100f), 0.00001f)
        assertEquals(0.45f, propertySheetDragAlpha(1000f, 100f), 0.00001f)
        assertEquals(1f, propertySheetDragAlpha(0f, 0f))
        assertEquals(0.45f, propertySheetDragAlpha(10f, 0f), 0.00001f)
    }
}
