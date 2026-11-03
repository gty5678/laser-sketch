package com.example.boschlaser.shared.command

import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.OpeningGeometry
import com.example.boschlaser.shared.geometry.WallGeometry
import com.example.boschlaser.shared.model.SketchDocument
import com.example.boschlaser.shared.model.DoorStyle
import com.example.boschlaser.shared.model.OpeningType

/** Switching the symbol preserves the exact host, position, width and flip state. */
data class ChangeDoorStyleCommand(val openingId: String, val style: DoorStyle) : SketchCommand {
    override fun apply(document: SketchDocument): SketchDocument? {
        val old = document.openings[openingId] ?: return null
        if (old.type != OpeningType.DOOR) return null
        return UpdateOpeningCommand(old.copy(doorStyle = style)).apply(document)
    }
}

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
