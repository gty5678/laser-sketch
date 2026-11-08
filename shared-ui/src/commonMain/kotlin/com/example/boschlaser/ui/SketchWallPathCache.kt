package com.example.boschlaser.ui

import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.model.Wall
import com.example.boschlaser.shared.model.Opening

/** Retains screen and magnifier products in coordinates independent of camera translation. */
internal class SketchWallPathCache<T> {
    private var walls: Map<String, Wall>? = null
    private var openings: Map<String, Opening>? = null
    private val entries = mutableMapOf<Double, T>()

    fun get(document: SketchDocument, scale: Double, build: () -> T): T {
        if (walls !== document.walls || openings !== document.openings) {
            walls = document.walls
            openings = document.openings
            entries.clear()
        }
        entries[scale]?.let { return it }
        val result = build()
        if (entries.size == 2) entries.clear()
        entries[scale] = result
        return result
    }
}
