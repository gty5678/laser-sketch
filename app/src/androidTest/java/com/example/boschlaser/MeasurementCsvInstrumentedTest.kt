package com.example.boschlaser

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.boschlaser.platform.AndroidMeasurementCsv
import com.example.boschlaser.shared.measurement.Measurement
import com.example.boschlaser.shared.measurement.MeasurementRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MeasurementCsvInstrumentedTest {
    @Test fun providerExportPreservesDoublePrecisionAndClosesTheStream() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "shared").apply { check(isDirectory || mkdirs()) }
        val output = File(directory, "csv-fixture-${UUID.randomUUID()}.csv")
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", output)
            AndroidMeasurementCsv(context).export(uri, listOf(MeasurementRecord(
                Measurement("fixture", 16777217.25, 1, accumulatedMm = -16777217.75), byteArrayOf(0, -1), "Fixture, type")))
            val csv = output.readText(Charsets.UTF_8)
            assertTrue(csv.startsWith("序号,时间,类型,本次毫米,本次米,累计毫米,原始报文\n"))
            assertTrue(csv.contains(",\"Fixture, type\",16777217.3,16777.2173,-16777217.8,00 ff\n"))
            AndroidMeasurementCsv(context).export(uri, emptyList())
            assertEquals(1, output.readLines().size)
        } finally { output.delete() }
    }
}
