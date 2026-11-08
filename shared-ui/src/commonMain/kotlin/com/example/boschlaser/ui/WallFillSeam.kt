package com.example.boschlaser.ui

import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.geometry.WallGeometry
import com.example.boschlaser.shared.model.Wall

/** Positive side belongs to the first wall. Uses physical centers for offset reference lines. */
internal data class WallFillSeam(val origin: Vec2, val normal: Vec2)

internal fun wallFillSeam(first: Wall, second: Wall): WallFillSeam? {
    val a = WallGeometry.build(first).centerLine
    val b = WallGeometry.build(second).centerLine
    if (Geometry.length(a) < 1.0 || Geometry.length(b) < 1.0) return null
    for (aStart in listOf(true, false)) for (bStart in listOf(true, false)) {
        val ar = if (aStart) first.start else first.end
        val br = if (bStart) second.start else second.end
        if (Geometry.distance(ar, br) >= 12.0) continue
        val ac = if (aStart) a.start else a.end
        val bc = if (bStart) b.start else b.end
        val au = Geometry.normalize(if (aStart) a.end - a.start else a.start - a.end)
        val bu = Geometry.normalize(if (bStart) b.end - b.start else b.start - b.end)
        val difference = au - bu
        if (difference.dot(difference) < 1e-8) return null
        return WallFillSeam((ac + bc) * .5, Geometry.normalize(difference))
    }
    return null
}
