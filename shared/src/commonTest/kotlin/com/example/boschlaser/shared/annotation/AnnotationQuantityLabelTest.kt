package com.example.boschlaser.shared.annotation

import kotlin.test.*

class AnnotationQuantityLabelTest {
    @Test fun unitConversionPreservesPhysicalQuantityAndRejectsInvalidNumbers() {
        val distance = listOf("mm", "m", "cm")
        assertEquals("1", AnnotationQuantityLabel.convert("1000", 0, 1, distance))
        assertEquals("1250", AnnotationQuantityLabel.convert("1.25", 1, 0, distance))
        assertEquals("-12.5", AnnotationQuantityLabel.convert("-125", 0, 2, distance))
        assertEquals("10000", AnnotationQuantityLabel.convert("1", 0, 1, listOf("m²", "cm²", "mm²")))
        assertEquals("", AnnotationQuantityLabel.convert("", 0, 1, distance))
        for (value in listOf("label", "NaN", "Infinity", "1e309")) assertNull(AnnotationQuantityLabel.convert(value, 0, 1, distance))
        assertNull(AnnotationQuantityLabel.convert("1e308", 1, 0, distance))
        assertNull(AnnotationQuantityLabel.convert("1", -1, 0, distance))
    }
    @Test fun decimalUnitChangesDoNotExposeBinaryRoundingAndRoundTripExactly() {
        val units = listOf("mm", "cm", "m")
        assertEquals("12.575", AnnotationQuantityLabel.convert("125.75", 0, 1, units))
        assertEquals("125.75", AnnotationQuantityLabel.convert("12.575", 1, 0, units))
        assertEquals("0.00000125", AnnotationQuantityLabel.convert("1.25e-3", 0, 2, units))
        assertEquals("-1250", AnnotationQuantityLabel.convert("-1.25", 2, 0, units))
        assertEquals("0", AnnotationQuantityLabel.convert("-0.0", 2, 0, units))
        assertEquals("2575", AnnotationQuantityLabel.convert("25.75", 0, 1, listOf("cm²", "mm²")))
    }
    private val distance = listOf("mm", "m", "cm")
    @Test fun parsingPreservesLegacyDisplayWithoutChangingTheStoredLabel() {
        assertEquals(AnnotationQuantityLabel.Draft("12.5", 2), AnnotationQuantityLabel.parse(" 12.5 cm ", distance))
        assertEquals(AnnotationQuantityLabel.Draft("Original", 0), AnnotationQuantityLabel.parse("Original", distance))
        assertEquals(AnnotationQuantityLabel.Draft("12.5", 0), AnnotationQuantityLabel.parse("12.5 unknown", distance))
        assertEquals(AnnotationQuantityLabel.Draft("12.5", 0), AnnotationQuantityLabel.parse("12.5  cm", distance))
        assertEquals(AnnotationQuantityLabel.Draft("", 0), AnnotationQuantityLabel.parse("", distance))
    }
    @Test fun formattingChangesTheTextUnitSuffixWithoutNumericConversion() {
        assertEquals("12.50 cm", AnnotationQuantityLabel.format(" 12.50 ", 2, distance))
        assertEquals("", AnnotationQuantityLabel.format("  ", 1, distance))
        assertEquals("12.5 mm²", AnnotationQuantityLabel.format("12.5", 2, listOf("m²", "cm²", "mm²")))
        assertFailsWith<IllegalArgumentException> { AnnotationQuantityLabel.format("12", 3, distance) }
        assertFailsWith<IllegalArgumentException> { AnnotationQuantityLabel.parse("12", emptyList()) }
    }
}
