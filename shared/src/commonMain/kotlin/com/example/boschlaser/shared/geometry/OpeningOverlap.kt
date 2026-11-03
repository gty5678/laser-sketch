package com.example.boschlaser.shared.geometry

import com.example.boschlaser.shared.model.Opening
import com.example.boschlaser.shared.model.SketchDocument
import kotlin.math.max
import kotlin.math.min

/** Compare physical opening intervals, not door leaves or swing arcs. Touching is allowed. */
fun openingsOverlap(first: Opening, second: Opening): Boolean =
    first.id != second.id && first.hostWallId == second.hostWallId &&
        min(first.offset + first.width / 2.0, second.offset + second.width / 2.0) -
        max(first.offset - first.width / 2.0, second.offset - second.width / 2.0) > 1e-7

/** Advisory after a committed placement/move/resize; unrelated edits do not repeat the warning. */
fun hasEditedOpeningOverlap(before: SketchDocument, after: SketchDocument): Boolean {
    return after.openings.values.any { opening ->
        val old = before.openings[opening.id]
        val geometryChanged = old == null || old.hostWallId != opening.hostWallId ||
            old.offset != opening.offset || old.width != opening.width
        geometryChanged && after.openings.values.any { openingsOverlap(opening, it) }
    }
}
