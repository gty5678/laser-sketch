package com.example.boschlaser

import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.boschlaser.platform.AndroidMeasurementRepository
import com.example.boschlaser.shared.bosch.MeasurementOperation
import com.example.boschlaser.shared.measurement.Measurement as CoreMeasurement
import com.example.boschlaser.shared.measurement.MeasurementRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AndroidMeasurementRepositoryInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun name() = "measurement-test-${UUID.randomUUID()}.db"
    @Test fun versionOneMigrationPreservesValuesRawLabelsAndSourceTable() = runBlocking {
        val name = name()
        createVersionOne(name)
        val repository = AndroidMeasurementRepository(context, name)
        try {
            repository.refresh()
            val record = repository.records.value.single()
            assertEquals("legacy-100", record.measurement.id)
            assertEquals(2.35f.toDouble() * 1000.0, record.measurement.valueMm, 1e-9)
            assertEquals(-1500.0, record.measurement.accumulatedMm!!, 1e-9)
            assertEquals("legacy-operation-test", record.legacyOperation)
            assertContentEquals(byteArrayOf(1, 2, 3), record.raw)
            assertEquals(2.35f, record.toLegacyMeasurement(context).meters)
            assertEquals("legacy-operation-test", record.toLegacyMeasurement(context).operation)
            context.openOrCreateDatabase(name, 0, null).use { db ->
                assertEquals(2, db.version)
                db.rawQuery("SELECT COUNT(*) FROM measurements", null).use { cursor ->
                    cursor.moveToFirst(); assertEquals(1, cursor.getInt(0))
                }
            }
        } finally { repository.close(); context.deleteDatabase(name) }
    }
    @Test fun sameTimestampIdsSurviveReopenAndDeletingOnePreservesTheOther() = runBlocking {
        val name = name()
        val first = MeasurementRecord(CoreMeasurement("first-test", 1234.56789, 1, "test-device", MeasurementOperation.SUBTRACT, -1500.0), byteArrayOf(4, 5))
        val second = MeasurementRecord(CoreMeasurement("second-test", 2000.0, 1))
        val repository = AndroidMeasurementRepository(context, name)
        try {
            repository.save(first); repository.save(second)
            repository.save(first)
            assertEquals(listOf("first-test", "second-test"), repository.records.value.map { it.measurement.id })
            repository.close()
            val reopened = AndroidMeasurementRepository(context, name)
            try {
                reopened.refresh()
                assertEquals(first, reopened.records.value.first())
                assertEquals(second, reopened.records.value.last())
                reopened.delete(first.measurement.id)
                assertEquals(listOf(second), reopened.records.value)
                reopened.clear()
                assertTrue(reopened.records.value.isEmpty())
            } finally { reopened.close() }
        } finally { repository.close(); context.deleteDatabase(name) }
    }
    @Test fun failedInsertRollsBackAndDoesNotPublishUncommittedState() = runBlocking {
        val name = name()
        val repository = AndroidMeasurementRepository(context, name)
        try {
            val first = MeasurementRecord(CoreMeasurement("first-test", 1000.0, 1))
            repository.save(first)
            context.openOrCreateDatabase(name, 0, null).use { db ->
                db.execSQL("""CREATE TRIGGER block_test_insert BEFORE INSERT ON measurement_records
                    WHEN NEW.id = 'blocked-test' BEGIN SELECT RAISE(ABORT, 'test failure'); END""")
            }
            try {
                repository.save(MeasurementRecord(CoreMeasurement("blocked-test", 2000.0, 2)))
                fail("Expected failed transaction")
            } catch (_: SQLiteConstraintException) { }
            assertEquals(listOf(first), repository.records.value)
            repository.refresh()
            assertEquals(listOf(first), repository.records.value)
        } finally { repository.close(); context.deleteDatabase(name) }
    }
    @Test fun deletingMigratedRecordAndClearingHistoryAlsoRemoveLegacySourceRows() = runBlocking {
        val name = name()
        createVersionOne(name)
        val repository = AndroidMeasurementRepository(context, name)
        try {
            repository.refresh(); repository.delete("legacy-100")
            context.openOrCreateDatabase(name, 0, null).use { db ->
                db.rawQuery("SELECT COUNT(*) FROM measurements", null).use { cursor ->
                    cursor.moveToFirst(); assertEquals(0, cursor.getInt(0))
                }
                db.insertOrThrow("measurements", null, legacyValues())
            }
            repository.save(MeasurementRecord(CoreMeasurement("new-test", 1000.0, 200)))
            repository.clear()
            context.openOrCreateDatabase(name, 0, null).use { db ->
                db.rawQuery("SELECT COUNT(*) FROM measurements", null).use { cursor ->
                    cursor.moveToFirst(); assertEquals(0, cursor.getInt(0))
                }
            }
            repository.refresh()
            assertTrue(repository.records.value.isEmpty())
        } finally { repository.close(); context.deleteDatabase(name) }
    }
    @Test fun failedUpgradeRollsBackNewTableAndKeepsOriginalDatabaseVersion() = runBlocking {
        val name = name()
        context.openOrCreateDatabase(name, 0, null).use { db ->
            // A damaged version 1 fixture lacks the accumulated column; migration must be atomic.
            db.execSQL("""CREATE TABLE measurements (timestamp INTEGER PRIMARY KEY, meters REAL NOT NULL,
                operation TEXT, raw BLOB NOT NULL)""")
            db.insertOrThrow("measurements", null, ContentValues().apply {
                put("timestamp", 100L); put("meters", 1.25f); put("raw", byteArrayOf(1, 2, 3))
            })
            db.version = 1
        }
        val repository = AndroidMeasurementRepository(context, name)
        try {
            try { repository.refresh(); fail("Expected failed upgrade") } catch (_: SQLiteException) { }
            assertTrue(repository.records.value.isEmpty())
            context.openOrCreateDatabase(name, 0, null).use { db ->
                assertEquals(1, db.version)
                db.rawQuery("SELECT meters, raw FROM measurements", null).use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(1.25f, cursor.getFloat(0))
                    assertArrayEquals(byteArrayOf(1, 2, 3), cursor.getBlob(1))
                }
                db.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE name = 'measurement_records'", null).use { cursor ->
                    cursor.moveToFirst(); assertEquals(0, cursor.getInt(0))
                }
            }
        } finally { repository.close(); context.deleteDatabase(name) }
    }
    private fun createVersionOne(name: String) {
        context.openOrCreateDatabase(name, 0, null).use { db ->
            db.execSQL("""CREATE TABLE measurements (timestamp INTEGER PRIMARY KEY, meters REAL NOT NULL,
                operation TEXT, accumulated_meters REAL, raw BLOB NOT NULL)""")
            db.insertOrThrow("measurements", null, legacyValues())
            db.version = 1
        }
    }
    private fun legacyValues() = ContentValues().apply {
        put("timestamp", 100L); put("meters", 2.35f); put("operation", "legacy-operation-test")
        put("accumulated_meters", -1.5f); put("raw", byteArrayOf(1, 2, 3))
    }
    private fun assertContentEquals(expected: ByteArray, actual: ByteArray) { assertArrayEquals(expected, actual) }
}
