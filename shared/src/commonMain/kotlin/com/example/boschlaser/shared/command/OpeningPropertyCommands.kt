package com.example.boschlaser.shared.command

import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.OpeningGeometry
import com.example.boschlaser.shared.geometry.WallGeometry
import com.example.boschlaser.shared.model.SketchDocument

/** Resize retains the center where possible, clamping only enough to fit the unchanged host. */
data class ChangeOpeningPropertiesCommand(
    val openingId: String,
    val width: Double,
    val flip: Boolean = false,
    val flipHinge: Boolean = false,
) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? {
        if (!width.isFinite() || width < 100.0) return null
        val old = document.openings[openingId] ?: return null
        val host = document.walls[old.hostWallId] ?: return null
        val wallLength = Geometry.length(WallGeometry.referenceLine(host))
        if (width >= wallLength) return null
        return UpdateOpeningCommand(old.copy(
            offset = OpeningGeometry.constrainedOffset(old.offset, wallLength, width),
            width = width,
            flipped = if (flip) !old.flipped else old.flipped,
            hingeFlipped = if (flipHinge) !old.hingeFlipped else old.hingeFlipped,
        )).apply(document)
    }
}
