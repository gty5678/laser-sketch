package com.example.boschlaser.shared.model

import com.example.boschlaser.shared.geometry.Vec2

sealed interface Column {
    val id: String
    val center: Vec2
}
data class RectColumn(
    override val id: String,
    override val center: Vec2,
    val width: Double,
    val depth: Double,
    val rotationDegrees: Double = 0.0,
) : Column {
    init {
        require(id.isNotBlank())
        require(width.isFinite() && width > 0.0 && depth.isFinite() && depth > 0.0)
        require(rotationDegrees.isFinite())
    }
}
data class CircularColumn(
    override val id: String,
    override val center: Vec2,
    val diameter: Double,
) : Column {
    init { require(id.isNotBlank() && diameter.isFinite() && diameter > 0.0) }
}

