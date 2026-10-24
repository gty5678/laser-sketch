package com.example.boschlaser

import java.util.Locale
import kotlin.math.abs

/** Display rounding happens directly on shared millimeters, without a legacy Float projection. */
internal fun formatAnnotationMillimeters(value: Double): String =
    if (abs(value) < 0.05) "0"
    else if (abs(value % 1.0) < 0.05) "%.0f".format(Locale.US, value)
    else "%.1f".format(Locale.US, value)
