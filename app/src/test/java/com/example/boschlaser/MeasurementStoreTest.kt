package com.example.boschlaser

import org.junit.Assert.assertEquals
import org.junit.Test

class MeasurementStoreTest {
    @Test
    fun listenerReceivesNewestMeasurementsImmediately() {
        MeasurementStore.clear()
        val snapshots = mutableListOf<List<Float>>()
        val listener: (List<Float>) -> Unit = { snapshots += it }

        try {
            MeasurementStore.addListener(listener)
            MeasurementStore.add(1200f)
            MeasurementStore.add(2350.5f)

            assertEquals(listOf(2350.5f, 1200f), snapshots.last())
        } finally {
            MeasurementStore.removeListener(listener)
            MeasurementStore.clear()
        }
    }
}
