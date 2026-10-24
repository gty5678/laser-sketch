package com.example.boschlaser.platform

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.boschlaser.shared.bosch.BoschProtocol
import com.example.boschlaser.shared.bosch.MeasurementOperation
import com.example.boschlaser.shared.measurement.Measurement
import com.example.boschlaser.shared.measurement.MeasurementRecord
import com.example.boschlaser.shared.measurement.MeasurementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Version 2 adds ID-based records; the version 1 table is retained as the migration source. */
class AndroidMeasurementRepository(context: Context, databaseName: String = "measurement-history.db") : MeasurementRepository {
    private val helper = DatabaseHelper(context.applicationContext, databaseName)
    private val mutex = Mutex()
    private val mutableRecords = MutableStateFlow<List<MeasurementRecord>>(emptyList())
    override val records = mutableRecords.asStateFlow()
    override suspend fun refresh(): Unit = mutex.withLock {
        withContext(Dispatchers.IO) { mutableRecords.value = read(helper.writableDatabase) }
    }
    override suspend fun save(record: MeasurementRecord): Unit = write { db ->
        val item = record.measurement
        val values = ContentValues().apply {
            put("id", item.id); put("value_mm", item.valueMm); put("timestamp", item.timestamp)
            put("device_id", item.deviceId); put("operation_name", item.operation?.name)
            item.accumulatedMm?.let { put("accumulated_mm", it) } ?: putNull("accumulated_mm")
            put("raw", record.raw); put("legacy_operation", record.legacyOperation)
        }
        if (db.update(TABLE, values, "id = ?", arrayOf(item.id)) == 0) db.insertOrThrow(TABLE, null, values)
    }
    override suspend fun delete(id: String): Unit = write { db ->
        db.delete(TABLE, "id = ?", arrayOf(id))
        val legacyTimestamp = id.removePrefix("legacy-").toLongOrNull()
        if (legacyTimestamp != null && id == "legacy-$legacyTimestamp") {
            db.delete("measurements", "timestamp = ?", arrayOf(legacyTimestamp.toString()))
        }
    }
    override suspend fun clear(): Unit = write { db ->
        db.delete(TABLE, null, null)
        db.delete("measurements", null, null)
    }
    private suspend fun write(action: (SQLiteDatabase) -> Unit): Unit = mutex.withLock {
        withContext(Dispatchers.IO) {
            val db = helper.writableDatabase
            val updated: List<MeasurementRecord>
            db.beginTransaction()
            try {
                action(db)
                updated = read(db)
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            mutableRecords.value = updated
        }
    }
    suspend fun close(): Unit = mutex.withLock { withContext(Dispatchers.IO) { helper.close() } }
    private fun read(db: SQLiteDatabase): List<MeasurementRecord> = buildList {
        db.query(TABLE, null, null, null, null, null, "timestamp ASC, rowid ASC").use { cursor ->
            fun text(name: String): String? = cursor.getColumnIndexOrThrow(name).let {
                if (cursor.isNull(it)) null else cursor.getString(it)
            }
            while (cursor.moveToNext()) {
                val raw = cursor.getBlob(cursor.getColumnIndexOrThrow("raw")) ?: byteArrayOf()
                val operation = text("operation_name")?.let { MeasurementOperation.valueOf(it) }
                    ?: BoschProtocol.parseMeasurement(raw)?.operation
                val accumulatedIndex = cursor.getColumnIndexOrThrow("accumulated_mm")
                add(MeasurementRecord(Measurement(
                    cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    cursor.getDouble(cursor.getColumnIndexOrThrow("value_mm")),
                    cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                    text("device_id"), operation,
                    if (cursor.isNull(accumulatedIndex)) null else cursor.getDouble(accumulatedIndex),
                ), raw, text("legacy_operation")))
            }
        }
    }
    private class DatabaseHelper(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 2) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE measurements (
                timestamp INTEGER PRIMARY KEY, meters REAL NOT NULL, operation TEXT,
                accumulated_meters REAL, raw BLOB NOT NULL)""")
            createRecords(db)
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                createRecords(db)
                db.execSQL("""INSERT INTO $TABLE
                    (id, value_mm, timestamp, device_id, operation_name, accumulated_mm, raw, legacy_operation)
                    SELECT 'legacy-' || timestamp, meters * 1000.0, timestamp, NULL, NULL,
                        accumulated_meters * 1000.0, raw, operation FROM measurements""")
            }
        }
        private fun createRecords(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE $TABLE (
                id TEXT PRIMARY KEY NOT NULL, value_mm REAL NOT NULL, timestamp INTEGER NOT NULL,
                device_id TEXT, operation_name TEXT, accumulated_mm REAL, raw BLOB NOT NULL,
                legacy_operation TEXT)""")
            db.execSQL("CREATE INDEX measurement_records_timestamp ON $TABLE(timestamp)")
        }
    }
    private companion object { const val TABLE = "measurement_records" }
}
