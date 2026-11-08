package com.example.boschlaser.ui

import kotlin.test.*

class IosNumberFormattingTest {
    @Test fun measurementDisplayRetainsDoublePrecisionWithoutIntTruncationOrScientificNotation() {
        assertEquals("16777217.1", decimal(16777217.1))
        assertEquals("-16777217.1", decimal(-16777217.1))
        assertEquals("100000000000000000000", decimal(1e20))
        assertEquals("1000", decimal(1000.0))
    }
    @Test fun csvHonorsTheSharedRequestedDecimalPlaces() {
        assertEquals("1.3", fixedDecimal(1.25, 1))
        assertEquals("1.2500", fixedDecimal(1.25, 4))
        assertEquals("0.0013", fixedDecimal(.00125, 4))
    }
}
