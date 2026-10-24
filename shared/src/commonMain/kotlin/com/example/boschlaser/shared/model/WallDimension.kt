package com.example.boschlaser.shared.model

import com.example.boschlaser.shared.geometry.Vec2

/** Physical side/axis or end section relative to the wall's start-to-end direction. */
enum class WallFaceSide { LEFT, RIGHT, CENTER, START, END }

sealed interface DimensionReference {
    val entityId: String
    fun exists(walls: Map<String, Wall>, openings: Map<String, Opening>, columns: Map<String, Column>): Boolean
}

data class WallFaceReference(val wallId: String, val side: WallFaceSide) : DimensionReference {
    override val entityId get() = wallId
    override fun exists(walls: Map<String, Wall>, openings: Map<String, Opening>, columns: Map<String, Column>) = wallId in walls
    init { require(wallId.isNotBlank()) }
}

/** START/END are the jambs; LEFT/RIGHT are the frame edges along the host wall. */
enum class OpeningFrameSide { START, END, LEFT, RIGHT }
data class OpeningFrameReference(val openingId: String, val side: OpeningFrameSide) : DimensionReference {
    override val entityId get() = openingId
    override fun exists(walls: Map<String, Wall>, openings: Map<String, Opening>, columns: Map<String, Column>) = openingId in openings
    init { require(openingId.isNotBlank()) }
}

/** Center axes and edges follow a rectangular column's local rotation; circles use world axes/tangents. */
enum class ColumnReferenceLine { LEFT, RIGHT, TOP, BOTTOM, CENTER_X, CENTER_Y }
data class ColumnLineReference(val columnId: String, val line: ColumnReferenceLine) : DimensionReference {
    override val entityId get() = columnId
    override fun exists(walls: Map<String, Wall>, openings: Map<String, Opening>, columns: Map<String, Column>) = columnId in columns
    init { require(columnId.isNotBlank()) }
}

fun DimensionReference.sameEntity(other: DimensionReference): Boolean = entityId == other.entityId && when (this) {
    is WallFaceReference -> other is WallFaceReference
    is OpeningFrameReference -> other is OpeningFrameReference
    is ColumnLineReference -> other is ColumnLineReference
}

/** Same-wall dimensions are limited to opposite end sections; other self widths remain unsupported. */
fun DimensionReference.canDimensionWith(other: DimensionReference): Boolean = !sameEntity(other) ||
    (this is WallFaceReference && other is WallFaceReference && side != other.side &&
        side in listOf(WallFaceSide.START, WallFaceSide.END) && other.side in listOf(WallFaceSide.START, WallFaceSide.END))

/** Associative measurement. For wall length, the first end stays fixed and the second end moves. */
data class WallDimension(
    val id: String,
    val first: DimensionReference,
    val second: DimensionReference,
    val position: Vec2,
) {
    init { require(id.isNotBlank() && first.canDimensionWith(second)) }
}
