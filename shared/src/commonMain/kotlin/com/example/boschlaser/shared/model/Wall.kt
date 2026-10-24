package com.example.boschlaser.shared.model

import com.example.boschlaser.shared.geometry.Vec2

/** Alignment locates the wall body relative to its stored reference line. */
enum class WallAlignment { CENTER, LEFT, RIGHT }
enum class WallAnchor { START, END }

data class Wall(
    val id: String,
    val start: Vec2,
    val end: Vec2,
    val thickness: Double = 240.0,
    val alignment: WallAlignment = WallAlignment.CENTER,
    val measuredLength: Double? = null,
) {
    init {
        require(id.isNotBlank())
        require(thickness.isFinite() && thickness > 0.0)
        require(measuredLength == null || measuredLength.isFinite() && measuredLength > 0.0)
    }
}

