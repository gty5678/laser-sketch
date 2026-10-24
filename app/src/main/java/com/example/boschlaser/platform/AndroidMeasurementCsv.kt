package com.example.boschlaser.platform

import android.content.Context
import android.net.Uri
import com.example.boschlaser.R
import com.example.boschlaser.shared.bosch.MeasurementOperation
import com.example.boschlaser.shared.measurement.MeasurementCsv
import com.example.boschlaser.shared.measurement.MeasurementRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Only the application Context is retained; provider streams and formatting stay on IO. */
class AndroidMeasurementCsv(context: Context) {
    private val application = context.applicationContext

    suspend fun export(uri: Uri, records: List<MeasurementRecord>) = withContext(Dispatchers.IO) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val stream = checkNotNull(application.contentResolver.openOutputStream(uri))
        stream.bufferedWriter(Charsets.UTF_8).use { writer ->
            MeasurementCsv.write(records, application.resources.getStringArray(R.array.measurement_csv_headers).toList(),
                time = { time.format(Date(it)) },
                operation = { record -> record.legacyOperation ?: application.getString(when (record.measurement.operation) {
                    MeasurementOperation.ADD -> R.string.measurement_operation_add
                    MeasurementOperation.SUBTRACT -> R.string.measurement_operation_subtract
                    null -> R.string.measurement_csv_single
                }) },
                decimal = { value, places -> String.format(Locale.US, "%.$places" + "f", value) },
                append = writer::write)
        }
    }
}
