package com.example.boschlaser

import java.util.Locale
import kotlin.math.floor

/** Keep sketch's whole-mm display and ties toward +infinity without Float/Int narrowing. */
internal fun formatSketchMillimeters(value: Double): String {
    require(value.isFinite())
    val lower = floor(value)
    val rounded = if (value - lower >= .5) lower + 1.0 else lower
    return if (rounded == 0.0) "0" else "%.0f".format(Locale.US, rounded)
}
