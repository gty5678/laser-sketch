package com.example.boschlaser.shared.model

enum class OpeningType { DOOR, WINDOW }
/** Offset is the opening CENTER distance from the host reference-line start, in mm. */
data class Opening(
    val id: String,
    val hostWallId: String,
    val offset: Double,
    val width: Double,
    val type: OpeningType,
    val flipped: Boolean = false,
    val hingeFlipped: Boolean = false,
) {
    init {
        require(id.isNotBlank() && hostWallId.isNotBlank())
        require(offset.isFinite() && offset >= 0.0)
        require(width.isFinite() && width > 0.0)
    }
}

