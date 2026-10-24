package com.example.boschlaser.shared.measurement

/** Platform supplies localization and decimal formatting; records never pass through Float. */
object MeasurementCsv {
    fun write(
        records: List<MeasurementRecord>,
        headers: List<String>,
        time: (Long) -> String,
        operation: (MeasurementRecord) -> String,
        decimal: (Double, Int) -> String,
        append: (String) -> Unit,
    ) {
        require(headers.size == 7)
        fun row(cells: List<String>) = append(cells.joinToString(",", transform = ::escape) + "\n")
        row(headers)
        records.forEachIndexed { index, record ->
            val item = record.measurement
            row(listOf(
                (index + 1).toString(), time(item.timestamp), operation(record),
                decimal(item.valueMm, 1), decimal(item.valueMm / 1000.0, 4),
                item.accumulatedMm?.let { decimal(it, 1) }.orEmpty(),
                record.raw.joinToString(" ") { byte -> byte.toUByte().toString(16).padStart(2, '0') },
            ))
        }
    }

    private fun escape(value: String): String = if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else value
}
