package com.example.boschlaser.shared.annotation

import kotlin.test.*

class AnnotationQuantityLabelTest {
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
