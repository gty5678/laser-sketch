package com.example.boschlaser.shared.performance

import com.example.boschlaser.shared.editor.HitTestContext
import com.example.boschlaser.shared.editor.HitTestEngine
import com.example.boschlaser.shared.editor.SnapContext
import com.example.boschlaser.shared.editor.SnapEngine
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.Project
import com.example.boschlaser.shared.model.ProjectManifest
import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.model.Wall
import com.example.boschlaser.shared.storage.ProjectSerializer
import java.io.File
import java.util.Locale
import kotlin.math.ceil

/** Opt-in diagnostic, deliberately outside unit tests: JVM latency is not an Android frame budget. */
object EditorPerformanceBaseline {
    @Volatile private var result: Any? = null

    @JvmStatic
    fun main(args: Array<String>) {
        val rows = mutableListOf("walls,operation,samples,median_ms,p95_ms")
        println("Editor JVM baseline; ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}")
        for (count in listOf(100, 500, 1000)) {
            val document = fixture(count)
            val project = Project(ProjectManifest("synthetic-$count", "Synthetic baseline", 0), sketch = document)
            val hits = HitTestEngine()
            val snaps = SnapEngine()
            val hitContext = HitTestContext(30.0, 30.0, 30.0, 30.0)
            val operations = linkedMapOf<String, (Int) -> Any>(
                "hitTest" to { i -> hits.hitTest(document, pointer(i), hitContext) },
                "snapDefault" to { i -> snaps.snap(document, pointer(i), SnapContext(30.0, gridStep = 100.0)) },
                "snapPhysicalEdges" to { i -> snaps.snap(document, pointer(i), SnapContext(30.0, objectEdges = true)) },
                "snapIntersections" to { i -> snaps.snap(document, pointer(i), SnapContext(30.0, endpoints = false, intersections = true)) },
                "serializeProject" to { _ -> ProjectSerializer.encode(project) },
            )
            for ((name, operation) in operations) {
                // Change pointers and consume every result to avoid timing an unused computation.
                repeat(30) { result = operation(it) }
                val samples = List(40) { i ->
                    val start = System.nanoTime()
                    result = operation(i + 30)
                    (System.nanoTime() - start) / 1_000_000.0
                }.sorted()
                val median = (samples[19] + samples[20]) / 2.0
                val p95 = samples[ceil(samples.size * .95).toInt() - 1]
                val row = String.format(Locale.US, "%d,%s,%d,%.4f,%.4f", count, name, samples.size, median, p95)
                rows += row
                println(row)
            }
        }
        val output = File(args.single())
        output.parentFile.mkdirs()
        output.writeText(rows.joinToString("\n", postfix = "\n"))
        println("Report: ${output.absolutePath}")
    }

    private fun fixture(count: Int): SketchDocument = SketchDocument(walls = (0 until count).associate { i ->
        val x = (i % 25) * 4000.0
        val y = (i / 25) * 4000.0
        val start = Vec2(x, y)
        val end = if (i % 2 == 0) Vec2(x + 4000.0, y) else Vec2(x, y + 4000.0)
        val wall = Wall("wall-$i", start, end)
        wall.id to wall
    })

    // Close to a physical wall face, away from exact endpoints; also probes empty space.
    private fun pointer(i: Int) = Vec2((i % 25) * 4000.0 + 2000.0, (i % 40) * 4000.0 + 130.0)
}
