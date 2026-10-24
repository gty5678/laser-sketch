package com.example.boschlaser.shared.geometry

import com.example.boschlaser.shared.model.CircularColumn
import com.example.boschlaser.shared.model.Column
import com.example.boschlaser.shared.model.RectColumn
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object ColumnGeometry {
    fun normalizedDegrees(value: Double): Double {
        require(value.isFinite())
        return ((value % 360.0) + 360.0) % 360.0
    }
    fun corners(column: RectColumn): List<Vec2> {
        val angle = column.rotationDegrees * PI / 180.0
        val cosine = cos(angle)
        val sine = sin(angle)
        return listOf(
            Vec2(-column.width / 2.0, -column.depth / 2.0),
            Vec2(column.width / 2.0, -column.depth / 2.0),
            Vec2(column.width / 2.0, column.depth / 2.0),
            Vec2(-column.width / 2.0, column.depth / 2.0),
        ).map { column.center + Vec2(it.x * cosine - it.y * sine, it.x * sine + it.y * cosine) }
    }
    fun contains(column: Column, point: Vec2, tolerance: Double): Boolean {
        require(tolerance.isFinite() && tolerance >= 0.0)
        return when (column) {
            is CircularColumn -> Geometry.distance(point, column.center) <= column.diameter / 2.0 + tolerance
            is RectColumn -> {
                val angle = column.rotationDegrees * PI / 180.0
                val delta = point - column.center
                val localX = delta.x * cos(angle) + delta.y * sin(angle)
                val localY = -delta.x * sin(angle) + delta.y * cos(angle)
                abs(localX) <= column.width / 2.0 + tolerance && abs(localY) <= column.depth / 2.0 + tolerance
            }
        }
    }
}
