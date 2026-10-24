package com.example.boschlaser.ui

import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.geometry.WallNetworkGeometry
import com.example.boschlaser.shared.model.Wall

/** Canvas-owned derived geometry. Retains only the current wall map and two display widths. */
internal class SketchWallGeometryCache {
    private var walls: Map<String, Wall>? = null
    private var minimumThickness = Double.POSITIVE_INFINITY
    private val entries = mutableMapOf<Double, List<List<Vec2>>>()

    fun polygons(current: Map<String, Wall>, minimumHalfWidth: Double): List<List<Vec2>> {
        if (walls !== current) {
            walls = current
            minimumThickness = current.values.minOfOrNull { it.thickness } ?: Double.POSITIVE_INFINITY
            entries.clear()
        }
        // Normal walls have the same polygons at both screen and magnifier scales.
        val width = if (minimumThickness / 2.0 >= minimumHalfWidth) 0.0 else minimumHalfWidth
        entries[width]?.let { return it }
        val values = current.values
        val polygons = values.map { WallNetworkGeometry.bodyPolygon(it, values, width) } +
            WallNetworkGeometry.jointPolygons(values, width)
        if (entries.size == 2) entries.clear()
        entries[width] = polygons
        return polygons
    }
}
