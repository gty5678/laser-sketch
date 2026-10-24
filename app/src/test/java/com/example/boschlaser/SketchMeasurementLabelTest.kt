package com.example.boschlaser

import org.junit.Assert.assertEquals
import org.junit.Test

class SketchMeasurementLabelTest {
    @Test fun wholeMillimeterDisplayKeepsPositiveAndNegativeTieRules() {
        assertEquals("150", formatSketchMillimeters(149.5))
        assertEquals("149", formatSketchMillimeters(149.4999))
        assertEquals("-149", formatSketchMillimeters(-149.5))
        assertEquals("-150", formatSketchMillimeters(-149.5001))
        assertEquals("0", formatSketchMillimeters(-0.0))
        assertEquals("0", formatSketchMillimeters(-0.5))
    }

    @Test fun fractionalDoubleInputDoesNotRoundThroughFloat() {
        assertEquals("16777217", formatSketchMillimeters(16777216.5))
        assertEquals("16777216", formatSketchMillimeters(16777216.4999))
    }

    @Test fun largeMillimeterValuesDoNotSaturateOrMoveAlreadyIntegralValues() {
        assertEquals("2147483649", formatSketchMillimeters(2147483648.5))
        assertEquals("-2147483648", formatSketchMillimeters(-2147483648.5))
        assertEquals("4503599627370497", formatSketchMillimeters(4503599627370497.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteValuesCannotBecomeVisibleDistanceLabels() {
        formatSketchMillimeters(Double.NaN)
    }
}
