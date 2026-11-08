package com.example.boschlaser.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CustomColorTest {
    @Test fun parsesRgbWithOpaqueAlphaAndRoundTripsBoundaryColors() {
        for (color in listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF123ABC.toInt())) {
            assertEquals(color, parseCustomColor(customColorHex(color)))
        }
        assertEquals(0xFF123ABC.toInt(), parseCustomColor("#123abc"))
        assertEquals("00000F", customColorHex(0xFF00000F.toInt()))
    }

    @Test fun rejectsIncompleteInvalidAndAlphaCodes() {
        for (value in listOf("", "123", "#", "12345G", "FF123456", " 123456", "1234567")) {
            assertNull(parseCustomColor(value), value)
        }
    }
    @Test fun wheelRoundTripsExistingColorsAndBrightnessIncludesBlackAndWhite() {
        for (color in listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF123ABC.toInt(),
            0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())) {
            assertEquals(color, wheelColor(color).argb())
        }
        assertEquals(0xFFFFFFFF.toInt(), wheelPosition(0f, 0f, 100f, 1f).argb())
        assertEquals(0xFF000000.toInt(), WheelColor(120f, 1f, 0f).argb())
    }

    @Test fun diskCoordinatesMatchHueAndClampDraggingAtTheEdge() {
        assertEquals(0xFFFF0000.toInt(), wheelPosition(100f, 0f, 100f, 1f).argb())
        assertEquals(90f, wheelPosition(0f, 100f, 100f, 1f).hue, .001f)
        assertEquals(270f, wheelPosition(0f, -100f, 100f, 1f).hue, .001f)
        assertEquals(1f, wheelPosition(500f, 0f, 100f, 1f).saturation)
        assertEquals(.5f, wheelPosition(50f, 0f, 100f, 1f).saturation, .001f)
        assertEquals(0f, wheelPosition(0f, 0f, 0f, 1f).saturation)
    }

}
