package com.example.boschlaser.shared.model

/** Semantic data only; polygons, selection, camera and previews are deliberately derived. */
data class SketchDocument(
    val formatVersion: Int = 1,
    val walls: Map<String, Wall> = emptyMap(),
    val openings: Map<String, Opening> = emptyMap(),
    val columns: Map<String, Column> = emptyMap(),
    val dimensions: Map<String, WallDimension> = emptyMap(),
) {
    init {
        require(formatVersion == 1)
        require(walls.all { (id, wall) -> id == wall.id })
        require(openings.all { (id, opening) -> id == opening.id && opening.hostWallId in walls })
        require(columns.all { (id, column) -> id == column.id })
        require(dimensions.all { (id, dimension) -> id == dimension.id &&
            dimension.first.exists(walls, openings, columns) && dimension.second.exists(walls, openings, columns) })
    }
}
