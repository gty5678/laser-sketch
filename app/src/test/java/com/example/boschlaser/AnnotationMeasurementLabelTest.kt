package com.example.boschlaser

import org.junit.Assert.assertEquals
import org.junit.Test

class AnnotationMeasurementLabelTest {
    @Test fun preservesOneDecimalBeyondFloatPrecision() {
        assertEquals("16777217.1", formatAnnotationMillimeters(16777217.1))
        assertEquals("123456.8", formatAnnotationMillimeters(123456.75))
    }

    @Test fun retainsExistingPositiveDisplayRoundingWithoutIntegerOverflow() {
        assertEquals("1200", formatAnnotationMillimeters(1200.0))
        assertEquals("1200", formatAnnotationMillimeters(1200.04))
        assertEquals("1200.1", formatAnnotationMillimeters(1200.06))
        assertEquals("3000000000", formatAnnotationMillimeters(3000000000.0))
    }

    @Test fun negativeAccumulatedValuesKeepTheirFraction() {
        assertEquals("0", formatAnnotationMillimeters(-0.04))
        assertEquals("0", formatAnnotationMillimeters(-0.0))
        assertEquals("-1200.6", formatAnnotationMillimeters(-1200.6))
        assertEquals("-1200", formatAnnotationMillimeters(-1200.04))
    }
}
