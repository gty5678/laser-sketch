package com.example.boschlaser.shared.measurement

import kotlin.test.*

class MeasurementCsvTest {
    private val headers = List(7) { "column$it" }

    @Test fun numbersReachFormatterAsDoubleAndHistoryIsNotPaged() {
        val records = List(205) { index -> MeasurementRecord(Measurement("record-$index", 16777217.25, 1,
            accumulatedMm = -16777217.75), byteArrayOf(0, 127, -1)) }
        val numbers = mutableListOf<Pair<Double, Int>>()
        val lines = mutableListOf<String>()
        MeasurementCsv.write(records, headers, { "time" }, { "distance" },
            { value, places -> numbers += value to places; value.toString() }, lines::add)
        assertEquals(206, lines.size)
        assertEquals(listOf(16777217.25 to 1, 16777.21725 to 4, -16777217.75 to 1), numbers.take(3))
        assertEquals("205,time,distance,1.677721725E7,16777.21725,-1.677721775E7,00 7f ff\n", lines.last())
    }

    @Test fun quotesCommasAndLineBreaksAreEscapedWithoutLosingText() {
        val lines = mutableListOf<String>()
        val record = MeasurementRecord(Measurement("fixture", 12.5, 1), legacyOperation = "a,\"b\"\r\nc")
        MeasurementCsv.write(listOf(record), headers, { "time" }, { it.legacyOperation!! },
            { value, _ -> value.toString() }, lines::add)
        assertEquals("1,time,\"a,\"\"b\"\"\r\nc\",12.5,0.0125,,\n", lines.last())
    }

    @Test fun emptyHistoryStillHasTheCompatibleSevenColumnHeader() {
        val lines = mutableListOf<String>()
        MeasurementCsv.write(emptyList(), headers, { error("No records") }, { error("No records") },
            { _, _ -> error("No records") }, lines::add)
        assertEquals(listOf(headers.joinToString(",") + "\n"), lines)
        assertFailsWith<IllegalArgumentException> {
            MeasurementCsv.write(emptyList(), emptyList(), { "" }, { "" }, { _, _ -> "" }, lines::add)
        }
    }
}
