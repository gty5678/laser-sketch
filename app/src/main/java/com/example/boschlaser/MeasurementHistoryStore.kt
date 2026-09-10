package com.example.boschlaser

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Durable storage for every distance received from the laser meter. */
object MeasurementHistoryStore {
    private const val DATABASE_NAME = "measurement-history.db"
    private const val DATABASE_VERSION = 1
    private const val TABLE = "measurements"

    @Volatile
    private var helper: DatabaseHelper? = null

    private fun database(context: Context): SQLiteDatabase {
        val current = helper ?: synchronized(this) {
            helper ?: DatabaseHelper(context.applicationContext).also { helper = it }
        }
        return current.writableDatabase
    }

    fun load(context: Context): List<Measurement> {
        val values = mutableListOf<Measurement>()
        database(context).query(
            TABLE,
            arrayOf("timestamp", "meters", "operation", "accumulated_meters", "raw"),
            null,
            null,
            null,
            null,
            "timestamp ASC",
        ).use { cursor ->
            val timestampIndex = cursor.getColumnIndexOrThrow("timestamp")
            val metersIndex = cursor.getColumnIndexOrThrow("meters")
            val operationIndex = cursor.getColumnIndexOrThrow("operation")
            val accumulatedIndex = cursor.getColumnIndexOrThrow("accumulated_meters")
            val rawIndex = cursor.getColumnIndexOrThrow("raw")
            while (cursor.moveToNext()) {
                values += Measurement(
                    timestamp = cursor.getLong(timestampIndex),
                    meters = cursor.getFloat(metersIndex),
                    operation = if (cursor.isNull(operationIndex)) null else cursor.getString(operationIndex),
                    accumulatedMeters = if (cursor.isNull(accumulatedIndex)) null else cursor.getFloat(accumulatedIndex),
                    raw = cursor.getBlob(rawIndex) ?: byteArrayOf(),
                )
            }
        }
        return values
    }

    fun add(context: Context, item: Measurement) {
        val values = ContentValues().apply {
            put("timestamp", item.timestamp)
            put("meters", item.meters)
            put("operation", item.operation)
            item.accumulatedMeters?.let { put("accumulated_meters", it) } ?: putNull("accumulated_meters")
            put("raw", item.raw)
        }
        database(context).insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun delete(context: Context, timestamp: Long) {
        database(context).delete(TABLE, "timestamp = ?", arrayOf(timestamp.toString()))
    }

    fun clear(context: Context) {
        database(context).delete(TABLE, null, null)
    }

    private class DatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    timestamp INTEGER PRIMARY KEY,
                    meters REAL NOT NULL,
                    operation TEXT,
                    accumulated_meters REAL,
                    raw BLOB NOT NULL
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
