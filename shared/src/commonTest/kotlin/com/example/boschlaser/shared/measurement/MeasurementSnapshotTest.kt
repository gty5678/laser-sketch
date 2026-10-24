package com.example.boschlaser.shared.measurement

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeasurementSnapshotTest {
    @Test fun initialHistoryIsNotAnAcquisitionButFirstRecordAfterEmptySnapshotIs() {
        assertFalse(isNewestMeasurementAdded(null, listOf("old")))
        assertTrue(isNewestMeasurementAdded(emptyList(), listOf("first")))
    }

    @Test fun appendingOneRecordPreservesEarlierIdentityOrder() {
        assertTrue(isNewestMeasurementAdded(listOf("second", "first"), listOf("third", "second", "first")))
        assertFalse(isNewestMeasurementAdded(listOf("second", "first"), listOf("third", "first", "second")))
    }

    @Test fun boundedSnapshotAcceptsOneNewRecordAndDropsOnlyOldest() {
        assertTrue(isNewestMeasurementAdded(listOf("second", "first"), listOf("third", "second")))
        assertTrue(isNewestMeasurementAdded(listOf("first"), listOf("second")))
    }

    @Test fun unchangedIdsAreNeverNewEvenForSingleRecordOrEqualValues() {
        assertFalse(isNewestMeasurementAdded(listOf("first"), listOf("first")))
        assertFalse(isNewestMeasurementAdded(listOf("second", "first"), listOf("second", "first")))
    }

    @Test fun deleteClearAndHistoryReplacementDoNotApplyValuesAutomatically() {
        assertFalse(isNewestMeasurementAdded(listOf("second", "first"), listOf("first")))
        assertFalse(isNewestMeasurementAdded(listOf("first"), emptyList()))
        assertFalse(isNewestMeasurementAdded(listOf("second", "first"), listOf("first", "second")))
        assertFalse(isNewestMeasurementAdded(listOf("second", "first"), listOf("replacement", "other")))
        assertFalse(isNewestMeasurementAdded(listOf("first"), listOf("third", "second", "first")))
    }
}
